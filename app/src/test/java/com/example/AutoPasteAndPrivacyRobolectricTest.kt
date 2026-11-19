package com.example

import android.Manifest
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.AppSettings
import com.example.domain.AutoPasteManager
import com.example.domain.AutoPasteStatus
import com.example.domain.CandidateIdentifier
import com.example.domain.DetectionSource
import com.example.domain.ExtractionResult
import com.example.domain.NodeInsertionDriver
import com.example.domain.PasteAttemptResult
import com.example.domain.PrivacyManager
import com.example.domain.RetentionMode
import com.example.domain.TargetFieldInfo
import com.example.ocr.OcrLineBlock
import com.example.ocr.OnDeviceOcrProcessor
import com.example.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoPasteAndPrivacyRobolectricTest {

  private lateinit var context: Application
  private lateinit var privacyManager: PrivacyManager
  private lateinit var autoPasteManager: AutoPasteManager
  private lateinit var permissionManager: PermissionManager
  private lateinit var ocrProcessor: OnDeviceOcrProcessor

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    privacyManager = PrivacyManager()
    autoPasteManager = AutoPasteManager(context, privacyManager)
    permissionManager = PermissionManager(context)
    ocrProcessor = OnDeviceOcrProcessor(context)
  }

  @Test
  fun `spatial line stitching merges two-column OCR label and value`() {
    val rawBlocks =
      listOf(
        OcrLineBlock("Reference Number", top = 300, bottom = 335, left = 40, confidence = 0.96f),
        OcrLineBlock("000987654321", top = 302, bottom = 337, left = 480, confidence = 0.94f),
        OcrLineBlock("Amount", top = 380, bottom = 415, left = 40, confidence = 0.98f),
        OcrLineBlock("PKR 2,500.00", top = 381, bottom = 416, left = 480, confidence = 0.95f),
      )

    val stitched = ocrProcessor.stitchSpatialLines(rawBlocks)
    assertTrue(stitched.hadAnyText)
    assertEquals(2, stitched.lines.size)
    assertEquals("Reference Number 000987654321", stitched.lines[0])
    assertEquals("Amount PKR 2,500.00", stitched.lines[1])
  }

  @Test
  fun `auto paste inserts exact ID via ACTION_SET_TEXT and prevents duplicate paste`() {
    val settings =
      AppSettings(
        isMasterEnabled = true,
        destinationPackage = "com.custom.paymentpanel",
        retentionMode = RetentionMode.AUTO_3_MINUTES,
      )
    val candidate =
      CandidateIdentifier(
        value = "000482910455",
        matchedLabel = "Transaction ID",
        confidence = 0.96f,
        sourceAppPackage = "com.bank.source",
        sourceAppName = "JazzCash",
        detectionSource = DetectionSource.ACCESSIBILITY,
      )
    privacyManager.onExtractionResult(
      ExtractionResult.SingleMatch(candidate),
      isMasterEnabled = true,
      retentionMode = settings.retentionMode,
    )

    val field =
      TargetFieldInfo(
        packageName = "com.custom.paymentpanel",
        isEditable = true,
        isEnabled = true,
        isPassword = false,
        hintText = "Enter TID",
        contentDescription = null,
        viewIdResourceName = "com.custom.paymentpanel:id/et_tid",
        adjacentLabelText = "Transaction ID",
        currentText = "",
        nodeSignature = "et_tid_1",
      )

    var insertedText: String? = null
    val driver =
      object : NodeInsertionDriver {
        override fun setTextDirectly(text: String): Boolean {
          insertedText = text
          return true
        }

        override fun pasteViaClipboardAction(): Boolean = false
      }

    val firstResult = autoPasteManager.tryAutoPaste(field, settings, driver)
    assertTrue(firstResult is PasteAttemptResult.InsertedViaSetText)
    assertEquals("000482910455", insertedText)
    assertEquals(AutoPasteStatus.PASTED_SET_TEXT, autoPasteManager.autoPasteStatus.value)

    // Second tap on same field must be blocked as duplicate
    val secondResult = autoPasteManager.tryAutoPaste(field, settings, driver)
    assertTrue(secondResult is PasteAttemptResult.Blocked)
  }

  @Test
  fun `auto paste uses clipboard fallback only when ACTION_SET_TEXT fails`() {
    val settings =
      AppSettings(
        isMasterEnabled = true,
        destinationPackage = "com.custom.paymentpanel",
        allowClipboardFallback = true,
      )
    privacyManager.onExtractionResult(
      ExtractionResult.SingleMatch(
        CandidateIdentifier(
          value = "FT263210004981",
          matchedLabel = "Reference ID",
          confidence = 0.94f,
          sourceAppPackage = null,
          sourceAppName = "Mashreq Bank",
          detectionSource = DetectionSource.PHOTO_PICKER_OCR,
        )
      ),
      isMasterEnabled = true,
      retentionMode = RetentionMode.MANUAL_OR_OFF,
    )

    val field =
      TargetFieldInfo(
        packageName = "com.custom.paymentpanel",
        isEditable = true,
        isEnabled = true,
        isPassword = false,
        hintText = "Reference Number",
        contentDescription = null,
        viewIdResourceName = null,
        adjacentLabelText = null,
        currentText = "",
        nodeSignature = "ref_field",
      )

    var pastedViaClipboard = false
    val driver =
      object : NodeInsertionDriver {
        override fun setTextDirectly(text: String): Boolean = false

        override fun pasteViaClipboardAction(): Boolean {
          pastedViaClipboard = true
          return true
        }
      }

    val result = autoPasteManager.tryAutoPaste(field, settings, driver)
    assertTrue(result is PasteAttemptResult.InsertedViaClipboard)
    assertTrue(pastedViaClipboard)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertEquals("FT263210004981", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
  }

  @Test
  fun `auto paste blocks wrong app and wrong fields like Amount or Mobile`() {
    val settings =
      AppSettings(
        isMasterEnabled = true,
        destinationPackage = "com.custom.paymentpanel",
      )
    privacyManager.onExtractionResult(
      ExtractionResult.SingleMatch(
        CandidateIdentifier(
          value = "001234567890",
          matchedLabel = "TID",
          confidence = 0.95f,
          sourceAppPackage = null,
          sourceAppName = "Easypaisa",
          detectionSource = DetectionSource.ACCESSIBILITY,
        )
      ),
      isMasterEnabled = true,
      retentionMode = RetentionMode.AUTO_3_MINUTES,
    )

    val driver =
      object : NodeInsertionDriver {
        override fun setTextDirectly(text: String): Boolean = true

        override fun pasteViaClipboardAction(): Boolean = true
      }

    val wrongAppField =
      TargetFieldInfo(
        packageName = "com.other.app",
        isEditable = true,
        isEnabled = true,
        isPassword = false,
        hintText = "Transaction ID",
        contentDescription = null,
        viewIdResourceName = null,
        adjacentLabelText = null,
        currentText = "",
        nodeSignature = "other",
      )
    assertTrue(autoPasteManager.tryAutoPaste(wrongAppField, settings, driver) is PasteAttemptResult.Blocked)

    val amountField =
      TargetFieldInfo(
        packageName = "com.custom.paymentpanel",
        isEditable = true,
        isEnabled = true,
        isPassword = false,
        hintText = "Transaction Amount",
        contentDescription = null,
        viewIdResourceName = "et_amount",
        adjacentLabelText = null,
        currentText = "",
        nodeSignature = "amt",
      )
    assertTrue(autoPasteManager.tryAutoPaste(amountField, settings, driver) is PasteAttemptResult.Blocked)
  }

  @Test
  fun `master switch OFF immediately clears volatile ID and disables paste`() {
    val onSettings =
      AppSettings(isMasterEnabled = true, destinationPackage = "com.custom.paymentpanel")
    privacyManager.onExtractionResult(
      ExtractionResult.SingleMatch(
        CandidateIdentifier(
          value = "009988776655",
          matchedLabel = "TX ID",
          confidence = 0.95f,
          sourceAppPackage = null,
          sourceAppName = "Raqami Bank",
          detectionSource = DetectionSource.ACCESSIBILITY,
        )
      ),
      isMasterEnabled = true,
      retentionMode = RetentionMode.AUTO_3_MINUTES,
    )
    assertEquals("009988776655", privacyManager.currentTransaction.value?.id)

    // Toggle Master OFF
    val offSettings = onSettings.copy(isMasterEnabled = false)
    privacyManager.enforceMasterOff()
    autoPasteManager.resetState()

    assertNull(privacyManager.currentTransaction.value)
    assertEquals(AutoPasteStatus.DISABLED, autoPasteManager.autoPasteStatus.value)

    val field =
      TargetFieldInfo(
        packageName = "com.custom.paymentpanel",
        isEditable = true,
        isEnabled = true,
        isPassword = false,
        hintText = "Enter TID",
        contentDescription = null,
        viewIdResourceName = null,
        adjacentLabelText = null,
        currentText = "",
        nodeSignature = "tid",
      )
    val driver =
      object : NodeInsertionDriver {
        override fun setTextDirectly(text: String): Boolean = true

        override fun pasteViaClipboardAction(): Boolean = true
      }

    val result = autoPasteManager.tryAutoPaste(field, offSettings, driver)
    assertTrue(result is PasteAttemptResult.Blocked)
  }

  @Test
  fun `permission denial states are accurately reflected by PermissionManager`() {
    val shadowApp = Shadows.shadowOf(context)
    shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val status = permissionManager.refresh()

    assertFalse(status.isAccessibilityEnabled)
    assertFalse(status.isNotificationsGranted)
  }
}
