package com.codeagent.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeAiProvider : AiProvider {
    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> = flow {
        emit(ChatStreamEvent.TextDelta("No AI provider configured. Please set up your API key in Settings."))
        emit(ChatStreamEvent.Done)
    }

    override suspend fun listModels(): List<ModelInfo> = emptyList()
}
