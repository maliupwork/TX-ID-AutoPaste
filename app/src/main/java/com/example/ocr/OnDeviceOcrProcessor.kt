package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.domain.OcrEngineState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class OcrLineBlock(
  val text: String,
  val top: Int,
  val bottom: Int,
  val left: Int,
  val confidence: Float,
)

data class OcrScanOutput(
  val lines: List<String>,
  val averageConfidence: Float,
  val hadAnyText: Boolean,
)

/**
 * 100% on-device OCR processor backed by bundled Google ML Kit Text Recognition.
 * Zero network calls, zero image persistence, immediate bitmap disposal.
 */
class OnDeviceOcrProcessor(private val context: Context) {

  private val recognizer: TextRecognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  private val _engineState = MutableStateFlow(OcrEngineState.IDLE)
  val engineState: StateFlow<OcrEngineState> = _engineState.asStateFlow()

  fun syncWithMasterState(isMasterEnabled: Boolean) {
    if (_engineState.value != OcrEngineState.PROCESSING) {
      _engineState.value = if (isMasterEnabled) OcrEngineState.READY else OcrEngineState.IDLE
    }
  }

  suspend fun processImageUri(uri: Uri): OcrScanOutput =
    withContext(Dispatchers.IO) {
      val bitmap = decodeBitmapSafely(uri)
      processBitmap(bitmap, recycleAfterScan = true)
    }

  suspend fun processBitmap(
    bitmap: Bitmap,
    recycleAfterScan: Boolean = true,
  ): OcrScanOutput {
    _engineState.value = OcrEngineState.PROCESSING
    return try {
      val inputImage = InputImage.fromBitmap(bitmap, 0)
      val visionText = awaitVisionText(inputImage)
      val output = buildStructuredOutput(visionText)
      _engineState.value = OcrEngineState.READY
      output
    } catch (e: Exception) {
      _engineState.value = OcrEngineState.ERROR
      throw e
    } finally {
      if (recycleAfterScan && !bitmap.isRecycled) {
        bitmap.recycle()
      }
    }
  }

  private suspend fun awaitVisionText(image: InputImage): Text =
    suspendCancellableCoroutine { cont ->
      recognizer
        .process(image)
        .addOnSuccessListener { result ->
          if (cont.isActive) cont.resume(result)
        }
        .addOnFailureListener { error ->
          if (cont.isActive) cont.resumeWithException(error)
        }
    }

  @Suppress("DEPRECATION")
  private fun decodeBitmapSafely(uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }
    } else {
      MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
  }

  internal fun buildStructuredOutput(visionText: Text): OcrScanOutput {
    val rawLines = mutableListOf<OcrLineBlock>()
    for (block in visionText.textBlocks) {
      for (line in block.lines) {
        val rect = line.boundingBox
        val elementConfidences =
          line.elements.mapNotNull { el ->
            val conf = el.confidence
            if (conf > 0f) conf else null
          }
        val lineConfidence =
          if (elementConfidences.isNotEmpty()) elementConfidences.average().toFloat() else 0.88f

        rawLines.add(
          OcrLineBlock(
            text = line.text.trim(),
            top = rect?.top ?: 0,
            bottom = rect?.bottom ?: 0,
            left = rect?.left ?: 0,
            confidence = lineConfidence,
          )
        )
      }
    }
    return stitchSpatialLines(rawLines)
  }

  /**
   * Merges two-column receipt lines that share the same horizontal row
   * (e.g. "Transaction ID" on left + "004819203847" on right).
   */
  internal fun stitchSpatialLines(rawLines: List<OcrLineBlock>): OcrScanOutput {
    val nonEmpty = rawLines.filter { it.text.isNotBlank() }
    if (nonEmpty.isEmpty()) {
      return OcrScanOutput(emptyList(), 0f, hadAnyText = false)
    }

    val sorted = nonEmpty.sortedWith(compareBy<OcrLineBlock> { it.top }.thenBy { it.left })
    val groupedRows = mutableListOf<MutableList<OcrLineBlock>>()

    for (item in sorted) {
      val itemCenterY = (item.top + item.bottom) / 2f
      val itemHeight = (item.bottom - item.top).coerceAtLeast(20)
      val matchingRow =
        groupedRows.lastOrNull()?.takeIf { row ->
          val anchor = row.first()
          val anchorCenterY = (anchor.top + anchor.bottom) / 2f
          val anchorHeight = (anchor.bottom - anchor.top).coerceAtLeast(20)
          val threshold = ((itemHeight + anchorHeight) / 2f) * 0.55f
          abs(itemCenterY - anchorCenterY) <= threshold
        }

      if (matchingRow != null) {
        matchingRow.add(item)
      } else {
        groupedRows.add(mutableListOf(item))
      }
    }

    val stitchedLines =
      groupedRows.map { row ->
        row.sortedBy { it.left }.joinToString(" ") { it.text }
      }
    val avgConfidence = nonEmpty.map { it.confidence }.average().toFloat()

    return OcrScanOutput(
      lines = stitchedLines,
      averageConfidence = avgConfidence,
      hadAnyText = true,
    )
  }

  fun close() {
    runCatching { recognizer.close() }
  }
}
