package com.koda.dev.app

import com.koda.dev.core.ai.AnthropicAiProvider
import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ChatRequest
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.ai.OpenAiCompatibleProvider
import com.koda.dev.core.model.ProviderConfig
import com.koda.dev.core.model.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRoutingTest {

    @Test
    fun `Anthropic routing creates AnthropicAiProvider targeting v1 messages with x-api-key`() = runTest {
        var capturedUrl: String? = null
        var capturedApiKey: String? = null
        var capturedVersion: String? = null

        val sseFactory = object : EventSource.Factory {
            override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
                capturedUrl = request.url.toString()
                capturedApiKey = request.header("x-api-key")
                capturedVersion = request.header("anthropic-version")
                val source = object : EventSource {
                    override fun cancel() = Unit
                    override fun request(): Request = request
                }
                listener.onEvent(
                    source,
                    "content_block_delta",
                    "content_block_delta",
                    """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""
                )
                listener.onEvent(source, "message_stop", "message_stop", """{"type":"message_stop"}""")
                return source
            }
        }

        val provider = createAiProvider(
            config = ProviderConfig(
                id = "anthropic",
                providerType = ProviderType.ANTHROPIC,
                name = "Anthropic",
                model = "claude-3-7-sonnet-20250219",
                baseUrl = "https://api.anthropic.com"
            ),
            apiKey = "sk-ant-test-key",
            client = OkHttpClient(),
            sseFactory = sseFactory
        )

        assertTrue(provider is AnthropicAiProvider)

        val events = provider.streamChat(
            ChatRequest(
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "hello")),
                model = "claude-3-7-sonnet-20250219"
            )
        ).toList()

        assertEquals("https://api.anthropic.com/v1/messages", capturedUrl)
        assertEquals("sk-ant-test-key", capturedApiKey)
        assertEquals("2023-06-01", capturedVersion)
        assertEquals(listOf(ChatStreamEvent.TextDelta("Hello"), ChatStreamEvent.Done), events)
    }

    @Test
    fun `OpenAI-compatible and Antiroute protocols route to OpenAiCompatibleProvider`() {
        val dummySse = object : EventSource.Factory {
            override fun newEventSource(request: Request, listener: EventSourceListener): EventSource =
                object : EventSource {
                    override fun cancel() = Unit
                    override fun request(): Request = request
                }
        }

        val openAi = createAiProvider(
            config = ProviderConfig("openai", ProviderType.OPENAI_COMPATIBLE, "OpenAI", "gpt-4o", "https://api.openai.com/v1"),
            apiKey = "sk-test",
            client = OkHttpClient(),
            sseFactory = dummySse
        )
        val antiroute = createAiProvider(
            config = ProviderConfig("antiroute", ProviderType.ANTIROUTE, "Antiroute", "gemini-2.5-pro", "https://tunnel.example/v1"),
            apiKey = "sk-antigravity",
            client = OkHttpClient(),
            sseFactory = dummySse
        )

        assertTrue(openAi is OpenAiCompatibleProvider)
        assertTrue(antiroute is OpenAiCompatibleProvider)
    }
}
