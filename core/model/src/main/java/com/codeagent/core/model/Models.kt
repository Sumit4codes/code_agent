package com.codeagent.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val treeUri: String,
    val lastOpened: Long
)

@Serializable
data class ChatSession(
    val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val toolCalls: List<ToolCallData> = emptyList(),
    val toolCallId: String? = null,
    val timestamp: Long
)

@Serializable
enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}

@Serializable
data class ToolCallData(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null
)

@Serializable
data class PendingChange(
    val id: String,
    val sessionId: String,
    val filePath: String,
    val changeType: ChangeType,
    val originalContent: String? = null,
    val proposedContent: String? = null,
    val createdAt: Long,
    val status: ChangeStatus = ChangeStatus.PENDING
)

@Serializable
enum class ChangeType {
    EDIT, CREATE, DELETE
}

@Serializable
enum class ChangeStatus {
    PENDING, APPROVED, REJECTED, APPLIED
}

@Serializable
data class ProviderConfig(
    val id: String,
    val providerType: ProviderType,
    val name: String,
    val model: String,
    val baseUrl: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String? = null,
    val isDefault: Boolean = false
)

@Serializable
enum class ProviderType {
    OPENAI_COMPATIBLE, ANTHROPIC
}

@Serializable
data class DiffHunk(
    val oldStartLine: Int,
    val oldLineCount: Int,
    val newStartLine: Int,
    val newLineCount: Int,
    val lines: List<DiffLine>
)

@Serializable
enum class DiffLineType {
    CONTEXT, ADDITION, DELETION
}

@Serializable
data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)
