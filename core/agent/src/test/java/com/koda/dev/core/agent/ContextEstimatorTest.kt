package com.koda.dev.core.agent

import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.PropertyDef
import com.koda.dev.core.ai.ToolParameters
import com.koda.dev.core.ai.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextEstimatorTest {

    @Test
    fun estimatesTokensAccuratelyFromCharacters() {
        val messages = listOf(
            ChatMessage(role = ChatMessage.Role.USER, content = "a".repeat(400)),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "b".repeat(800))
        )
        val tools = listOf(
            ToolSpec(
                name = "read_file",
                description = "Reads a file",
                parameters = ToolParameters(
                    type = "object",
                    properties = mapOf("path" to PropertyDef("string", "File path"))
                )
            )
        )
        val systemPrompt = "c".repeat(400)

        val state = ContextEstimator.estimate(
            messages = messages,
            systemPrompt = systemPrompt,
            tools = tools,
            maxTokens = 128_000
        )

        assertEquals(300, state.messagesTokens) // 1200 / 4
        assertEquals(100, state.systemTokens)   // 400 / 4
        assertTrue(state.toolsTokens > 0)
        assertEquals(256, state.metaTokens)
        assertEquals(128_000, state.maxTokens)
        assertTrue(state.remainingTokens < 128_000)
        assertTrue(state.fraction > 0f)
    }

    @Test
    fun formatTokenCountHandlesVariousMagnitudes() {
        assertEquals("500", ContextEstimator.formatTokenCount(500))
        assertEquals("1.5k", ContextEstimator.formatTokenCount(1500))
        assertEquals("128k", ContextEstimator.formatTokenCount(128_000))
        assertEquals("1.0M", ContextEstimator.formatTokenCount(1_000_000))
        assertEquals("2.0M", ContextEstimator.formatTokenCount(2_000_000))
    }
}
