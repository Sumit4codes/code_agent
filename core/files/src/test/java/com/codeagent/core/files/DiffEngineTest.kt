package com.codeagent.core.files

import org.junit.Assert.*
import org.junit.Test

class DiffEngineTest {

    @Test
    fun `no diff for identical content`() {
        val original = "line1\nline2\nline3"
        val hunks = DiffEngine.computeDiff(original, original)
        assertTrue(hunks.isEmpty())
    }

    @Test
    fun `detects single line addition`() {
        val original = "line1\nline3"
        val proposed = "line1\nline2\nline3"
        val hunks = DiffEngine.computeDiff(original, proposed)
        assertEquals(1, hunks.size)
        assertTrue(hunks[0].lines.any { it.type == com.codeagent.core.model.DiffLineType.ADDITION })
    }

    @Test
    fun `detects single line deletion`() {
        val original = "line1\nline2\nline3"
        val proposed = "line1\nline3"
        val hunks = DiffEngine.computeDiff(original, proposed)
        assertEquals(1, hunks.size)
        assertTrue(hunks[0].lines.any { it.type == com.codeagent.core.model.DiffLineType.DELETION })
    }

    @Test
    fun `detects line change`() {
        val original = "line1\nold\nline3"
        val proposed = "line1\nnew\nline3"
        val hunks = DiffEngine.computeDiff(original, proposed)
        assertEquals(1, hunks.size)
        assertTrue(hunks[0].lines.any { it.type == com.codeagent.core.model.DiffLineType.DELETION })
        assertTrue(hunks[0].lines.any { it.type == com.codeagent.core.model.DiffLineType.ADDITION })
    }

    @Test
    fun `unified diff format contains correct headers and line counts`() {
        val original = "line1\nline2\nline3\nline4\nline5"
        val proposed = "line1\nline2\nmodified3\nline4\nline5"
        val hunks = DiffEngine.computeDiff(original, proposed, "test.kt", contextLines = 2)
        assertEquals(1, hunks.size)
        val hunk = hunks[0]
        assertEquals(1, hunk.oldStartLine)
        assertEquals(5, hunk.oldLineCount)
        assertEquals(1, hunk.newStartLine)
        assertEquals(5, hunk.newLineCount)

        // Check context line numbers
        assertEquals(1, hunk.lines[0].oldLineNumber)
        assertEquals(1, hunk.lines[0].newLineNumber)
        assertEquals(2, hunk.lines[1].oldLineNumber)
        assertEquals(2, hunk.lines[1].newLineNumber)

        val diff = DiffEngine.generateUnifiedDiff(original, proposed, "test.kt", contextLines = 2)
        assertTrue(diff.contains("@@ -1,5 +1,5 @@"))
    }

    @Test
    fun `applyEdit returns proposed content`() {
        val original = "old content"
        val proposed = "new content"
        assertEquals(proposed, DiffEngine.applyEdit(original, proposed))
    }
}
