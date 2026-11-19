package com.example.ui

import android.app.Application
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TxIdApplication
import com.example.domain.AppSettings
import com.example.domain.AutoPasteStatus
import com.example.domain.CandidateIdentifier
import com.example.domain.DetectedTransaction
import com.example.domain.DetectionSource
import com.example.domain.ExtractionResult
import com.example.domain.InstalledAppInfo
import com.example.domain.OcrEngineState
import com.example.domain.PermissionStatus
import com.example.domain.RetentionMode
import com.example.service.PaymentPanelAccessibilityService
import com.example.service.ReceiptDetectionService
import com.example.service.TxIdNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
  val settings: AppSettings = AppSettings(),
  val permissionStatus: PermissionStatus = PermissionStatus(),
  val isAccessibilityBound: Boolean = false,
  val ocrEngineState: OcrEngineState = OcrEngineState.IDLE,
  val autoPasteStatus: AutoPasteStatus = AutoPasteStatus.DISABLED,
  val autoPasteDetail: String = "Auto Paste is off",
  val detectedTransaction: DetectedTransaction? = null,
  val pendingCandidates: List<CandidateIdentifier> = emptyList(),
  val installedApps: List<InstalledAppInfo> = emptyList(),
  val isLoadingApps: Boolean = false,
  val feedbackMessage: String? = null,
) {
  val destinationAppName: String?
    get() {
      val pkg = settings.destinationPackage ?: return null
      return installedApps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
    }

  val selectedSourceAppNames: List<String>
    get() =
      settings.selectedSourcePackages.map { pkg ->
        installedApps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
      }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val container = (application as TxIdApplication).container
  private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  private val _isLoadingApps = MutableStateFlow(true)
  private val _feedbackMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<MainUiState> =
    combine(
      container.settingsRepository.settingsFlow,
      container.permissionManager.permissionStatus,
      PaymentPanelAccessibilityService.isConnected,
      container.ocrProcessor.engineState,
      container.autoPasteManager.autoPasteStatus,
      container.autoPasteManager.lastStatusDetail,
      container.privacyManager.currentTransaction,
      container.privacyManager.pendingCandidates,
      _installedApps,
      _isLoadingApps,
      _feedbackMessage,
      ReceiptDetectionService.lastScreenScanMessage,
    ) { values ->
      val settings = values[0] as AppSettings
      val permissions = values[1] as PermissionStatus
      val isBound = values[2] as Boolean
      val ocrState = values[3] as OcrEngineState
      val pasteStatus = values[4] as AutoPasteStatus
      val pasteDetail = values[5] as String
      val currentTx = values[6] as DetectedTransaction?
      @Suppress("UNCHECKED_CAST")
      val candidates = values[7] as List<CandidateIdentifier>
      @Suppress("UNCHECKED_CAST")
      val apps = values[8] as List<InstalledAppInfo>
      val loadingApps = values[9] as Boolean
      val localFeedback = values[10] as String?
      val serviceFeedback = values[11] as String?

      container.ocrProcessor.syncWithMasterState(settings.isMasterEnabled)
      container.autoPasteManager.syncStatusWithMasterState(settings)

      MainUiState(
        settings = settings,
        permissionStatus = permissions,
        isAccessibilityBound = isBound,
        ocrEngineState = if (settings.isMasterEnabled) ocrState else OcrEngineState.IDLE,
        autoPasteStatus = pasteStatus,
        autoPasteDetail = pasteDetail,
        detectedTransaction = currentTx,
        pendingCandidates = candidates,
        installedApps = apps,
        isLoadingApps = loadingApps,
        feedbackMessage = localFeedback ?: serviceFeedback,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = MainUiState(),
    )

  init {
    refreshInstalledApps()
    refreshPermissions()
  }

  fun refreshPermissions() {
    container.permissionManager.refresh()
  }

  fun refreshInstalledApps() {
    viewModelScope.launch {
      _isLoadingApps.value = true
      _installedApps.value = container.installedAppsRepository.getInstalledLaunchableApps()
      _isLoadingApps.value = false
    }
  }

  fun loadAppIcon(packageName: String): Drawable? {
    return container.installedAppsRepository.loadAppIcon(packageName)
  }

  fun setMasterEnabled(enabled: Boolean) {
    viewModelScope.launch {
      container.settingsRepository.setMasterEnabled(enabled)
      if (!enabled) {
        container.privacyManager.enforceMasterOff()
        container.autoPasteManager.resetState()
        container.autoPasteManager.clearAppClipboardIfPossible()
        container.ocrProcessor.syncWithMasterState(false)
        ReceiptDetectionService.stopIfRunning(getApplication())
        TxIdNotificationHelper.cancelCandidatePrompt(getApplication())
        _feedbackMessage.value = "Monitoring stopped & temporary identifiers cleared"
      } else {
        container.ocrProcessor.syncWithMasterState(true)
        val perms = container.permissionManager.refresh()
        _feedbackMessage.value =
          if (!perms.isAccessibilityEnabled) {
            "Master ON — enable Accessibility Service for automatic screen detection & paste"
          } else {
            "TX ID AutoPaste is armed"
          }
      }
    }
  }

  fun scanReceiptUri(uri: Uri) {
    val currentSettings = uiState.value.settings
    if (!currentSettings.isMasterEnabled) {
      _feedbackMessage.value = "Turn Master Switch ON before running OCR receipt scans"
      return
    }

    viewModelScope.launch {
      try {
        val output = container.ocrProcessor.processImageUri(uri)
        if (!output.hadAnyText) {
          _feedbackMessage.value = "No readable receipt text detected in the selected image"
          return@launch
        }

        val result =
          container.extractor.extract(
            lines = output.lines,
            sourceAppPackage = null,
            sourceAppName = "Receipt Image",
            detectionSource = DetectionSource.PHOTO_PICKER_OCR,
            baseOcrConfidence = output.averageConfidence,
            minConfidenceThreshold = currentSettings.minConfidenceThreshold,
          )

        container.privacyManager.onExtractionResult(
          result = result,
          isMasterEnabled = currentSettings.isMasterEnabled,
          retentionMode = currentSettings.retentionMode,
        )
        container.autoPasteManager.syncStatusWithMasterState(currentSettings)

        when (result) {
          is ExtractionResult.SingleMatch -> {
            _feedbackMessage.value = "Extracted ${result.candidate.matchedLabel} locally"
            TxIdNotificationHelper.cancelCandidatePrompt(getApplication())
          }
          is ExtractionResult.MultipleCandidates -> {
            _feedbackMessage.value =
              "${result.candidates.size} identifiers found — confirm which one to use"
            TxIdNotificationHelper.showMultipleCandidatesPrompt(getApplication(), result.candidates)
          }
          is ExtractionResult.LowConfidence -> {
            _feedbackMessage.value = result.reason
          }
          is ExtractionResult.NoMatch -> {
            _feedbackMessage.value = result.reason
          }
        }
      } catch (_: Exception) {
        _feedbackMessage.value = "Unable to scan selected image locally"
      }
    }
  }

  fun confirmCandidate(candidate: CandidateIdentifier) {
    val settings = uiState.value.settings
    container.privacyManager.confirmCandidate(candidate, settings.retentionMode)
    container.autoPasteManager.syncStatusWithMasterState(settings)
    TxIdNotificationHelper.cancelCandidatePrompt(getApplication())
    _feedbackMessage.value = "Confirmed ${candidate.matchedLabel} for AutoPaste"
  }

  fun dismissCandidates() {
    container.privacyManager.dismissCandidates()
    container.autoPasteManager.syncStatusWithMasterState(uiState.value.settings)
    TxIdNotificationHelper.cancelCandidatePrompt(getApplication())
  }

  fun copyLatestId() {
    val tx = uiState.value.detectedTransaction ?: return
    container.autoPasteManager.copyToClipboard(tx.id, markSensitive = true)
    _feedbackMessage.value = "Copied exact ID to clipboard"
  }

  fun clearLatestId() {
    container.privacyManager.clearAll()
    container.autoPasteManager.clearAppClipboardIfPossible()
    container.autoPasteManager.syncStatusWithMasterState(uiState.value.settings)
    TxIdNotificationHelper.cancelCandidatePrompt(getApplication())
    _feedbackMessage.value = "Temporary ID cleared from memory"
  }

  fun toggleSourcePackage(packageName: String) {
    viewModelScope.launch {
      if (packageName == uiState.value.settings.destinationPackage) {
        _feedbackMessage.value = "This app is already selected as your Payment Panel destination"
        return@launch
      }
      container.settingsRepository.toggleSourcePackage(packageName)
    }
  }

  fun setDestinationPackage(packageName: String?) {
    viewModelScope.launch {
      if (packageName != null && packageName in uiState.value.settings.selectedSourcePackages) {
        container.settingsRepository.toggleSourcePackage(packageName)
      }
      container.settingsRepository.setDestinationPackage(packageName)
    }
  }

  fun setRetentionMode(mode: RetentionMode) {
    viewModelScope.launch { container.settingsRepository.setRetentionMode(mode) }
  }

  fun setClipboardFallbackEnabled(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.setClipboardFallbackEnabled(enabled) }
  }

  fun setMinConfidenceThreshold(threshold: Float) {
    viewModelScope.launch { container.settingsRepository.setMinConfidenceThreshold(threshold) }
  }

  fun setMediaProjectionFallbackEnabled(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.setMediaProjectionFallbackEnabled(enabled) }
  }

  fun consumeFeedback() {
    _feedbackMessage.value = null
    ReceiptDetectionService.clearStatusMessage()
  }
}
