package com.example.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.domain.PermissionStatus
import com.example.service.PaymentPanelAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks runtime and system-level permissions cleanly for Android 13, 14, 15 and newer.
 * Never requests broad storage, contacts, or SMS permissions.
 */
class PermissionManager(private val context: Context) {

  private val _permissionStatus = MutableStateFlow(readCurrentStatus())
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  fun refresh(): PermissionStatus {
    val updated = readCurrentStatus()
    _permissionStatus.value = updated
    return updated
  }

  private fun readCurrentStatus(): PermissionStatus {
    return PermissionStatus(
      isAccessibilityEnabled = isAccessibilityServiceEnabled(),
      isNotificationsGranted = isNotificationPermissionGranted(),
    )
  }

  fun isAccessibilityServiceEnabled(): Boolean {
    val enabled =
      Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0,
      )
    if (enabled != 1) return false

    val enabledServices =
      Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false

    val expectedComponent =
      ComponentName(context, PaymentPanelAccessibilityService::class.java)
    val fullName = expectedComponent.flattenToString()
    val shortName = expectedComponent.flattenToShortString()

    return enabledServices
      .split(':')
      .any { entry ->
        entry.equals(fullName, ignoreCase = true) ||
          entry.equals(shortName, ignoreCase = true)
      }
  }

  fun isNotificationPermissionGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun createAccessibilitySettingsIntent(): Intent {
    return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }
}
