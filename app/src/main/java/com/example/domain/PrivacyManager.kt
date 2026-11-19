package com.example.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages volatile in-memory storage of ONLY the extracted Transaction ID / Reference Number.
 *
 * Privacy guarantees:
 * - Zero disk/database persistence of transaction identifiers.
 * - Zero logging of transaction IDs, receipt text, or banking details.
 * - Immediate purge when Master Switch is turned OFF or manually cleared.
 * - Automatic time-bounded expiry based on RetentionMode.
 */
class PrivacyManager(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

  private val _currentTransaction = MutableStateFlow<DetectedTransaction?>(null)
  val currentTransaction: StateFlow<DetectedTransaction?> = _currentTransaction.asStateFlow()

  private val _pendingCandidates = MutableStateFlow<List<CandidateIdentifier>>(emptyList())
  val pendingCandidates: StateFlow<List<CandidateIdentifier>> = _pendingCandidates.asStateFlow()

  private val _lastRejectionReason = MutableStateFlow<String?>(null)
  val lastRejectionReason: StateFlow<String?> = _lastRejectionReason.asStateFlow()

  private var expiryJob: Job? = null

  fun onExtractionResult(
    result: ExtractionResult,
    isMasterEnabled: Boolean,
    retentionMode: RetentionMode,
    allowWhenMasterOff: Boolean = false,
  ) {
    if (!isMasterEnabled && !allowWhenMasterOff) {
      return
    }

    when (result) {
      is ExtractionResult.SingleMatch -> {
        _pendingCandidates.value = emptyList()
        _lastRejectionReason.value = null
        setConfirmedCandidate(result.candidate, retentionMode)
      }
      is ExtractionResult.MultipleCandidates -> {
        // Never silently pick one; require explicit user confirmation
        _currentTransaction.value = null
        expiryJob?.cancel()
        _pendingCandidates.value = result.candidates
        _lastRejectionReason.value = null
      }
      is ExtractionResult.LowConfidence -> {
        _lastRejectionReason.value = result.reason
      }
      is ExtractionResult.NoMatch -> {
        _lastRejectionReason.value = result.reason
      }
    }
  }

  fun confirmCandidate(candidate: CandidateIdentifier, retentionMode: RetentionMode) {
    _pendingCandidates.value = emptyList()
    _lastRejectionReason.value = null
    setConfirmedCandidate(candidate, retentionMode)
  }

  fun confirmCandidateByValue(value: String, retentionMode: RetentionMode): Boolean {
    val match = _pendingCandidates.value.firstOrNull { it.value == value } ?: return false
    confirmCandidate(match, retentionMode)
    return true
  }

  fun dismissCandidates() {
    _pendingCandidates.value = emptyList()
  }

  fun markAsPasted(retentionMode: RetentionMode) {
    val current = _currentTransaction.value ?: return
    if (retentionMode == RetentionMode.CLEAR_AFTER_SINGLE_PASTE) {
      clearAll()
    } else {
      _currentTransaction.value = current.copy(wasPasted = true)
    }
  }

  fun clearAll() {
    expiryJob?.cancel()
    expiryJob = null
    _currentTransaction.value = null
    _pendingCandidates.value = emptyList()
    _lastRejectionReason.value = null
  }

  fun enforceMasterOff() {
    clearAll()
  }

  fun getActiveTransactionOrNull(nowMillis: Long = System.currentTimeMillis()): DetectedTransaction? {
    val tx = _currentTransaction.value ?: return null
    val expiresAt = tx.expiresAtMillis
    if (expiresAt != null && nowMillis >= expiresAt) {
      clearAll()
      return null
    }
    return tx
  }

  private fun setConfirmedCandidate(
    candidate: CandidateIdentifier,
    retentionMode: RetentionMode,
  ) {
    val now = System.currentTimeMillis()
    val duration = retentionMode.durationMillis
    val expiresAt = duration?.let { now + it }

    _currentTransaction.value =
      DetectedTransaction(
        id = candidate.value,
        matchedLabel = candidate.matchedLabel,
        confidence = candidate.confidence,
        sourceAppPackage = candidate.sourceAppPackage,
        sourceAppName = candidate.sourceAppName,
        detectionSource = candidate.detectionSource,
        detectedAtMillis = now,
        expiresAtMillis = expiresAt,
        wasPasted = false,
      )

    expiryJob?.cancel()
    if (duration != null) {
      expiryJob =
        scope.launch {
          delay(duration)
          _currentTransaction.value = null
        }
    }
  }
}
