package com.koda.dev.feature.chat

import com.koda.dev.core.model.Message
import com.koda.dev.core.model.MessageRole
import com.koda.dev.core.model.ToolCallData
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
}
