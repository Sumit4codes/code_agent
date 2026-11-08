package com.koda.dev.core.agent

import com.koda.dev.core.ai.AiProvider
import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ChatRequest
import com.koda.dev.core.ai.ChatStreamEvent

object HistoryCompactor {

    const val COMPACTION_PREFIX = "[Context Compacted: Earlier history summarized]"

    /**
     * Builds a prompt asking the model to summarize earlier conversation turns
     * into a high-density, structured technical summary.
     */
    fun buildCompactionPrompt(messages: List<ChatMessage>): String {
        val transcript = buildString {
            for (m in messages) {
                if (m.role == ChatMessage.Role.SYSTEM && m.content.startsWith(COMPACTION_PREFIX)) continue
                val roleName = when (m.role) {
                    ChatMessage.Role.USER -> "User"
                    ChatMessage.Role.ASSISTANT -> "Assistant"
                    ChatMessage.Role.TOOL -> "Tool"
                    ChatMessage.Role.SYSTEM -> "System"
                }
                append("$roleName: ${m.content.take(600)}\n")
            }
        }
        return """
            You are a technical context compactor. Summarize the preceding conversation into a concise, high-density structured summary for the AI coding agent so work can continue seamlessly without losing context.
            
            Format with markdown:
            ### Current Objectives & Progress
            - [List main goals and current status]
            
            ### Key Architectural Decisions
            - [List important decisions, configurations, dependencies, or conventions]
            
            ### Modified Files & Outputs
            - [List files created or modified]
            
            Keep the summary factual, concise, and under 400 words.
            
            Conversation to summarize:
            $transcript
        """.trimIndent()
    }

    /**
     * Executes the compaction LLM call using the provided provider and returns the structured summary.
     */
    suspend fun summarizeHistory(
        aiProvider: AiProvider,
        messages: List<ChatMessage>,
        model: String
    ): String {
        if (messages.isEmpty()) return ""
        val prompt = buildCompactionPrompt(messages)
        val request = ChatRequest(
            messages = listOf(ChatMessage(role = ChatMessage.Role.USER, content = prompt)),
            model = model,
            maxTokens = 2048
        )
        val sb = StringBuilder()
        aiProvider.streamChat(request).collect { event ->
            if (event is ChatStreamEvent.TextDelta) {
                sb.append(event.text)
            }
        }
        return sb.toString().trim()
    }
}
