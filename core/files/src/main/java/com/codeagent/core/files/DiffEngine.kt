package com.codeagent.core.files

import com.codeagent.core.model.DiffHunk
import com.codeagent.core.model.DiffLine
import com.codeagent.core.model.DiffLineType
import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import com.github.difflib.patch.Patch

object DiffEngine {

    fun computeDiff(
        original: String,
        proposed: String,
        filePath: String = "",
        contextLines: Int = 3
    ): List<DiffHunk> {
        val originalLines = original.lines()
        val proposedLines = proposed.lines()

        val patch: Patch<String> = DiffUtils.diff(originalLines, proposedLines)
        val hunks = mutableListOf<DiffHunk>()

        for (delta in patch.deltas) {
            val source = delta.source
            val target = delta.target
            val srcSize = source.size()
            val tgtSize = target.size()

            val startContextIndex = maxOf(0, source.position - contextLines)
            val contextBefore = originalLines.subList(startContextIndex, source.position)
            val endContextIndex = minOf(source.position + srcSize + contextLines, originalLines.size)
            val contextAfter = originalLines.subList(
                minOf(source.position + srcSize, originalLines.size),
                endContextIndex
            )

            val lines = mutableListOf<DiffLine>()
            var oldLine = startContextIndex + 1
            var newLine = target.position - contextBefore.size + 1

            for (line in contextBefore) {
                lines.add(DiffLine(DiffLineType.CONTEXT, line, oldLine++, newLine++))
            }

            when (delta.type) {
                DeltaType.DELETE -> {
                    for (line in source.lines) {
                        lines.add(DiffLine(DiffLineType.DELETION, line, oldLine++, null))
                    }
                }
                DeltaType.INSERT -> {
                    for (line in target.lines) {
                        lines.add(DiffLine(DiffLineType.ADDITION, line, null, newLine++))
                    }
                }
                DeltaType.CHANGE -> {
                    for (line in source.lines) {
                        lines.add(DiffLine(DiffLineType.DELETION, line, oldLine++, null))
                    }
                    for (line in target.lines) {
                        lines.add(DiffLine(DiffLineType.ADDITION, line, null, newLine++))
                    }
                }
                else -> {}
            }

            for (line in contextAfter) {
                lines.add(DiffLine(DiffLineType.CONTEXT, line, oldLine++, newLine++))
            }

            val oldLineCount = contextBefore.size + srcSize + contextAfter.size
            val newLineCount = contextBefore.size + tgtSize + contextAfter.size
            val oldStartLine = if (oldLineCount == 0) 0 else startContextIndex + 1
            val newStartLine = if (newLineCount == 0) 0 else target.position - contextBefore.size + 1

            hunks.add(
                DiffHunk(
                    oldStartLine = oldStartLine,
                    oldLineCount = oldLineCount,
                    newStartLine = newStartLine,
                    newLineCount = newLineCount,
                    lines = lines
                )
            )
        }

        return hunks
    }

    fun generateUnifiedDiff(
        original: String,
        proposed: String,
        filePath: String,
        contextLines: Int = 3
    ): String {
        val hunks = computeDiff(original, proposed, filePath, contextLines)
        val sb = StringBuilder()
        sb.appendLine("--- a/$filePath")
        sb.appendLine("+++ b/$filePath")
        for (hunk in hunks) {
            sb.appendLine("@@ -${hunk.oldStartLine},${hunk.oldLineCount} +${hunk.newStartLine},${hunk.newLineCount} @@")
            for (line in hunk.lines) {
                val prefix = when (line.type) {
                    DiffLineType.CONTEXT -> " "
                    DiffLineType.ADDITION -> "+"
                    DiffLineType.DELETION -> "-"
                }
                sb.appendLine("$prefix${line.content}")
            }
        }
        return sb.toString()
    }

    fun applyEdit(original: String, proposed: String): String = proposed
}
