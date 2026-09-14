package com.codeagent.core.agent

object EditResolver {

    data class ResolveResult(
        val success: Boolean,
        val proposedContent: String,
        val errorMessage: String? = null
    )

    private val PLACEHOLDER_REGEX = Regex(
        """(?i)^\s*(//|/\*|#|<!--|;|--)\s*(\.\.\.|…|rest of|existing code|remains unchanged|code unchanged).*$"""
    )

    /**
     * Resolves the proposed full file content from the original file content and the LLM's edit arguments.
     *
     * Supports:
     * 1. Targeted replacement when [oldContent] is provided (exact, line-ending normalized, or trimmed matching).
     * 2. Placeholder splicing when [newContent] contains comments like `// ... existing code ...`.
     * 3. Automatic snippet detection when [newContent] is only an edited sub-block (e.g. while loop) of [originalContent].
     * 4. Safety guard preventing catastrophic truncation when a short snippet does not match headers/footers.
     */
    fun resolveEdit(
        originalContent: String,
        newContent: String,
        oldContent: String? = null
    ): ResolveResult {
        // Case 1: Targeted edit with old_content provided
        if (!oldContent.isNullOrBlank()) {
            return resolveTargetedEdit(originalContent, newContent, oldContent)
        }

        // Case 2: No old_content provided
        if (originalContent.isBlank() || originalContent == newContent) {
            return ResolveResult(success = true, proposedContent = newContent)
        }

        val hasCrLf = originalContent.contains("\r\n")
        val normalizedOrig = originalContent.replace("\r\n", "\n")
        val normalizedNew = newContent.replace("\r\n", "\n")

        // 2a. Placeholder comment handling (e.g. // ... existing code ...)
        val placeholderResult = tryResolvePlaceholders(normalizedOrig, normalizedNew)
        if (placeholderResult != null) {
            val finalContent = if (hasCrLf) placeholderResult.replace("\n", "\r\n") else placeholderResult
            return ResolveResult(success = true, proposedContent = finalContent)
        }

        // 2b. Snippet detection: check if newContent is an edited subsegment of originalContent
        val snippetResult = tryResolveSnippet(normalizedOrig, normalizedNew)
        if (snippetResult != null) {
            val finalContent = if (hasCrLf) snippetResult.replace("\n", "\r\n") else snippetResult
            return ResolveResult(success = true, proposedContent = finalContent)
        }

        // 2c. Safety guard: detect catastrophic truncation when a small snippet was passed without anchors
        val origLines = normalizedOrig.lines()
        val newLines = normalizedNew.lines()
        if (origLines.size >= 5 && newLines.size <= origLines.size / 2) {
            val firstOrigNonBlank = origLines.firstOrNull { it.isNotBlank() }?.trim()
            val firstNewNonBlank = newLines.firstOrNull { it.isNotBlank() }?.trim()
            if (firstOrigNonBlank != null && firstNewNonBlank != null && firstOrigNonBlank != firstNewNonBlank) {
                return ResolveResult(
                    success = false,
                    proposedContent = originalContent,
                    errorMessage = "Safety guard: The proposed edit appears to be a partial snippet that would delete ${origLines.size - newLines.size} lines of existing code. Please specify 'old_content' with the exact code snippet to replace, and 'content' with the new code."
                )
            }
        }

        return ResolveResult(success = true, proposedContent = newContent)
    }

    private fun resolveTargetedEdit(
        originalContent: String,
        newContent: String,
        oldContent: String
    ): ResolveResult {
        val hasCrLf = originalContent.contains("\r\n")
        val normalizedOrig = originalContent.replace("\r\n", "\n")
        val normalizedOld = oldContent.replace("\r\n", "\n")
        val normalizedNew = newContent.replace("\r\n", "\n")

        // Direct exact match
        val directIndex = normalizedOrig.indexOf(normalizedOld)
        if (directIndex >= 0) {
            val replaced = normalizedOrig.substring(0, directIndex) +
                normalizedNew +
                normalizedOrig.substring(directIndex + normalizedOld.length)
            val finalContent = if (hasCrLf) replaced.replace("\n", "\r\n") else replaced
            return ResolveResult(success = true, proposedContent = finalContent)
        }

        // Trimmed line matching fallback (handles indentation difference or minor whitespace variations)
        val origLines = normalizedOrig.lines()
        val oldLines = normalizedOld.lines()
        val matchRange = findMatchingLineRange(origLines, oldLines)
        if (matchRange != null) {
            val (startIdx, endIdx) = matchRange
            val prefix = origLines.subList(0, startIdx)
            val suffix = origLines.subList(endIdx + 1, origLines.size)
            val replacementLines = normalizedNew.lines()
            val combined = (prefix + replacementLines + suffix).joinToString("\n")
            val finalContent = if (hasCrLf) combined.replace("\n", "\r\n") else combined
            return ResolveResult(success = true, proposedContent = finalContent)
        }

        return ResolveResult(
            success = false,
            proposedContent = originalContent,
            errorMessage = "Could not find 'old_content' in the file. Please ensure 'old_content' matches the existing text in the file. Use read_file to inspect the file first."
        )
    }

    private fun tryResolveSnippet(
        normalizedOrig: String,
        normalizedNew: String
    ): String? {
        val origLines = normalizedOrig.lines()
        val newLines = normalizedNew.lines()

        if (origLines.size < 3 || newLines.size >= origLines.size) return null

        val firstNewNonBlankIdx = newLines.indexOfFirst { it.isNotBlank() }
        val lastNewNonBlankIdx = newLines.indexOfLast { it.isNotBlank() }
        if (firstNewNonBlankIdx < 0 || lastNewNonBlankIdx < 0) return null

        val firstNewTrimmed = newLines[firstNewNonBlankIdx].trim()
        val lastNewTrimmed = newLines[lastNewNonBlankIdx].trim()

        val firstOrigNonBlank = origLines.firstOrNull { it.isNotBlank() }?.trim()
        val lastOrigNonBlank = origLines.lastOrNull { it.isNotBlank() }?.trim()

        // If the new content starts with the original header AND ends with the original footer, it's not a subsegment snippet
        if (firstNewTrimmed == firstOrigNonBlank && lastNewTrimmed == lastOrigNonBlank) {
            return null
        }

        // Search for where the snippet matches in origLines
        // Find candidates where origLines matches firstNewTrimmed
        for (i in origLines.indices) {
            if (origLines[i].trim() != firstNewTrimmed) continue

            // Check if lastNewTrimmed also matches downstream
            val remainingNewCount = lastNewNonBlankIdx - firstNewNonBlankIdx
            val searchWindowStart = i
            val searchWindowEnd = minOf(origLines.size - 1, i + remainingNewCount + 10)

            var matchedEndOrig = -1
            for (j in searchWindowStart..searchWindowEnd) {
                if (origLines[j].trim() == lastNewTrimmed) {
                    matchedEndOrig = j
                    break
                }
            }

            if (matchedEndOrig >= i) {
                // Verified snippet candidate between i and matchedEndOrig
                // Only treat as snippet if it leaves code before or after untouched
                if (i > 0 || matchedEndOrig < origLines.size - 1) {
                    val prefix = origLines.subList(0, i)
                    val suffix = origLines.subList(matchedEndOrig + 1, origLines.size)
                    return (prefix + newLines + suffix).joinToString("\n")
                }
            }
        }

        return null
    }

    private fun tryResolvePlaceholders(
        normalizedOrig: String,
        normalizedNew: String
    ): String? {
        val newLines = normalizedNew.lines()
        val hasPlaceholders = newLines.any { line -> PLACEHOLDER_REGEX.matches(line.trim()) }
        if (!hasPlaceholders) return null

        val origLines = normalizedOrig.lines()
        val chunks = mutableListOf<List<String>>()
        var currentChunk = mutableListOf<String>()

        for (line in newLines) {
            if (PLACEHOLDER_REGEX.matches(line.trim())) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk)
                    currentChunk = mutableListOf()
                }
            } else {
                currentChunk.add(line)
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        if (chunks.isEmpty()) return null

        var workingOrig = origLines.toMutableList()

        for (chunk in chunks) {
            val nonBlank = chunk.filter { it.isNotBlank() }
            if (nonBlank.isEmpty()) continue

            val firstTrimmed = nonBlank.first().trim()
            val lastTrimmed = nonBlank.last().trim()

            // Exact match or best similarity match
            var startIdx = workingOrig.indexOfFirst { it.trim() == firstTrimmed }
            var endIdx = -1

            if (startIdx >= 0) {
                endIdx = workingOrig.subList(startIdx, workingOrig.size)
                    .indexOfLast { it.trim() == lastTrimmed }
                    .let { if (it >= 0) startIdx + it else startIdx }
            } else {
                // Find best matching line by similarity
                val bestStart = workingOrig.indices.maxByOrNull { lineSimilarity(workingOrig[it], firstTrimmed) }
                if (bestStart != null && lineSimilarity(workingOrig[bestStart], firstTrimmed) >= 0.4) {
                    startIdx = bestStart
                    val bestEnd = (startIdx until workingOrig.size)
                        .maxByOrNull { lineSimilarity(workingOrig[it], lastTrimmed) }
                    endIdx = if (bestEnd != null && lineSimilarity(workingOrig[bestEnd], lastTrimmed) >= 0.4) {
                        bestEnd
                    } else {
                        startIdx
                    }
                }
            }

            if (startIdx >= 0 && endIdx >= startIdx) {
                val prefix = workingOrig.subList(0, startIdx)
                val suffix = workingOrig.subList(endIdx + 1, workingOrig.size)
                workingOrig = (prefix + chunk + suffix).toMutableList()
            }
        }

        return workingOrig.joinToString("\n")
    }

    private fun lineSimilarity(line1: String, line2: String): Double {
        val t1 = line1.trim()
        val t2 = line2.trim()
        if (t1 == t2) return 1.0
        if (t1.isEmpty() || t2.isEmpty()) return 0.0

        val commonPrefixLen = t1.commonPrefixWith(t2).length
        val prefixScore = (2.0 * commonPrefixLen) / (t1.length + t2.length)

        val splitRegex = Regex("""\s+|(?<=[^a-zA-Z0-9])|(?=[^a-zA-Z0-9])""")
        val tokens1 = t1.split(splitRegex).filter { it.isNotBlank() }.toSet()
        val tokens2 = t2.split(splitRegex).filter { it.isNotBlank() }.toSet()
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        val jaccard = if (union > 0) intersection.toDouble() / union else 0.0

        return 0.5 * prefixScore + 0.5 * jaccard
    }

    private fun findMatchingLineRange(
        origLines: List<String>,
        oldLines: List<String>
    ): Pair<Int, Int>? {
        val trimmedOld = oldLines.map { it.trim() }
        val startNonBlank = trimmedOld.indexOfFirst { it.isNotEmpty() }
        val endNonBlank = trimmedOld.indexOfLast { it.isNotEmpty() }
        if (startNonBlank < 0) return null

        val oldSlice = trimmedOld.subList(startNonBlank, endNonBlank + 1)
        val sliceSize = oldSlice.size

        for (i in 0..(origLines.size - sliceSize)) {
            var allMatch = true
            for (j in 0 until sliceSize) {
                if (origLines[i + j].trim() != oldSlice[j]) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) {
                return Pair(i, i + sliceSize - 1)
            }
        }
        return null
    }
}
