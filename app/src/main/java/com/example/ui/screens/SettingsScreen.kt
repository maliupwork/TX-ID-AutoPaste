package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.RetentionMode
import com.example.ui.MainUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  uiState: MainUiState,
  onOpenAccessibilitySettings: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onSetRetentionMode: (RetentionMode) -> Unit,
  onSetClipboardFallback: (Boolean) -> Unit,
  onSetMinConfidence: (Float) -> Unit,
  onSetMediaProjectionFallback: (Boolean) -> Unit,
  onPurgeMemoryNow: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text("Settings & Privacy") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 680.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Section 1: Permissions & Services
        SettingsSectionCard(title = "Permissions & System Services") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AccessibilityNew,
                contentDescription = null,
                tint =
                  if (uiState.permissionStatus.isAccessibilityEnabled)
                    MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.tertiary,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Accessibility Service", style = MaterialTheme.typography.titleSmall)
                Text(
                  text =
                    if (uiState.permissionStatus.isAccessibilityEnabled)
                      "Enabled (explicit user consent active)"
                    else "Disabled — required for automatic detection & ACTION_SET_TEXT paste",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
              onClick = onOpenAccessibilitySettings,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("settings_accessibility_button"),
            ) {
              Text("Manage")
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint =
                  if (uiState.permissionStatus.isNotificationsGranted)
                    MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.tertiary,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Notifications (Android 13+)", style = MaterialTheme.typography.titleSmall)
                Text(
                  text =
                    if (uiState.permissionStatus.isNotificationsGranted)
                      "Granted for multi-ID confirmation & scan status"
                    else "Optional — needed for notification ID confirmation prompts",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (!uiState.permissionStatus.isNotificationsGranted) {
              Spacer(modifier = Modifier.width(8.dp))
              FilledTonalButton(
                onClick = onRequestNotificationPermission,
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("request_notifications_button"),
              ) {
                Text("Allow")
              }
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("MediaProjection OCR Fallback", style = MaterialTheme.typography.titleSmall)
              Text(
                text =
                  "Allow optional one-shot screen OCR with explicit Android system consent. Never runs automatically or continuously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = uiState.settings.mediaProjectionFallbackEnabled,
              onCheckedChange = onSetMediaProjectionFallback,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("media_projection_toggle"),
            )
          }
        }

        // Section 2: Safety & Accuracy Guards
        SettingsSectionCard(title = "Auto-Paste Safety & Retention") {
          Text(
            text = "Temporary ID Retention",
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(6.dp))
          RetentionMode.entries.forEach { mode ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.small)
                  .clickable { onSetRetentionMode(mode) }
                  .padding(vertical = 4.dp)
                  .testTag("retention_${mode.name}"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = uiState.settings.retentionMode == mode,
                onClick = { onSetRetentionMode(mode) },
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = mode.label, style = MaterialTheme.typography.bodyMedium)
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Clipboard Paste Fallback", style = MaterialTheme.typography.titleSmall)
              Text(
                text =
                  "Uses ACTION_SET_TEXT first. Only places the ID on clipboard (flagged sensitive on Android 13+) if direct insertion is unsupported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = uiState.settings.allowClipboardFallback,
              onCheckedChange = onSetClipboardFallback,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("clipboard_fallback_toggle"),
            )
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

          val thresholdPercent = (uiState.settings.minConfidenceThreshold * 100).roundToInt()
          Text(
            text = "Minimum Extraction Confidence: $thresholdPercent%",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "Identifiers below this score are rejected and never auto-pasted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Slider(
            value = uiState.settings.minConfidenceThreshold,
            onValueChange = onSetMinConfidence,
            valueRange = 0.60f..0.95f,
            steps = 6,
            modifier = Modifier.fillMaxWidth().testTag("confidence_slider"),
          )
        }

        // Section 3: Privacy Architecture & Banking Restrictions
        SettingsSectionCard(title = "Strict Privacy & Security Limitations") {
          PrivacyBullet(
            "100% On-Device OCR: Bundled ML Kit Text Recognition runs locally with zero receipt uploads, zero cloud OCR, and zero external APIs."
          )
          PrivacyBullet(
            "No Permanent History: Only the single extracted Transaction ID is held in volatile RAM until pasted, expired, cleared, or Master OFF."
          )
          PrivacyBullet(
            "Zero Unnecessary Permissions: No Contacts, SMS, Storage, Location, Analytics, or Ad SDKs."
          )
          PrivacyBullet(
            "No Auto-Submit: Never clicks Continue, Submit, Confirm, or Pay buttons inside your Payment Panel."
          )
          PrivacyBullet(
            "Banking App Security (FLAG_SECURE): If Mashreq, Raqami, JazzCash, or Easypaisa marks a receipt screen FLAG_SECURE, Android blocks Accessibility text and Screen Capture. Use Scan Receipt → Photo Picker on a saved/shared receipt image."
          )

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedButton(
            onClick = onPurgeMemoryNow,
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("purge_memory_button"),
            colors =
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteForever,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Purge Temporary Memory Now")
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
      }
      Spacer(modifier = Modifier.height(14.dp))
      content()
    }
  }
}

@Composable
private fun PrivacyBullet(text: String) {
  Row(
    modifier = Modifier.padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      imageVector = Icons.Default.PrivacyTip,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 2.dp).size(15.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
