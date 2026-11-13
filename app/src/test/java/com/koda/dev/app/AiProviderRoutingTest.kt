package com.koda.dev.app

import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ChatRequest
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.model.ProviderConfig
import com.koda.dev.core.model.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRoutingTest {
    private val unusedSseFactory = object : EventSource.Factory {
        override fun newEventSource(request: Request, listener: EventSourceListener): EventSource =
            object : EventSource {
                override fun cancel() = Unit
                override fun request(): Request = request
            }
    }

    @Test
    fun `Anthropic selection returns an explicit unsupported error instead of fake chat text`() = runTest {
        val provider = createAiProvider(
            config = ProviderConfig(
                id = "anthropic",
                providerType = ProviderType.ANTHROPIC,
                name = "Anthropic",
                model = "claude-test",
                baseUrl = "https://api.anthropic.com"
            ),
            apiKey = "test-key",
            client = OkHttpClient(),
            sseFactory = unusedSseFactory
        )

        val events = provider.streamChat(
            ChatRequest(messages = listOf(ChatMessage(ChatMessage.Role.USER, "hello")), model = "claude-test")
        ).toList()

        assertTrue(events.single() is ChatStreamEvent.Error)
        assertTrue((events.single() as ChatStreamEvent.Error).message.contains("not implemented"))
        assertFalse(events.any { it is ChatStreamEvent.TextDelta })
    }
}
