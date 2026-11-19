package com.example.domain

enum class RetentionMode(val label: String, val durationMillis: Long?) {
  AUTO_3_MINUTES("Auto-clear after 3 minutes or paste", 3 * 60_000L),
  CLEAR_AFTER_SINGLE_PASTE("Clear immediately after paste", null),
  MANUAL_OR_OFF("Keep until cleared or turned OFF", null),
}

enum class DetectionSource(val displayName: String) {
  ACCESSIBILITY("Accessibility Monitor"),
  PHOTO_PICKER_OCR("Local Image OCR"),
  SCREEN_OCR("Local Screen OCR"),
}

enum class OcrEngineState(val displayLabel: String) {
  READY("ML Kit Ready (Local)"),
  PROCESSING("Scanning Locally..."),
  IDLE("Paused (Master OFF)"),
  ERROR("Scan Error"),
}

enum class AutoPasteStatus(val displayLabel: String) {
  DISABLED("Disabled"),
  ARMED("Armed for Payment Panel"),
  AWAITING_CONFIRMATION("Awaiting ID Confirmation"),
  PASTED_SET_TEXT("Inserted via ACTION_SET_TEXT"),
  PASTED_CLIPBOARD("Inserted via Clipboard Fallback"),
  BLOCKED_WRONG_FIELD("Skipped (Unrecognized Field)"),
  BLOCKED_LOW_CONFIDENCE("Skipped (Low Confidence)"),
}

data class CandidateIdentifier(
  val value: String,
  val matchedLabel: String,
  val confidence: Float,
  val sourceAppPackage: String?,
  val sourceAppName: String?,
  val detectionSource: DetectionSource,
  val detectedAtMillis: Long = System.currentTimeMillis(),
)

data class DetectedTransaction(
  val id: String,
  val matchedLabel: String,
  val confidence: Float,
  val sourceAppPackage: String?,
  val sourceAppName: String?,
  val detectionSource: DetectionSource,
  val detectedAtMillis: Long,
  val expiresAtMillis: Long?,
  val wasPasted: Boolean = false,
)

sealed interface ExtractionResult {
  data class SingleMatch(val candidate: CandidateIdentifier) : ExtractionResult

  data class MultipleCandidates(val candidates: List<CandidateIdentifier>) : ExtractionResult

  data class LowConfidence(val candidates: List<CandidateIdentifier>, val reason: String) :
    ExtractionResult

  data class NoMatch(val reason: String) : ExtractionResult
}

data class InstalledAppInfo(
  val packageName: String,
  val appName: String,
  val isKnownPaymentApp: Boolean = false,
  val suggestedPaymentBrand: String? = null,
)

data class AppSettings(
  val isMasterEnabled: Boolean = false,
  val selectedSourcePackages: Set<String> = emptySet(),
  val destinationPackage: String? = null,
  val retentionMode: RetentionMode = RetentionMode.AUTO_3_MINUTES,
  val allowClipboardFallback: Boolean = true,
  val minConfidenceThreshold: Float = 0.75f,
  val mediaProjectionFallbackEnabled: Boolean = true,
)

data class PermissionStatus(
  val isAccessibilityEnabled: Boolean = false,
  val isNotificationsGranted: Boolean = true,
)
