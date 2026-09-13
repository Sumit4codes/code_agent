package com.codeagent.core.testing

import com.codeagent.core.ai.AiProvider
import com.codeagent.core.ai.ChatRequest
import com.codeagent.core.ai.ChatStreamEvent
import com.codeagent.core.ai.ModelInfo
import com.codeagent.core.ai.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeAiProvider : AiProvider {
    private val responseQueue = mutableListOf<List<ChatStreamEvent>>()
    val recordedRequests = mutableListOf<ChatRequest>()

    fun enqueueResponse(events: List<ChatStreamEvent>) {
        responseQueue.add(events)
    }

    fun enqueueText(text: String) {
        responseQueue.add(listOf(ChatStreamEvent.TextDelta(text), ChatStreamEvent.Done))
    }

    fun enqueueToolCall(call: ToolCall, thenText: String? = null) {
        val events = mutableListOf<ChatStreamEvent>(
            ChatStreamEvent.ToolCallComplete(call)
        )
        if (thenText != null) {
            events.add(ChatStreamEvent.TextDelta(thenText))
        }
        events.add(ChatStreamEvent.Done)
        responseQueue.add(events)
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> {
        recordedRequests.add(request)
        val events = if (responseQueue.isNotEmpty()) {
            responseQueue.removeAt(0)
        } else {
            listOf(ChatStreamEvent.TextDelta("Fake response"), ChatStreamEvent.Done)
        }
        return flow {
            events.forEach { emit(it) }
        }
    }

    override suspend fun listModels(): List<ModelInfo> = listOf(
        ModelInfo("fake-model", "Fake Model", 4096)
    )
}
