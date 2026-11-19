package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.TxIdApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ConfirmCandidateReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as? TxIdApplication ?: return
    val container = app.container

    when (intent.action) {
      ACTION_CONFIRM_CANDIDATE -> {
        val value = intent.getStringExtra(EXTRA_CANDIDATE_VALUE) ?: return
        val settings = runBlocking { container.settingsRepository.settingsFlow.first() }
        container.privacyManager.confirmCandidateByValue(value, settings.retentionMode)
        container.autoPasteManager.syncStatusWithMasterState(settings)
        TxIdNotificationHelper.cancelCandidatePrompt(context)
      }
      ACTION_DISMISS_CANDIDATES -> {
        container.privacyManager.dismissCandidates()
        val settings = runBlocking { container.settingsRepository.settingsFlow.first() }
        container.autoPasteManager.syncStatusWithMasterState(settings)
        TxIdNotificationHelper.cancelCandidatePrompt(context)
      }
    }
  }

  companion object {
    const val ACTION_CONFIRM_CANDIDATE = "com.example.action.CONFIRM_CANDIDATE"
    const val ACTION_DISMISS_CANDIDATES = "com.example.action.DISMISS_CANDIDATES"
    const val EXTRA_CANDIDATE_VALUE = "extra_candidate_value"
  }
}
