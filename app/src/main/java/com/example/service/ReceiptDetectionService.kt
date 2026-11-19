package com.example.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.example.TxIdApplication
import com.example.domain.DetectionSource
import com.example.domain.ExtractionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles optional single-shot MediaProjection screen OCR as an Android 14/15 compliant
 * foreground service (`foregroundServiceType="mediaProjection"`).
 *
 * Privacy guarantees:
 * - Only runs on explicit user action & OS MediaProjection consent.
 * - Captures at most ONE transient frame, runs local ML Kit OCR, immediately recycles bitmap,
 *   and tears down the VirtualDisplay and Foreground Service.
 * - Immediately aborts if Master Switch is turned OFF.
 */
class ReceiptDetectionService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP_IMMEDIATELY) {
      tearDownProjection()
      stopSelf()
      return START_NOT_STICKY
    }

    val notification = TxIdNotificationHelper.buildForegroundScanNotification(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceCompat.startForeground(
        this,
        TxIdNotificationHelper.MEDIA_PROJECTION_NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(TxIdNotificationHelper.MEDIA_PROJECTION_NOTIFICATION_ID, notification)
    }

    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
      ?: Activity.RESULT_CANCELED
    @Suppress("DEPRECATION")
    val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

    if (resultCode != Activity.RESULT_OK || resultData == null) {
      _lastScreenScanMessage.value = "Screen capture permission was declined"
      stopSelf()
      return START_NOT_STICKY
    }

    scope.launch {
      val container = (applicationContext as TxIdApplication).container
      val settings = container.settingsRepository.settingsFlow.first()
      if (!settings.isMasterEnabled) {
        _lastScreenScanMessage.value = "Screen scan aborted because Master Switch is OFF"
        tearDownProjection()
        stopSelf()
        return@launch
      }

      // Brief delay so the OS permission sheet finishes dismissing before single-frame capture
      delay(450L)

      val settingsAfterDelay = container.settingsRepository.settingsFlow.first()
      if (!settingsAfterDelay.isMasterEnabled) {
        tearDownProjection()
        stopSelf()
        return@launch
      }

      runOneShotCapture(resultCode, resultData)
    }

    return START_NOT_STICKY
  }

  private fun runOneShotCapture(resultCode: Int, resultData: Intent) {
    val projectionManager =
      getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    if (projectionManager == null) {
      _lastScreenScanMessage.value = "MediaProjection unavailable on this device"
      stopSelf()
      return
    }

    val projection =
      try {
        projectionManager.getMediaProjection(resultCode, resultData)
      } catch (_: SecurityException) {
        _lastScreenScanMessage.value = "Screen capture permission rejected by Android security policy"
        stopSelf()
        return
      }

    if (projection == null) {
      _lastScreenScanMessage.value = "Unable to start single-shot screen capture"
      stopSelf()
      return
    }
    mediaProjection = projection

    val metrics = resources.displayMetrics
    val width = metrics.widthPixels.coerceAtLeast(720)
    val height = metrics.heightPixels.coerceAtLeast(1280)
    val density = metrics.densityDpi

    projection.registerCallback(
      object : MediaProjection.Callback() {
        override fun onStop() {
          tearDownProjection()
        }
      },
      Handler(Looper.getMainLooper()),
    )

    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    imageReader = reader

    var handled = false
    reader.setOnImageAvailableListener(
      { imgReader ->
        if (handled) return@setOnImageAvailableListener
        val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
        handled = true
        scope.launch {
          try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val rawBitmap =
              Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888,
              )
            rawBitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
            if (cropped !== rawBitmap) rawBitmap.recycle()

            processCapturedBitmap(cropped)
          } catch (_: Exception) {
            _lastScreenScanMessage.value =
              "Screen scan failed. If a banking app blocks screen capture (FLAG_SECURE), scan a saved receipt image instead."
          } finally {
            image.close()
            tearDownProjection()
            stopSelf()
          }
        }
      },
      Handler(Looper.getMainLooper()),
    )

    virtualDisplay =
      projection.createVirtualDisplay(
        "TxIdOneShotOcr",
        width,
        height,
        density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        Handler(Looper.getMainLooper()),
      )

    // Safety timeout so service never stays alive if no frame arrives
    scope.launch {
      delay(4000L)
      if (!handled) {
        handled = true
        _lastScreenScanMessage.value =
          "No screen frame captured (banking screen may enforce FLAG_SECURE). Try Photo Picker scan."
        tearDownProjection()
        stopSelf()
      }
    }
  }

  private suspend fun processCapturedBitmap(bitmap: Bitmap) {
    val container = (applicationContext as TxIdApplication).container
    val settings = container.settingsRepository.settingsFlow.first()
    if (!settings.isMasterEnabled) {
      bitmap.recycle()
      return
    }

    val ocrOutput = container.ocrProcessor.processBitmap(bitmap, recycleAfterScan = true)
    if (!ocrOutput.hadAnyText) {
      _lastScreenScanMessage.value =
        "No readable text captured. Banking apps with FLAG_SECURE block screen capture — use Photo Picker image scan as a safe fallback."
      return
    }

    val result =
      container.extractor.extract(
        lines = ocrOutput.lines,
        sourceAppPackage = null,
        sourceAppName = "Screen OCR",
        detectionSource = DetectionSource.SCREEN_OCR,
        baseOcrConfidence = ocrOutput.averageConfidence,
        minConfidenceThreshold = settings.minConfidenceThreshold,
      )

    container.privacyManager.onExtractionResult(
      result = result,
      isMasterEnabled = settings.isMasterEnabled,
      retentionMode = settings.retentionMode,
    )
    container.autoPasteManager.syncStatusWithMasterState(settings)

    when (result) {
      is ExtractionResult.SingleMatch -> {
        _lastScreenScanMessage.value = "Detected ${result.candidate.matchedLabel} locally"
        TxIdNotificationHelper.cancelCandidatePrompt(this)
      }
      is ExtractionResult.MultipleCandidates -> {
        _lastScreenScanMessage.value = "${result.candidates.size} identifiers found — confirmation needed"
        TxIdNotificationHelper.showMultipleCandidatesPrompt(this, result.candidates)
      }
      is ExtractionResult.LowConfidence -> {
        _lastScreenScanMessage.value = result.reason
      }
      is ExtractionResult.NoMatch -> {
        _lastScreenScanMessage.value = result.reason
      }
    }
  }

  private fun tearDownProjection() {
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
    runCatching { mediaProjection?.stop() }
    mediaProjection = null
  }

  override fun onDestroy() {
    tearDownProjection()
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val ACTION_STOP_IMMEDIATELY = "com.example.action.STOP_RECEIPT_DETECTION"

    private val _lastScreenScanMessage = MutableStateFlow<String?>(null)
    val lastScreenScanMessage: StateFlow<String?> = _lastScreenScanMessage.asStateFlow()

    fun clearStatusMessage() {
      _lastScreenScanMessage.value = null
    }

    fun stopIfRunning(context: Context) {
      runCatching {
        context.stopService(Intent(context, ReceiptDetectionService::class.java))
      }
    }
  }
}
