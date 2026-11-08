package com.koda.dev.core.agent

import com.koda.dev.core.ai.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextHygieneTest {

    @Test
    fun recentSixToolsArePreservedVerbatim() {
        val messages = (1..6).map { i ->
            ChatMessage(
                role = ChatMessage.Role.TOOL,
                content = "Tool output $i: " + "X".repeat(10_000),
                toolCallId = "tc_$i"
            )
        }

        val result = ContextHygiene.shrinkToolResults(messages)
        assertEquals(6, result.size)
        // All 6 should be unchanged because RECENT_FULL_TOOLS is 6
        for (i in 0 until 6) {
            assertEquals(messages[i].content, result[i].content)
        }
    }

    @Test
    fun olderToolsExceedingThresholdAreTruncatedWithHeadAndTail() {
        val messages = mutableListOf<ChatMessage>()
        // Add 2 old tools with large content
        messages.add(ChatMessage(role = ChatMessage.Role.TOOL, content = "START_OLD_1 " + "A".repeat(10_000) + " END_OLD_1", toolCallId = "tc_old_1"))
        messages.add(ChatMessage(role = ChatMessage.Role.TOOL, content = "START_OLD_2 " + "B".repeat(10_000) + " END_OLD_2", toolCallId = "tc_old_2"))

        // Add 6 recent tools
        for (i in 1..6) {
            messages.add(ChatMessage(role = ChatMessage.Role.TOOL, content = "Recent $i", toolCallId = "tc_rec_$i"))
        }

        val result = ContextHygiene.shrinkToolResults(messages)
        assertEquals(8, result.size)

        // Old tool 1 should be truncated
        val old1 = result[0].content
        assertTrue(old1.contains("START_OLD_1"))
        assertTrue(old1.contains("END_OLD_1"))
        assertTrue(old1.contains("omitted from active prompt context"))
        assertTrue(old1.length < 5_000)

        // Recent tools must remain untouched
        for (i in 2 until 8) {
            assertEquals(messages[i].content, result[i].content)
        }
    }

    @Test
    fun sliceAfterCompactionRetainsLatestSummaryAndSubsequentMessages() {
        val messages = listOf(
            ChatMessage(role = ChatMessage.Role.USER, content = "Ancient question"),
            ChatMessage(role = ChatMessage.Role.TOOL, content = "Ancient large tool output: " + "Z".repeat(5000)),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Ancient answer"),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "### 📦 Context Compacted\n\nEarlier conversation turns have been summarized:\n- Objectives: All done"),
            ChatMessage(role = ChatMessage.Role.USER, content = "hi"),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Hello! How can I help?")
        )

        val sliced = ContextHygiene.sliceAfterCompaction(messages)
        assertEquals(3, sliced.size)
        // First message should be the system compaction directive
        assertEquals(ChatMessage.Role.SYSTEM, sliced[0].role)
        assertTrue(sliced[0].content.startsWith(HistoryCompactor.COMPACTION_PREFIX))
        assertTrue(sliced[0].content.contains("Objectives: All done"))

        // Subsequent messages are kept verbatim
        assertEquals(ChatMessage.Role.USER, sliced[1].role)
        assertEquals("hi", sliced[1].content)
        assertEquals(ChatMessage.Role.ASSISTANT, sliced[2].role)
        assertEquals("Hello! How can I help?", sliced[2].content)
    }

    @Test
    fun sliceAfterCompactionReturnsOriginalListIfNoCompactionPresent() {
        val messages = listOf(
            ChatMessage(role = ChatMessage.Role.USER, content = "hello"),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "world")
        )
        val sliced = ContextHygiene.sliceAfterCompaction(messages)
        assertEquals(messages, sliced)
    }
}
