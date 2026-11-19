package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.TxIdApplication
import com.example.domain.AppSettings
import com.example.domain.DetectionSource
import com.example.domain.ExtractionResult
import com.example.domain.NodeInsertionDriver
import com.example.domain.TargetFieldInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Accessibility service that:
 * 1. Immediately halts all inspection when Master Switch is OFF.
 * 2. Reads text nodes ONLY from user-selected source payment apps to detect exact TX/Reference IDs.
 * 3. Inserts the verified ID ONLY into approved TX ID / Reference fields inside the selected Payment Panel app.
 * 4. Never presses Continue, Submit, or Confirm.
 */
class PaymentPanelAccessibilityService : AccessibilityService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Volatile private var currentSettings = AppSettings()
  @Volatile private var lastScannedWindowDigest: Int = 0

  override fun onCreate() {
    super.onCreate()
    val container = (applicationContext as TxIdApplication).container
    serviceScope.launch {
      container.settingsRepository.settingsFlow.collect { settings ->
        currentSettings = settings
        if (!settings.isMasterEnabled) {
          lastScannedWindowDigest = 0
        }
      }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    _isConnected.value = true
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val settings = currentSettings
    if (!settings.isMasterEnabled || event == null) {
      return
    }

    val packageName = event.packageName?.toString() ?: return
    if (packageName == applicationContext.packageName) return

    val container = (applicationContext as TxIdApplication).container

    // 1. Source receipt detection path (only approved payment apps)
    if (packageName in settings.selectedSourcePackages) {
      val root = rootInActiveWindow ?: return
      try {
        val lines = collectVisibleTextLines(root)
        if (lines.isEmpty()) return

        val digest = lines.hashCode()
        if (digest == lastScannedWindowDigest) return
        lastScannedWindowDigest = digest

        val appName = container.installedAppsRepository.resolveAppName(packageName)
        val result =
          container.extractor.extract(
            lines = lines,
            sourceAppPackage = packageName,
            sourceAppName = appName,
            detectionSource = DetectionSource.ACCESSIBILITY,
            baseOcrConfidence = 1.0f,
            minConfidenceThreshold = settings.minConfidenceThreshold,
          )

        container.privacyManager.onExtractionResult(
          result = result,
          isMasterEnabled = settings.isMasterEnabled,
          retentionMode = settings.retentionMode,
        )
        container.autoPasteManager.syncStatusWithMasterState(settings)

        if (result is ExtractionResult.MultipleCandidates) {
          TxIdNotificationHelper.showMultipleCandidatesPrompt(this, result.candidates)
        } else if (result is ExtractionResult.SingleMatch) {
          TxIdNotificationHelper.cancelCandidatePrompt(this)
        }
      } finally {
        @Suppress("DEPRECATION")
        root.recycle()
      }
      return
    }

    // 2. Destination Payment Panel auto-paste path (only approved Payment Panel app)
    if (packageName == settings.destinationPackage) {
      val root = rootInActiveWindow ?: return
      try {
        val targetNode = findFocusedOrTargetEditText(event, root) ?: return
        try {
          val fieldInfo = buildTargetFieldInfo(packageName, targetNode, root)
          val driver =
            object : NodeInsertionDriver {
              override fun setTextDirectly(text: String): Boolean {
                val args =
                  Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                  }
                return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
              }

              override fun pasteViaClipboardAction(): Boolean {
                return targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
              }
            }

          container.autoPasteManager.tryAutoPaste(
            field = fieldInfo,
            settings = settings,
            driver = driver,
          )
        } finally {
          if (targetNode !== root) {
            @Suppress("DEPRECATION")
            targetNode.recycle()
          }
        }
      } finally {
        @Suppress("DEPRECATION")
        root.recycle()
      }
    }
  }

  override fun onInterrupt() {
    lastScannedWindowDigest = 0
  }

  override fun onDestroy() {
    _isConnected.value = false
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun collectVisibleTextLines(root: AccessibilityNodeInfo): List<String> {
    val lines = mutableListOf<String>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.add(root)
    var visited = 0
    while (stack.isNotEmpty() && visited < 400) {
      val node = stack.removeFirst()
      visited++
      val nodeText = node.text?.toString()?.trim()
      if (!nodeText.isNullOrEmpty()) {
        nodeText.lines().forEach { sub ->
          val clean = sub.trim()
          if (clean.isNotEmpty()) lines.add(clean)
        }
      }
      val desc = node.contentDescription?.toString()?.trim()
      if (!desc.isNullOrEmpty() && desc != nodeText) {
        lines.add(desc)
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { stack.addLast(it) }
      }
    }
    return lines
  }

  private fun findFocusedOrTargetEditText(
    event: AccessibilityEvent,
    root: AccessibilityNodeInfo,
  ): AccessibilityNodeInfo? {
    val source = event.source
    if (source != null && source.isEditable && source.isEnabled) {
      return source
    }
    @Suppress("DEPRECATION")
    source?.recycle()

    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    if (focused != null && focused.isEditable && focused.isEnabled) {
      return focused
    }
    @Suppress("DEPRECATION")
    focused?.recycle()
    return null
  }

  private fun buildTargetFieldInfo(
    packageName: String,
    node: AccessibilityNodeInfo,
    root: AccessibilityNodeInfo,
  ): TargetFieldInfo {
    val hint = node.hintText?.toString()
    val contentDesc = node.contentDescription?.toString()
    val viewId = node.viewIdResourceName
    val currentText = node.text?.toString()
    val adjacentLabel = findAdjacentLabelText(node, root)

    val signature =
      "${viewId.orEmpty()}|${hint.orEmpty()}|${adjacentLabel.orEmpty()}|${node.windowId}"

    return TargetFieldInfo(
      packageName = packageName,
      isEditable = node.isEditable,
      isEnabled = node.isEnabled,
      isPassword = node.isPassword,
      hintText = hint,
      contentDescription = contentDesc,
      viewIdResourceName = viewId,
      adjacentLabelText = adjacentLabel,
      currentText = currentText,
      nodeSignature = signature,
    )
  }

  private fun findAdjacentLabelText(
    target: AccessibilityNodeInfo,
    root: AccessibilityNodeInfo,
  ): String? {
    val labeledBy = target.labeledBy
    if (labeledBy != null) {
      val labelStr = labeledBy.text?.toString()
      @Suppress("DEPRECATION")
      labeledBy.recycle()
      if (!labelStr.isNullOrBlank()) return labelStr
    }

    val parent = target.parent ?: return null
    try {
      val collected = mutableListOf<String>()
      for (i in 0 until parent.childCount) {
        val sibling = parent.getChild(i) ?: continue
        try {
          if (!sibling.isEditable) {
            val txt = sibling.text?.toString()?.trim()
            if (!txt.isNullOrEmpty()) collected.add(txt)
          }
        } finally {
          @Suppress("DEPRECATION")
          sibling.recycle()
        }
      }
      if (collected.isNotEmpty()) return collected.joinToString(" ")
    } finally {
      @Suppress("DEPRECATION")
      parent.recycle()
    }
    return null
  }

  companion object {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
  }
}
