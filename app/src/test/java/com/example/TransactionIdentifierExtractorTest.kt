package com.example

import com.example.domain.DetectionSource
import com.example.domain.ExtractionResult
import com.example.domain.TransactionIdentifierExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionIdentifierExtractorTest {

  private val extractor = TransactionIdentifierExtractor()

  @Test
  fun `jazzcash receipt preserves leading zeros and ignores amount and phone`() {
    val receiptLines =
      listOf(
        "JazzCash",
        "Transaction Successful",
        "Transaction ID (TID): 004819203847",
        "Amount: Rs. 5,500.00",
        "Receiver Mobile: 0300-1234567",
        "Date: 17/11/2026 20:45",
      )

    val result = extractor.extract(receiptLines, sourceAppName = "JazzCash")
    assertTrue(result is ExtractionResult.SingleMatch)
    val match = (result as ExtractionResult.SingleMatch).candidate
    assertEquals("004819203847", match.value)
    assertEquals("Transaction ID (TID)", match.matchedLabel)
  }

  @Test
  fun `jazzcash business receipt extracts alphanumeric TX ID and ignores merchant id`() {
    val receiptLines =
      listOf(
        "JazzCash Business",
        "Payment Received",
        "Merchant ID: 99887766",
        "TX ID: JC202611170004918273",
        "Total Amount: PKR 48,250.00",
      )

    val result = extractor.extract(receiptLines, sourceAppName = "JazzCash Business")
    assertTrue(result is ExtractionResult.SingleMatch)
    val match = (result as ExtractionResult.SingleMatch).candidate
    assertEquals("JC202611170004918273", match.value)
    assertEquals("TX ID", match.matchedLabel)
  }

  @Test
  fun `easypaisa receipt extracts multi-line long ID with leading zeros`() {
    val receiptLines =
      listOf(
        "easypaisa",
        "Money Sent",
        "Transaction ID",
        "00001234567890123456",
        "Fee: Rs. 0.00",
        "Account Number: 03459876543",
      )

    val result = extractor.extract(receiptLines, sourceAppName = "Easypaisa")
    assertTrue(result is ExtractionResult.SingleMatch)
    val match = (result as ExtractionResult.SingleMatch).candidate
    assertEquals("00001234567890123456", match.value)
    assertEquals(20, match.value.length)
  }

  @Test
  fun `mashreq bank receipt extracts transaction reference and ignores IBAN and AED amount`() {
    val receiptLines =
      listOf(
        "Mashreq Bank",
        "Transfer Completed",
        "Transaction Reference: MSHQ261117000849215566",
        "Amount: AED 1,200.00",
        "IBAN: AE070331234567890123456",
      )

    val result = extractor.extract(receiptLines, sourceAppName = "Mashreq Bank")
    assertTrue(result is ExtractionResult.SingleMatch)
    val match = (result as ExtractionResult.SingleMatch).candidate
    assertEquals("MSHQ261117000849215566", match.value)
    assertEquals("Transaction Reference", match.matchedLabel)
  }

  @Test
  fun `raqami bank receipt extracts hyphenated payment reference and very long reference`() {
    val receiptLines =
      listOf(
        "Raqami Islamic Digital Bank",
        "Payment Successful",
        "Payment Reference: RQM-20261117-000049281",
        "Account Number: 01928374650011",
      )

    val result = extractor.extract(receiptLines, sourceAppName = "Raqami Bank")
    assertTrue(result is ExtractionResult.SingleMatch)
    val match = (result as ExtractionResult.SingleMatch).candidate
    assertEquals("RQM-20261117-000049281", match.value)
    assertEquals("Payment Reference", match.matchedLabel)
  }

  @Test
  fun `very long 32-char identifier with leading zeros is never truncated`() {
    val longId = "00009988776655443322110099887766"
    val receiptLines = listOf("Reference ID: $longId", "Amount: Rs. 990.00")

    val result = extractor.extract(receiptLines)
    assertTrue(result is ExtractionResult.SingleMatch)
    assertEquals(longId, (result as ExtractionResult.SingleMatch).candidate.value)
  }

  @Test
  fun `multiple distinct identifiers require user confirmation`() {
    val receiptLines =
      listOf(
        "Raqami Bank",
        "Transaction ID: 001122334455",
        "Reference Number: FT261117000981",
        "Amount: PKR 10,000.00",
      )

    val result = extractor.extract(receiptLines)
    assertTrue(result is ExtractionResult.MultipleCandidates)
    val candidates = (result as ExtractionResult.MultipleCandidates).candidates
    assertEquals(2, candidates.size)
    assertTrue(candidates.any { it.value == "001122334455" })
    assertTrue(candidates.any { it.value == "FT261117000981" })
  }

  @Test
  fun `duplicate identical identifier under two labels deduplicates to SingleMatch`() {
    val receiptLines =
      listOf(
        "Transaction ID: 009988776655",
        "TID: 009988776655",
      )

    val result = extractor.extract(receiptLines)
    assertTrue(result is ExtractionResult.SingleMatch)
    assertEquals("009988776655", (result as ExtractionResult.SingleMatch).candidate.value)
  }

  @Test
  fun `incorrect or unrelated labels are strictly ignored`() {
    val receiptLines =
      listOf(
        "Order ID: 1234567890",
        "Invoice Number: 8877665544",
        "Account Number: 01234567890123",
        "Tracking ID: 9988776655",
        "Mobile: 0300-1234567",
        "Amount: Rs. 4,500.00",
      )

    val result = extractor.extract(receiptLines)
    assertTrue(result is ExtractionResult.NoMatch)
  }

  @Test
  fun `failed transaction screen does not extract identifier`() {
    val receiptLines =
      listOf(
        "Transaction Failed",
        "TID: 001122334455",
      )

    val result = extractor.extract(receiptLines)
    assertTrue(result is ExtractionResult.NoMatch)
  }

  @Test
  fun `low OCR confidence prevents acceptance`() {
    val receiptLines = listOf("Transaction ID: 004819203847")

    val result =
      extractor.extract(
        lines = receiptLines,
        detectionSource = DetectionSource.PHOTO_PICKER_OCR,
        baseOcrConfidence = 0.25f,
        minConfidenceThreshold = 0.75f,
      )
    assertTrue(result is ExtractionResult.LowConfidence)
  }
}
