package com.koda.dev.core.agent

import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.testing.FakeAiProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCompactorTest {

    @Test
    fun buildCompactionPromptFormatsTranscriptAndExcludesPriorCompactionNotices() {
        val messages = listOf(
            ChatMessage(role = ChatMessage.Role.USER, content = "Build a REST API"),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "I will set up Ktor"),
            ChatMessage(role = ChatMessage.Role.SYSTEM, content = "${HistoryCompactor.COMPACTION_PREFIX}\nOld summary"),
            ChatMessage(role = ChatMessage.Role.USER, content = "Add auth endpoints")
        )

        val prompt = HistoryCompactor.buildCompactionPrompt(messages)
        assertTrue(prompt.contains("User: Build a REST API"))
        assertTrue(prompt.contains("Assistant: I will set up Ktor"))
        assertTrue(prompt.contains("User: Add auth endpoints"))
        // Prior compaction notice must be skipped to avoid nesting
        org.junit.Assert.assertFalse(prompt.contains("Old summary"))
    }

    @Test
    fun summarizeHistoryCallsProviderAndReturnsSummary() = runTest {
        val fakeProvider = FakeAiProvider()
        fakeProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("### Current Objectives\n- Built REST API\n\n### Modified Files\n- Server.kt"),
                ChatStreamEvent.Done
            )
        )

        val messages = listOf(
            ChatMessage(role = ChatMessage.Role.USER, content = "Build the server"),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Server built")
        )

        val summary = HistoryCompactor.summarizeHistory(fakeProvider, messages, "test-model")
        assertTrue(summary.contains("Built REST API"))
        assertTrue(summary.contains("Server.kt"))
    }
}
