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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.AutoPasteStatus
import com.example.domain.CandidateIdentifier
import com.example.domain.OcrEngineState
import com.example.ui.MainUiState
import com.example.ui.components.LatestDetectedIdCard
import com.example.ui.components.MasterControlHeroCard
import com.example.ui.components.TelemetryStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  uiState: MainUiState,
  onToggleMaster: (Boolean) -> Unit,
  onScanPhotoPicker: () -> Unit,
  onScanScreenMediaProjection: () -> Unit,
  onCopyId: () -> Unit,
  onClearId: () -> Unit,
  onConfirmCandidate: (CandidateIdentifier) -> Unit,
  onDismissCandidates: () -> Unit,
  onOpenAppSelection: (Int) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onConsumeFeedback: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showScanSheet by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.feedbackMessage) {
    val msg = uiState.feedbackMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      onConsumeFeedback()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "TX ID AutoPaste",
              style = MaterialTheme.typography.titleLarge,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onOpenAppSelection(0) },
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("apps_top_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Apps,
              contentDescription = "Select Source and Destination Apps",
            )
          }
          IconButton(
            onClick = onOpenSettings,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("settings_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
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
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding),
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
        // 1. Large ON/OFF Master Switch
        MasterControlHeroCard(
          isMasterEnabled = uiState.settings.isMasterEnabled,
          selectedSourceCount = uiState.settings.selectedSourcePackages.size,
          hasDestinationApp = uiState.settings.destinationPackage != null,
          onToggle = onToggleMaster,
        )

        // Accessibility / App Setup Prompts when needed
        if (!uiState.permissionStatus.isAccessibilityEnabled) {
          SetupWarningBanner(
            title = "Accessibility Service Required for Auto-Detection & Paste",
            subtitle =
              "Enable TX ID AutoPaste Service in Android Accessibility Settings to detect receipts and insert IDs automatically.",
            buttonLabel = "Enable Accessibility",
            icon = Icons.Default.AccessibilityNew,
            testTag = "enable_accessibility_button",
            onClick = onOpenAccessibilitySettings,
          )
        }

        if (
          uiState.settings.selectedSourcePackages.isEmpty() ||
            uiState.settings.destinationPackage == null
        ) {
          SetupWarningBanner(
            title = "Configure Your Payment & Panel Apps",
            subtitle =
              "Choose installed source apps (JazzCash, Easypaisa, Mashreq, Raqami) and your separate Payment Panel app.",
            buttonLabel = "Select Apps",
            icon = Icons.Default.Apps,
            testTag = "select_apps_button",
            onClick = { onOpenAppSelection(0) },
          )
        }

        // 2. Latest Detected ID Preview + Scan / Copy / Clear controls
        LatestDetectedIdCard(
          transaction = uiState.detectedTransaction,
          pendingCandidates = uiState.pendingCandidates,
          isMasterEnabled = uiState.settings.isMasterEnabled,
          onScanClick = { showScanSheet = true },
          onCopyClick = onCopyId,
          onClearClick = onClearId,
          onConfirmCandidate = onConfirmCandidate,
          onDismissCandidates = onDismissCandidates,
        )

        // 3. System Telemetry Grid (5 required status cards)
        Text(
          text = "System Status",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.padding(top = 4.dp),
        )

        val autoDetectionStatusText =
          when {
            !uiState.settings.isMasterEnabled -> "Stopped (Master OFF)"
            !uiState.permissionStatus.isAccessibilityEnabled -> "Accessibility Needed"
            uiState.settings.selectedSourcePackages.isEmpty() -> "No Source Apps Selected"
            uiState.isAccessibilityBound ->
              "Active (${uiState.settings.selectedSourcePackages.size} apps)"
            else -> "Waiting for OS Service"
          }
        val autoDetectionColor =
          when {
            !uiState.settings.isMasterEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
            uiState.permissionStatus.isAccessibilityEnabled &&
              uiState.settings.selectedSourcePackages.isNotEmpty() ->
              MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
          }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          TelemetryStatusCard(
            title = "Automatic Detection",
            value = autoDetectionStatusText,
            icon = Icons.Default.Visibility,
            accentColor = autoDetectionColor,
            testTag = "status_auto_detection",
            modifier = Modifier.weight(1f),
          )
          TelemetryStatusCard(
            title = "OCR Engine",
            value = uiState.ocrEngineState.displayLabel,
            icon = Icons.Default.ImageSearch,
            accentColor =
              when (uiState.ocrEngineState) {
                OcrEngineState.READY,
                OcrEngineState.PROCESSING -> MaterialTheme.colorScheme.primary
                OcrEngineState.ERROR -> MaterialTheme.colorScheme.error
                OcrEngineState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
              },
            testTag = "status_ocr_engine",
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val panelStatus =
            when {
              uiState.destinationAppName == null -> "Not Selected"
              !uiState.permissionStatus.isAccessibilityEnabled ->
                "${uiState.destinationAppName} (Needs Accessibility)"
              else -> "${uiState.destinationAppName} (Linked)"
            }
          TelemetryStatusCard(
            title = "Payment Panel",
            value = panelStatus,
            icon = Icons.Default.AutoAwesome,
            accentColor =
              if (
                uiState.destinationAppName != null &&
                  uiState.permissionStatus.isAccessibilityEnabled
              ) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.tertiary
              },
            testTag = "status_payment_panel",
            onClick = { onOpenAppSelection(1) },
            modifier = Modifier.weight(1f),
          )

          val pasteColor =
            when (uiState.autoPasteStatus) {
              AutoPasteStatus.ARMED,
              AutoPasteStatus.PASTED_SET_TEXT,
              AutoPasteStatus.PASTED_CLIPBOARD -> MaterialTheme.colorScheme.primary
              AutoPasteStatus.AWAITING_CONFIRMATION,
              AutoPasteStatus.BLOCKED_WRONG_FIELD,
              AutoPasteStatus.BLOCKED_LOW_CONFIDENCE -> MaterialTheme.colorScheme.tertiary
              AutoPasteStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
            }
          TelemetryStatusCard(
            title = "Auto Paste",
            value = uiState.autoPasteStatus.displayLabel,
            icon = Icons.Default.ContentPasteGo,
            accentColor = pasteColor,
            testTag = "status_auto_paste",
            modifier = Modifier.weight(1f),
          )
        }

        TelemetryStatusCard(
          title = "Privacy Status",
          value = "On-Device Only • Zero Uploads • Volatile Memory • No History",
          icon = Icons.Default.Security,
          accentColor = MaterialTheme.colorScheme.primary,
          testTag = "status_privacy",
        )

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }

  if (showScanSheet) {
    ModalBottomSheet(onDismissRequest = { showScanSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Scan Payment Receipt Locally",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text =
            "All OCR runs 100% offline on your phone. Images are processed in memory and immediately discarded.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .clickable {
                showScanSheet = false
                onScanPhotoPicker()
              }
              .testTag("scan_photo_picker_option"),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.PhotoLibrary,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = "Scan Receipt Image (Photo Picker)",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Recommended when banking apps block screen capture (FLAG_SECURE). Zero storage permission required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (uiState.settings.mediaProjectionFallbackEnabled) {
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                  showScanSheet = false
                  onScanScreenMediaProjection()
                }
                .testTag("scan_screen_ocr_option"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.Screenshot,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.width(14.dp))
              Column {
                Text(
                  text = "One-Shot Screen OCR (MediaProjection)",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = "Captures one transient frame with explicit Android system consent, then stops immediately.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              "Note: Banking apps using Android FLAG_SECURE hide screen content from both Accessibility and Screen Capture.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SetupWarningBanner(
  title: String,
  subtitle: String,
  buttonLabel: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
      )
      Spacer(modifier = Modifier.height(10.dp))
      FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
      ) {
        Text(buttonLabel)
      }
    }
  }
}
