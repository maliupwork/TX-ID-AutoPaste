package com.example.domain

/**
 * Deterministic on-device extractor for Transaction IDs and Reference Numbers.
 *
 * Design guarantees:
 * - Preserves every digit, letter, hyphen, and leading zero as an exact String.
 * - Never truncates or reformats identifiers.
 * - Anchors strictly to approved transaction/reference labels.
 * - Rejects monetary amounts, dates, times, phone numbers, IBANs, CNICs, and account numbers.
 * - Flags multiple distinct identifiers for user confirmation instead of guessing.
 */
class TransactionIdentifierExtractor {

  private data class LabelPattern(
    val canonicalLabel: String,
    val regex: Regex,
    val labelConfidence: Float,
  )

  private val labelPatterns =
    listOf(
      LabelPattern(
        canonicalLabel = "Transaction ID (TID)",
        regex = Regex("""(?i)\btransaction\s+id\s*\(\s*tid\s*\)"""),
        labelConfidence = 0.99f,
      ),
      LabelPattern(
        canonicalLabel = "Transaction ID",
        regex = Regex("""(?i)\btransaction\s+id\b"""),
        labelConfidence = 0.96f,
      ),
      LabelPattern(
        canonicalLabel = "TX ID",
        regex = Regex("""(?i)\btx\s*id\b"""),
        labelConfidence = 0.95f,
      ),
      LabelPattern(
        canonicalLabel = "Transaction Reference",
        regex = Regex("""(?i)\btransaction\s+reference(?:\s+(?:number|no\.?|id))?\b"""),
        labelConfidence = 0.96f,
      ),
      LabelPattern(
        canonicalLabel = "Payment Reference",
        regex = Regex("""(?i)\bpayment\s+reference(?:\s+(?:number|no\.?|id))?\b"""),
        labelConfidence = 0.95f,
      ),
      LabelPattern(
        canonicalLabel = "Reference Number",
        regex = Regex("""(?i)\breference\s+(?:number|no\.?)\b"""),
        labelConfidence = 0.94f,
      ),
      LabelPattern(
        canonicalLabel = "Reference ID",
        regex = Regex("""(?i)\breference\s+id\b"""),
        labelConfidence = 0.94f,
      ),
      LabelPattern(
        canonicalLabel = "TID",
        regex = Regex("""(?i)\btid\b"""),
        labelConfidence = 0.92f,
      ),
    )

  private val excludedLineKeywords =
    Regex(
      """(?i)\b(amount|total\s+amount|fee|charges|tax|balance|date|time|mobile|phone|cell|contact|iban|account\s*(?:no|number|title)|cnic|sender|receiver|from\s+account|to\s+account|merchant\s+id|order\s+id|invoice|tracking\s+id)\b"""
    )

  private val failureStatusRegex =
    Regex("""(?i)\b(transaction\s+failed|payment\s+failed|payment\s+declined|payment\s+unsuccessful|failed\s+transaction)\b""")

  private val currencyPrefixRegex = Regex("""(?i)^(rs\.?|pkr|aed|usd|eur|gbp|\$|€|£)$""")
  private val decimalAmountRegex = Regex("""^\d{1,3}(,\d{3})+(\.\d{1,2})?$|^\d+\.\d{2}$""")
  private val dateLikeRegex =
    Regex("""^(\d{1,4}[/-]\d{1,2}[/-]\d{1,4}|\d{1,2}:\d{2}(:\d{2})?)$""")
  private val phoneLikeRegex = Regex("""^(\+92|0092|0)3\d{2}-\d{7}$|^\+\d{9,15}$""")
  private val cnicLikeRegex = Regex("""^\d{5}-\d{7}-\d$""")
  private val ibanLikeRegex = Regex("""(?i)^(PK\d{2}[A-Z]{4}\d{16}|AE\d{2}\d{19})$""")
  private val validTokenRegex = Regex("""^[A-Za-z0-9][A-Za-z0-9\-_]{4,46}[A-Za-z0-9]$""")

  fun extract(
    lines: List<String>,
    sourceAppPackage: String? = null,
    sourceAppName: String? = null,
    detectionSource: DetectionSource = DetectionSource.ACCESSIBILITY,
    baseOcrConfidence: Float = 1.0f,
    minConfidenceThreshold: Float = 0.75f,
  ): ExtractionResult {
    val cleanLines = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanLines.isEmpty()) {
      return ExtractionResult.NoMatch("No readable text found")
    }

    val fullText = cleanLines.joinToString("\n")
    if (failureStatusRegex.containsMatchIn(fullText)) {
      return ExtractionResult.NoMatch("Receipt indicates a failed or declined payment")
    }

    val discovered = mutableListOf<CandidateIdentifier>()

    for (index in cleanLines.indices) {
      val line = cleanLines[index]
      val matchedPattern = findBestLabelMatch(line) ?: continue
      val matchResult = matchedPattern.regex.find(line) ?: continue

      // 1) Try same line after the matched label
      val trailingSegment = line.substring(matchResult.range.last + 1).trim()
      var candidateToken = extractTokenFromSegment(trailingSegment)

      // 2) Fallback: check the very next line if trailing text had no value (e.g. "Transaction ID:" -> "\n000482910455")
      var lineDistancePenalty = 0f
      if (candidateToken == null && index + 1 < cleanLines.size) {
        val nextLine = cleanLines[index + 1]
        if (
          findBestLabelMatch(nextLine) == null &&
            !excludedLineKeywords.containsMatchIn(nextLine)
        ) {
          candidateToken = extractTokenFromSegment(nextLine)
          lineDistancePenalty = 0.03f
        }
      }

      if (candidateToken != null) {
        val tokenQuality = scoreTokenQuality(candidateToken)
        val combinedConfidence =
          (baseOcrConfidence.coerceIn(0f, 1f) * 0.45f +
            matchedPattern.labelConfidence * 0.40f +
            tokenQuality * 0.15f -
            lineDistancePenalty)
            .coerceIn(0f, 1f)

        discovered.add(
          CandidateIdentifier(
            value = candidateToken,
            matchedLabel = matchedPattern.canonicalLabel,
            confidence = combinedConfidence,
            sourceAppPackage = sourceAppPackage,
            sourceAppName = sourceAppName,
            detectionSource = detectionSource,
          )
        )
      }
    }

    if (discovered.isEmpty()) {
      return ExtractionResult.NoMatch("No transaction or reference identifier found")
    }

    // Deduplicate identical exact IDs, keeping highest confidence
    val deduplicated =
      discovered
        .groupBy { it.value }
        .values
        .map { group -> group.maxBy { it.confidence } }
        .sortedByDescending { it.confidence }

    val confidentCandidates = deduplicated.filter { it.confidence >= minConfidenceThreshold }
    if (confidentCandidates.isEmpty()) {
      return ExtractionResult.LowConfidence(
        candidates = deduplicated,
        reason = "Detected candidate confidence below threshold (${(minConfidenceThreshold * 100).toInt()}%)",
      )
    }

    return if (confidentCandidates.size == 1) {
      ExtractionResult.SingleMatch(confidentCandidates.first())
    } else {
      ExtractionResult.MultipleCandidates(confidentCandidates)
    }
  }

  private fun findBestLabelMatch(line: String): LabelPattern? {
    // Reject lines that primarily describe excluded concepts unless a valid label explicitly starts the line
    for (pattern in labelPatterns) {
      val match = pattern.regex.find(line)
      if (match != null) {
        val prefix = line.substring(0, match.range.first).trim()
        // If prefix contains excluded words like "Amount" or "Account", do not treat it as TX label
        if (prefix.isNotEmpty() && excludedLineKeywords.containsMatchIn(prefix)) {
          continue
        }
        return pattern
      }
    }
    return null
  }

  private fun extractTokenFromSegment(segment: String): String? {
    if (segment.isEmpty()) return null
    val cleaned =
      segment
        .trimStart(':', '=', '-', '#', ' ', '\t', '|', '•')
        .trim()
    if (cleaned.isEmpty()) return null

    // Split by whitespace and inspect tokens; reject immediately if currency prefix precedes token
    val parts = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return null

    if (currencyPrefixRegex.matches(parts.first())) {
      return null
    }

    // Check first token (most standard) or bounded token stripped of trailing punctuation like "." or ","
    val rawCandidate = parts.first().trimEnd(',', '.', ';', ')', ']')
    return if (isValidIdentifierToken(rawCandidate)) rawCandidate else null
  }

  internal fun isValidIdentifierToken(token: String): Boolean {
    if (!validTokenRegex.matches(token)) return false
    if (token.length < 6 || token.length > 48) return false

    val digitCount = token.count { it.isDigit() }
    if (digitCount < 4) return false

    if (decimalAmountRegex.matches(token)) return false
    if (dateLikeRegex.matches(token)) return false
    if (phoneLikeRegex.matches(token)) return false
    if (cnicLikeRegex.matches(token)) return false
    if (ibanLikeRegex.matches(token)) return false

    val lower = token.lowercase()
    if (lower in setOf("pending", "success", "completed", "failed", "unknown")) {
      return false
    }

    return true
  }

  private fun scoreTokenQuality(token: String): Float {
    var score = 0.85f
    if (token.length >= 10) score += 0.10f
    if (token.all { it.isDigit() } || token.any { it.isLetter() } && token.any { it.isDigit() }) {
      score += 0.05f
    }
    return score.coerceAtMost(1.0f)
  }
}
