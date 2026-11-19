package com.example.domain

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents metadata about an editable input field in the destination Payment Panel app.
 */
data class TargetFieldInfo(
  val packageName: String,
  val isEditable: Boolean,
  val isEnabled: Boolean,
  val isPassword: Boolean,
  val hintText: String?,
  val contentDescription: String?,
  val viewIdResourceName: String?,
  val adjacentLabelText: String?,
  val currentText: String?,
  val nodeSignature: String,
)

/**
 * Abstraction for node insertion so AutoPasteManager can be verified deterministically in tests.
 */
interface NodeInsertionDriver {
  fun setTextDirectly(text: String): Boolean

  fun pasteViaClipboardAction(): Boolean
}

sealed interface PasteAttemptResult {
  data class InsertedViaSetText(val id: String) : PasteAttemptResult

  data class InsertedViaClipboard(val id: String) : PasteAttemptResult

  data class Blocked(val status: AutoPasteStatus, val reason: String) : PasteAttemptResult
}

/**
 * Validates destination package, target input field semantics, confidence, duplicate protection,
 * and inserts only the verified transaction identifier—never clicking Continue, Submit, or Confirm.
 */
class AutoPasteManager(
  private val context: Context,
  private val privacyManager: PrivacyManager,
) {

  private val approvedFieldKeywords =
    listOf(
      "transaction id",
      "transaction_id",
      "tx id",
      "tx_id",
      "txid",
      "enter tid",
      "tid",
      "reference number",
      "reference_number",
      "reference no",
      "reference id",
      "reference_id",
      "transaction reference",
      "payment reference",
      "ref no",
      "ref id",
      "trx id",
      "trx_id",
    )

  private val rejectedFieldKeywords =
    listOf(
      "amount",
      "total",
      "price",
      "fee",
      "phone",
      "mobile",
      "cell",
      "password",
      "passcode",
      "pin",
      "otp",
      "email",
      "iban",
      "account",
      "username",
      "search",
      "remark",
      "comment",
      "note",
    )

  private val _autoPasteStatus = MutableStateFlow(AutoPasteStatus.DISABLED)
  val autoPasteStatus: StateFlow<AutoPasteStatus> = _autoPasteStatus.asStateFlow()

  private val _lastStatusDetail = MutableStateFlow("Auto Paste is off")
  val lastStatusDetail: StateFlow<String> = _lastStatusDetail.asStateFlow()

  private var lastPastedSignature: String? = null
  private var lastClipboardValuePlacedByApp: String? = null

  fun syncStatusWithMasterState(settings: AppSettings) {
    if (!settings.isMasterEnabled) {
      _autoPasteStatus.value = AutoPasteStatus.DISABLED
      _lastStatusDetail.value = "Auto Paste disabled while Master Switch is OFF"
      lastPastedSignature = null
      return
    }
    if (privacyManager.pendingCandidates.value.isNotEmpty()) {
      _autoPasteStatus.value = AutoPasteStatus.AWAITING_CONFIRMATION
      _lastStatusDetail.value = "Multiple IDs found — confirm one before pasting"
      return
    }
    if (_autoPasteStatus.value == AutoPasteStatus.DISABLED) {
      _autoPasteStatus.value = AutoPasteStatus.ARMED
      _lastStatusDetail.value =
        if (settings.destinationPackage != null) {
          "Ready to paste into selected Payment Panel"
        } else {
          "Select your Payment Panel app to arm Auto Paste"
        }
    }
  }

  fun resetState() {
    lastPastedSignature = null
    _autoPasteStatus.value = AutoPasteStatus.DISABLED
    _lastStatusDetail.value = "Auto Paste disabled"
  }

  fun tryAutoPaste(
    field: TargetFieldInfo,
    settings: AppSettings,
    driver: NodeInsertionDriver,
    nowMillis: Long = System.currentTimeMillis(),
  ): PasteAttemptResult {
    if (!settings.isMasterEnabled) {
      _autoPasteStatus.value = AutoPasteStatus.DISABLED
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.DISABLED,
        "Master Switch is OFF",
      )
    }

    val expectedDestination = settings.destinationPackage
    if (expectedDestination.isNullOrBlank() || field.packageName != expectedDestination) {
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.ARMED,
        "Active window is not the approved Payment Panel",
      )
    }

    if (privacyManager.pendingCandidates.value.isNotEmpty()) {
      _autoPasteStatus.value = AutoPasteStatus.AWAITING_CONFIRMATION
      _lastStatusDetail.value = "Waiting for user to confirm candidate ID"
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.AWAITING_CONFIRMATION,
        "Multiple candidate IDs require confirmation",
      )
    }

    val tx =
      privacyManager.getActiveTransactionOrNull(nowMillis)
        ?: return PasteAttemptResult.Blocked(
          AutoPasteStatus.ARMED,
          "No active transaction ID in temporary memory",
        )

    if (tx.confidence < settings.minConfidenceThreshold) {
      _autoPasteStatus.value = AutoPasteStatus.BLOCKED_LOW_CONFIDENCE
      _lastStatusDetail.value = "Skipped paste due to low extraction confidence"
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.BLOCKED_LOW_CONFIDENCE,
        "Confidence ${tx.confidence} is below threshold ${settings.minConfidenceThreshold}",
      )
    }

    if (!field.isEditable || !field.isEnabled || field.isPassword) {
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.BLOCKED_WRONG_FIELD,
        "Target field is not a safe editable input",
      )
    }

    if (!isApprovedTransactionField(field)) {
      _autoPasteStatus.value = AutoPasteStatus.BLOCKED_WRONG_FIELD
      _lastStatusDetail.value = "Input field does not match Transaction ID / TID / Reference"
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.BLOCKED_WRONG_FIELD,
        "Field semantics do not match an approved TX ID input",
      )
    }

    if (field.currentText?.trim() == tx.id) {
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.ARMED,
        "Field already contains the exact identifier",
      )
    }

    val pasteSignature = "${field.packageName}|${tx.id}|${field.nodeSignature}"
    if (lastPastedSignature == pasteSignature) {
      return PasteAttemptResult.Blocked(
        AutoPasteStatus.ARMED,
        "Duplicate paste prevented for this field",
      )
    }

    // Primary insertion method: ACTION_SET_TEXT (zero clipboard exposure)
    if (driver.setTextDirectly(tx.id)) {
      lastPastedSignature = pasteSignature
      privacyManager.markAsPasted(settings.retentionMode)
      _autoPasteStatus.value = AutoPasteStatus.PASTED_SET_TEXT
      _lastStatusDetail.value = "Inserted exact ID via ACTION_SET_TEXT"
      return PasteAttemptResult.InsertedViaSetText(tx.id)
    }

    // Fallback insertion method: transient clipboard paste if permitted
    if (settings.allowClipboardFallback) {
      copyToClipboard(tx.id, markSensitive = true)
      if (driver.pasteViaClipboardAction()) {
        lastPastedSignature = pasteSignature
        privacyManager.markAsPasted(settings.retentionMode)
        _autoPasteStatus.value = AutoPasteStatus.PASTED_CLIPBOARD
        _lastStatusDetail.value = "Inserted exact ID via clipboard fallback"
        return PasteAttemptResult.InsertedViaClipboard(tx.id)
      }
    }

    return PasteAttemptResult.Blocked(
      AutoPasteStatus.ARMED,
      "Target field rejected programmatic insertion",
    )
  }

  internal fun isApprovedTransactionField(field: TargetFieldInfo): Boolean {
    val combinedMetadata =
      listOfNotNull(
          field.hintText,
          field.contentDescription,
          field.adjacentLabelText,
          field.viewIdResourceName?.substringAfterLast('/'),
        )
        .joinToString(" ")
        .lowercase()

    if (combinedMetadata.isBlank()) return false

    val hasRejectedKeyword = rejectedFieldKeywords.any { keyword -> containsWordOrToken(combinedMetadata, keyword) }
    val hasApprovedKeyword = approvedFieldKeywords.any { keyword -> containsWordOrToken(combinedMetadata, keyword) }

    return hasApprovedKeyword && !hasRejectedKeyword
  }

  private fun containsWordOrToken(text: String, keyword: String): Boolean {
    val escaped = Regex.escape(keyword.replace("_", "[_\\s-]*"))
    return Regex("""(?i)(^|[^a-z0-9])$escaped([^a-z0-9]|$)""").containsMatchIn(text)
  }

  fun copyToClipboard(id: String, markSensitive: Boolean = true) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("Transaction ID", id)
    if (markSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      clip.description.extras =
        PersistableBundle().apply {
          putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    lastClipboardValuePlacedByApp = id
  }

  fun clearAppClipboardIfPossible() {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val currentText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    if (lastClipboardValuePlacedByApp != null && currentText == lastClipboardValuePlacedByApp) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
      } else {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
      }
    }
    lastClipboardValuePlacedByApp = null
  }
}
