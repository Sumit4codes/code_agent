package com.codeagent.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface AiProvider {
    suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent>
    suspend fun listModels(): List<ModelInfo>
}

@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val tools: List<ToolSpec> = emptyList()
)

@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
) {
    @Serializable
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, PropertyDef> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class PropertyDef(
    val type: String,
    val description: String
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

sealed class ChatStreamEvent {
    data class TextDelta(val text: String) : ChatStreamEvent()
    data class ToolCallStart(val id: String, val name: String) : ChatStreamEvent()
    data class ToolCallArgsDelta(val id: String, val args: String) : ChatStreamEvent()
    data class ToolCallComplete(val call: ToolCall) : ChatStreamEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int) : ChatStreamEvent()
    data class Error(val message: String, val cause: Throwable? = null) : ChatStreamEvent()
    data object Done : ChatStreamEvent()
}

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val contextWindow: Int? = null
)
