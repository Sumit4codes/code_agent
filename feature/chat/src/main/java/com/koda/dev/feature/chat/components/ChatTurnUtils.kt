package com.koda.dev.feature.chat.components

import com.koda.dev.core.model.Message
import com.koda.dev.core.model.MessageRole

/**
 * Consolidates consecutive assistant messages in a single turn so that intermediate tool-execution
 * steps are grouped together with the final turn response, preventing multiple redundant assistant
 * bubbles from cluttering the chat history.
 */
fun groupMessagesIntoTurns(rawMessages: List<Message>): List<Message> {
    val nonToolMessages = rawMessages.filter { it.role != MessageRole.TOOL }
    if (nonToolMessages.isEmpty()) return emptyList()

    val result = mutableListOf<Message>()
    val currentAssistantBatch = mutableListOf<Message>()

    fun flushAssistantBatch() {
        if (currentAssistantBatch.isEmpty()) return
        if (currentAssistantBatch.size == 1) {
            result.add(currentAssistantBatch.first())
        } else {
            // Combine all tool calls in chronological order
            val allToolCalls = currentAssistantBatch.flatMap { it.toolCalls }

            // Join non-blank content in chronological order
            val nonBlankContents = currentAssistantBatch
                .map { it.content.trim() }
                .filter { it.isNotBlank() }
            val combinedContent = nonBlankContents.joinToString("\n\n")

            val firstMsg = currentAssistantBatch.first()
            val lastMsg = currentAssistantBatch.last()

            // Use firstMsg.id for stable compose identity across streaming steps,
            // while updating toolCalls, content, and timestamp to reflect the full turn.
            result.add(
                firstMsg.copy(
                    content = combinedContent,
                    toolCalls = allToolCalls,
                    timestamp = lastMsg.timestamp
                )
            )
        }
        currentAssistantBatch.clear()
    }

    for (msg in nonToolMessages) {
        if (msg.role == MessageRole.ASSISTANT) {
            currentAssistantBatch.add(msg)
        } else {
            flushAssistantBatch()
            result.add(msg)
        }
    }
    flushAssistantBatch()

    return result
}
