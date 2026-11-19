package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.domain.AppSettings
import com.example.domain.AutoPasteStatus
import com.example.domain.CandidateIdentifier
import com.example.domain.DetectedTransaction
import com.example.domain.DetectionSource
import com.example.domain.OcrEngineState
import com.example.domain.PermissionStatus
import com.example.ui.MainUiState
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.TxIdAutoPasteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context returns TX ID AutoPaste`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TX ID AutoPaste", appName)
  }

  @Test
  fun `home screen renders all required controls and toggles Master switch`() {
    var toggledValue = false
    val state =
      MainUiState(
        settings = AppSettings(isMasterEnabled = false),
        permissionStatus = PermissionStatus(isAccessibilityEnabled = true, isNotificationsGranted = true),
        ocrEngineState = OcrEngineState.IDLE,
        autoPasteStatus = AutoPasteStatus.DISABLED,
        detectedTransaction =
          DetectedTransaction(
            id = "004819203847",
            matchedLabel = "Transaction ID (TID)",
            confidence = 0.96f,
            sourceAppPackage = "com.wallet",
            sourceAppName = "JazzCash",
            detectionSource = DetectionSource.ACCESSIBILITY,
            detectedAtMillis = System.currentTimeMillis(),
            expiresAtMillis = null,
          ),
      )

    composeTestRule.setContent {
      TxIdAutoPasteTheme {
        HomeScreen(
          uiState = state,
          onToggleMaster = { toggledValue = it },
          onScanPhotoPicker = {},
          onScanScreenMediaProjection = {},
          onCopyId = {},
          onClearId = {},
          onConfirmCandidate = {},
          onDismissCandidates = {},
          onOpenAppSelection = {},
          onOpenSettings = {},
          onOpenAccessibilitySettings = {},
          onConsumeFeedback = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("master_switch").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("latest_detected_id").performScrollTo().assertTextContains("004819203847")
    composeTestRule.onNodeWithTag("scan_receipt_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("copy_id_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("clear_id_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_auto_detection").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_ocr_engine").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_payment_panel").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_auto_paste").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_privacy").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("master_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(toggledValue)
  }

  @Test
  fun `home screen displays candidate confirmation when multiple IDs detected`() {
    var confirmedId: String? = null
    val state =
      MainUiState(
        settings = AppSettings(isMasterEnabled = true),
        pendingCandidates =
          listOf(
            CandidateIdentifier(
              value = "001122334455",
              matchedLabel = "Transaction ID",
              confidence = 0.95f,
              sourceAppPackage = null,
              sourceAppName = "Raqami Bank",
              detectionSource = DetectionSource.PHOTO_PICKER_OCR,
            ),
            CandidateIdentifier(
              value = "FT263210004981",
              matchedLabel = "Reference Number",
              confidence = 0.93f,
              sourceAppPackage = null,
              sourceAppName = "Raqami Bank",
              detectionSource = DetectionSource.PHOTO_PICKER_OCR,
            ),
          ),
      )

    composeTestRule.setContent {
      TxIdAutoPasteTheme {
        HomeScreen(
          uiState = state,
          onToggleMaster = {},
          onScanPhotoPicker = {},
          onScanScreenMediaProjection = {},
          onCopyId = {},
          onClearId = {},
          onConfirmCandidate = { confirmedId = it.value },
          onDismissCandidates = {},
          onOpenAppSelection = {},
          onOpenSettings = {},
          onOpenAccessibilitySettings = {},
          onConsumeFeedback = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_candidate_0").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals("001122334455", confirmedId)
  }
}
