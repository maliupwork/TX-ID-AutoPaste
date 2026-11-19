package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.CandidateIdentifier

object TxIdNotificationHelper {

  const val CHANNEL_ID = "tx_id_autopaste_channel"
  const val CANDIDATE_NOTIFICATION_ID = 4102
  const val MEDIA_PROJECTION_NOTIFICATION_ID = 4101

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = context.getString(R.string.notification_channel_description)
          }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun buildForegroundScanNotification(context: Context): Notification {
    ensureChannel(context)
    val openAppIntent =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("Scanning receipt locally")
      .setContentText("Extracting transaction ID on-device; image is never saved.")
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setContentIntent(openAppIntent)
      .build()
  }

  fun showMultipleCandidatesPrompt(
    context: Context,
    candidates: List<CandidateIdentifier>,
  ) {
    if (!canPostNotifications(context)) return
    ensureChannel(context)

    val openIntent =
      PendingIntent.getActivity(
        context,
        10,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    val builder =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Confirm Transaction Identifier")
        .setContentText("${candidates.size} identifiers detected. Choose which ID to auto-paste.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openIntent)

    candidates.take(2).forEachIndexed { idx, candidate ->
      val confirmIntent =
        Intent(context, ConfirmCandidateReceiver::class.java).apply {
          action = ConfirmCandidateReceiver.ACTION_CONFIRM_CANDIDATE
          putExtra(ConfirmCandidateReceiver.EXTRA_CANDIDATE_VALUE, candidate.value)
        }
      val pending =
        PendingIntent.getBroadcast(
          context,
          100 + idx,
          confirmIntent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      builder.addAction(0, "${candidate.matchedLabel}: ${candidate.value}", pending)
    }

    val clearIntent =
      Intent(context, ConfirmCandidateReceiver::class.java).apply {
        action = ConfirmCandidateReceiver.ACTION_DISMISS_CANDIDATES
      }
    val clearPending =
      PendingIntent.getBroadcast(
        context,
        199,
        clearIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    builder.addAction(0, "Clear", clearPending)

    NotificationManagerCompat.from(context).notify(CANDIDATE_NOTIFICATION_ID, builder.build())
  }

  fun cancelCandidatePrompt(context: Context) {
    NotificationManagerCompat.from(context).cancel(CANDIDATE_NOTIFICATION_ID)
  }

  private fun canPostNotifications(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}
