package com.koda.dev.feature.chat

import com.koda.dev.core.model.Message
import com.koda.dev.core.model.MessageRole
import com.koda.dev.core.model.ToolCallData
import com.koda.dev.feature.chat.components.deduplicateToolCallsForDisplay
import com.koda.dev.feature.chat.components.groupMessagesIntoTurns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnUtilsTest {

    @Test
    fun emptyListReturnsEmptyList() {
        val result = groupMessagesIntoTurns(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleTurnUserAndAssistantPreserved() {
        val messages = listOf(
            Message(
                id = "u1",
                sessionId = "s1",
                role = MessageRole.USER,
                content = "Hello",
                timestamp = 1000L
            ),
            Message(
                id = "a1",
                sessionId = "s1",
                role = MessageRole.ASSISTANT,
                content = "Hi there!",
                timestamp = 1001L
            )
        )

        val result = groupMessagesIntoTurns(messages)
        assertEquals(2, result.size)
        assertEquals("u1", result[0].id)
        assertEquals("a1", result[1].id)
        assertEquals("Hi there!", result[1].content)
        assertTrue(result[1].toolCalls.isEmpty())
    }

    @Test
    fun multipleAssistantStepsGroupedIntoSingleTurnWithAllToolCalls() {
        val messages = listOf(
            Message(
                id = "u1",
                sessionId = "s1",
                role = MessageRole.USER,
                content = "Fix the bug",
                timestamp = 1000L
            ),
            Message(
                id = "a1",
                sessionId = "s1",
                role = MessageRole.ASSISTANT,
                content = "Inspecting...",
                toolCalls = listOf(
                    ToolCallData(id = "tc1", name = "read_file", arguments = "{\"path\": \"A.kt\"}")
                ),
                timestamp = 1001L
            ),
            Message(
                id = "t1",
                sessionId = "s1",
                role = MessageRole.TOOL,
                content = "File content A",
                toolCallId = "tc1",
                timestamp = 1002L
            ),
            Message(
                id = "a2",
                sessionId = "s1",
                role = MessageRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    ToolCallData(id = "tc2", name = "edit_file", arguments = "{\"path\": \"A.kt\"}")
                ),
                timestamp = 1003L
            ),
            Message(
                id = "t2",
                sessionId = "s1",
                role = MessageRole.TOOL,
                content = "Success",
                toolCallId = "tc2",
                timestamp = 1004L
            ),
            Message(
                id = "a3",
                sessionId = "s1",
                role = MessageRole.ASSISTANT,
                content = "I have fixed the bug.",
                toolCalls = emptyList(),
                timestamp = 1005L
            )
        )

        val result = groupMessagesIntoTurns(messages)
        assertEquals(2, result.size)
        assertEquals("u1", result[0].id)
        assertEquals(MessageRole.USER, result[0].role)

        val assistantTurn = result[1]
        assertEquals("a1", assistantTurn.id)
        assertEquals(MessageRole.ASSISTANT, assistantTurn.role)
        assertEquals("Inspecting...\n\nI have fixed the bug.", assistantTurn.content)
        assertEquals(2, assistantTurn.toolCalls.size)
        assertEquals("tc1", assistantTurn.toolCalls[0].id)
        assertEquals("read_file", assistantTurn.toolCalls[0].name)
        assertEquals("tc2", assistantTurn.toolCalls[1].id)
        assertEquals("edit_file", assistantTurn.toolCalls[1].name)
        assertEquals(1005L, assistantTurn.timestamp)
    }

    @Test
    fun separateTurnsAreNotMergedTogether() {
        val messages = listOf(
            Message(id = "u1", sessionId = "s1", role = MessageRole.USER, content = "Turn 1 prompt", timestamp = 1000L),
            Message(id = "a1_1", sessionId = "s1", role = MessageRole.ASSISTANT, content = "", toolCalls = listOf(ToolCallData("tc1", "read_file", "{}")), timestamp = 1001L),
            Message(id = "a1_2", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Turn 1 answer", timestamp = 1002L),
            Message(id = "u2", sessionId = "s1", role = MessageRole.USER, content = "Turn 2 prompt", timestamp = 2000L),
            Message(id = "a2_1", sessionId = "s1", role = MessageRole.ASSISTANT, content = "", toolCalls = listOf(ToolCallData("tc2", "run_command", "{}")), timestamp = 2001L),
            Message(id = "a2_2", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Turn 2 answer", timestamp = 2002L)
        )

        val result = groupMessagesIntoTurns(messages)
        assertEquals(4, result.size)
        assertEquals("Turn 1 prompt", result[0].content)
        assertEquals("Turn 1 answer", result[1].content)
        assertEquals(1, result[1].toolCalls.size)
        assertEquals("read_file", result[1].toolCalls[0].name)

        assertEquals("Turn 2 prompt", result[2].content)
        assertEquals("Turn 2 answer", result[3].content)
        assertEquals(1, result[3].toolCalls.size)
        assertEquals("run_command", result[3].toolCalls[0].name)
    }

    @Test
    fun consecutiveIdenticalThoughtsAreDeduplicated() {
        val messages = listOf(
            Message(id = "u1", sessionId = "s1", role = MessageRole.USER, content = "Build project", timestamp = 1000L),
            Message(id = "a1", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Checking status...", timestamp = 1001L),
            Message(id = "a2", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Checking status...", timestamp = 1002L),
            Message(id = "a3", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Build complete!", timestamp = 1003L)
        )

        val result = groupMessagesIntoTurns(messages)
        assertEquals(2, result.size)
        assertEquals("Checking status...\n\nBuild complete!", result[1].content)
    }

    @Test
    fun intermediateThoughtsCompactedWhenMoreThan3Updates() {
        val messages = listOf(
            Message(id = "u1", sessionId = "s1", role = MessageRole.USER, content = "Run long task", timestamp = 1000L),
            Message(id = "a1", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Step 1: Starting", timestamp = 1001L),
            Message(id = "a2", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Step 2: Compiling", timestamp = 1002L),
            Message(id = "a3", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Step 3: Linking", timestamp = 1003L),
            Message(id = "a4", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Step 4: Packaging", timestamp = 1004L),
            Message(id = "a5", sessionId = "s1", role = MessageRole.ASSISTANT, content = "Step 5: Done", timestamp = 1005L)
        )

        val result = groupMessagesIntoTurns(messages)
        assertEquals(2, result.size)
        val expected = "Step 1: Starting\n\n... (3 intermediate progress updates) ...\n\nStep 5: Done"
        assertEquals(expected, result[1].content)
    }

    @Test
    fun deduplicateToolCallsForDisplayCollapsesRepeatedManageTaskStatus() {
        val toolCalls = listOf(
            ToolCallData(id = "tc1", name = "run_command", arguments = """{"command":"./build.sh"}"""),
            ToolCallData(id = "tc2", name = "manage_task", arguments = """{"action":"status","task_id":"task_1"}"""),
            ToolCallData(id = "tc3", name = "manage_task", arguments = """{"action":"status","task_id":"task_1"}"""),
            ToolCallData(id = "tc4", name = "manage_task", arguments = """{"action":"status","task_id":"task_1"}""")
        )

        val deduplicated = deduplicateToolCallsForDisplay(toolCalls)
        assertEquals(2, deduplicated.size)
        assertEquals("run_command", deduplicated[0].name)
        assertEquals("manage_task", deduplicated[1].name)
        assertTrue(deduplicated[1].arguments.contains("\"_check_count\":3"))
    }

    @Test
    fun formatRelativeTimeComputesCorrectUnits() {
        val now = 1_700_000_000_000L
        assertEquals("Just now", com.koda.dev.feature.chat.components.formatRelativeTime(now - 15_000L, now))
        assertEquals("2m ago", com.koda.dev.feature.chat.components.formatRelativeTime(now - 120_000L, now))
        assertEquals("3h ago", com.koda.dev.feature.chat.components.formatRelativeTime(now - 3 * 3600_000L, now))
        assertEquals("2d ago", com.koda.dev.feature.chat.components.formatRelativeTime(now - 2 * 86400_000L, now))
    }

    @Test
    fun calculateDiffStatsCountsFilesAndLines() {
        val changes = listOf(
            com.koda.dev.core.model.PendingChange(
                id = "c1",
                sessionId = "s1",
                filePath = "FileA.kt",
                changeType = com.koda.dev.core.model.ChangeType.CREATE,
                originalContent = null,
                proposedContent = "line 1\nline 2\nline 3",
                createdAt = 1000L
            ),
            com.koda.dev.core.model.PendingChange(
                id = "c2",
                sessionId = "s1",
                filePath = "FileB.kt",
                changeType = com.koda.dev.core.model.ChangeType.EDIT,
                originalContent = "line 1\nline 2",
                proposedContent = "line 1\nline 2 modified\nline 3",
                createdAt = 1001L
            )
        )

        val stats = com.koda.dev.feature.chat.components.calculateDiffStats(changes)
        assertEquals(2, stats.filesCount)
        assertTrue(stats.additions > 0)
    }

    @Test
    fun toHumanActionParsesToolSummary() {
        val toolCall = ToolCallData(
            id = "t1",
            name = "run_command",
            arguments = """{"CommandLine":"git status","toolSummary":"Check git status"}""",
            result = "On branch main",
            success = true
        )

        val action = com.koda.dev.feature.chat.components.toHumanAction(toolCall)
        assertEquals("Ran check git status", action.title)
        assertTrue(action.isTerminal)
        assertEquals("git status", action.commandLine)
    }
}
