package com.koda.dev.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Explicit failure provider used when a configured protocol has no chat implementation. */
class UnsupportedAiProvider(
    private val reason: String
) : AiProvider {
    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> = flow {
        emit(ChatStreamEvent.Error(reason))
    }

    override suspend fun listModels(): List<ModelInfo> = emptyList()
}
