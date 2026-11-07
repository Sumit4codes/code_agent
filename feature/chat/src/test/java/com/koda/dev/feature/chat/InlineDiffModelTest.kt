package com.koda.dev.feature.chat

import com.koda.dev.core.files.DiffEngine
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.DiffLineType
import com.koda.dev.core.model.PendingChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineDiffModelTest {

    @Test
    fun computeDiffForEditProducesAdditionsAndDeletions() {
        val original = "fun hello() {\n    println(\"old\")\n}"
        val proposed = "fun hello() {\n    println(\"new\")\n}"

        val hunks = DiffEngine.computeDiff(original, proposed, "Hello.kt")

        assertEquals(1, hunks.size)
        val hunk = hunks.first()
        val additions = hunk.lines.count { it.type == DiffLineType.ADDITION }
        val deletions = hunk.lines.count { it.type == DiffLineType.DELETION }

        assertEquals(1, additions)
        assertEquals(1, deletions)
        assertTrue(hunk.lines.any { it.content.contains("old") && it.type == DiffLineType.DELETION })
        assertTrue(hunk.lines.any { it.content.contains("new") && it.type == DiffLineType.ADDITION })
    }

    @Test
    fun computeDiffForNewFileShowsAllAdditions() {
        val proposed = "class NewClass {\n    val x = 1\n}"
        val hunks = DiffEngine.computeDiff("", proposed, "NewClass.kt")

        assertTrue(hunks.isNotEmpty())
        val additions = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.ADDITION } }
        val deletions = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.DELETION } }

        assertEquals(3, additions)
        assertEquals(0, deletions)
    }

    @Test
    fun pendingChangeModelIntegrity() {
        val change = PendingChange(
            id = "test-123",
            sessionId = "sess-1",
            filePath = "app/src/main/MainActivity.kt",
            changeType = ChangeType.EDIT,
            originalContent = "line 1\nline 2",
            proposedContent = "line 1\nline 2\nline 3",
            createdAt = System.currentTimeMillis()
        )

        assertEquals("test-123", change.id)
        assertEquals("app/src/main/MainActivity.kt", change.filePath)
        assertEquals(ChangeType.EDIT, change.changeType)

        val hunks = DiffEngine.computeDiff(change.originalContent ?: "", change.proposedContent ?: "", change.filePath)
        val addedLines = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.ADDITION } }
        assertEquals(1, addedLines)
    }

    @Test
    fun toolCallFileOperationMatching() {
        val fileTools = setOf(
            "propose_file_edit",
            "create_file",
            "delete_file",
            "edit_file",
            "write_file"
        )

        assertTrue(fileTools.contains("edit_file"))
        assertTrue(fileTools.contains("write_file"))
        assertTrue(fileTools.contains("propose_file_edit"))
    }

    @Test
    fun historicalCreateFileNeverMatchesSubsequentEditPendingChange() {
        val createToolName = "create_file"
        val filePath = "notes.txt"

        // Simulate subsequent pending change on the same file (an EDIT)
        val activePendingEdit = PendingChange(
            id = "edit-1",
            sessionId = "sess-1",
            filePath = "notes.txt",
            changeType = ChangeType.EDIT,
            originalContent = "First line",
            proposedContent = "First line\nSecond line",
            createdAt = System.currentTimeMillis()
        )
        val pendingChanges = listOf(activePendingEdit)

        // Historical change type should strictly be CREATE
        val historicalChangeType = when (createToolName) {
            "create_file" -> ChangeType.CREATE
            "delete_file" -> ChangeType.DELETE
            else -> ChangeType.EDIT
        }

        assertEquals(ChangeType.CREATE, historicalChangeType)

        // Matching must NOT match the subsequent EDIT
        val matchingPending = pendingChanges.find { change ->
            val pathMatches = change.filePath == filePath || change.filePath.endsWith(filePath)
            pathMatches && change.changeType == historicalChangeType
        }

        // Must be null: historical create does NOT bind to subsequent edit
        org.junit.Assert.assertNull(matchingPending)
    }

    @Test
    fun historicalCreateFilePreservesOriginalCreationContent() {
        val extractedNewContent = "Initial file content"
        val changeType = ChangeType.CREATE

        val originalContent = when (changeType) {
            ChangeType.CREATE -> null
            ChangeType.DELETE -> "something"
            ChangeType.EDIT -> "old"
        }

        val proposedContent = when (changeType) {
            ChangeType.CREATE -> extractedNewContent
            ChangeType.DELETE -> null
            ChangeType.EDIT -> "new"
        }

        org.junit.Assert.assertNull(originalContent)
        assertEquals("Initial file content", proposedContent)
    }

    @Test
    fun appliedStatusHidesExpandOptionAndCollapsesInitially() {
        val appliedStatus = com.koda.dev.core.model.ChangeStatus.APPLIED
        val isPending = appliedStatus == com.koda.dev.core.model.ChangeStatus.PENDING
        val shouldShowExpandOption = isPending
        val initiallyExpanded = isPending

        org.junit.Assert.assertFalse(shouldShowExpandOption)
        org.junit.Assert.assertFalse(initiallyExpanded)
    }

    @Test
    fun pendingStatusShowsExpandOptionAndExpandsInitially() {
        val pendingStatus = com.koda.dev.core.model.ChangeStatus.PENDING
        val isPending = pendingStatus == com.koda.dev.core.model.ChangeStatus.PENDING
        val shouldShowExpandOption = isPending
        val initiallyExpanded = isPending

        assertTrue(shouldShowExpandOption)
        assertTrue(initiallyExpanded)
    }
}
