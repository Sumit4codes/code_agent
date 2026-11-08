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

            // Join non-blank content in chronological order, deduplicating consecutive identical thoughts
            val nonBlankContents = currentAssistantBatch
                .map { it.content.trim() }
                .filter { it.isNotBlank() }

            val distinctContents = mutableListOf<String>()
            for (c in nonBlankContents) {
                if (distinctContents.isEmpty() || distinctContents.last() != c) {
                    distinctContents.add(c)
                }
            }

            // If there are many intermediate progress updates, keep the initial intent and latest progress/answer
            val compactedContents = if (distinctContents.size > 3) {
                listOf(
                    distinctContents.first(),
                    "... (${distinctContents.size - 2} intermediate progress updates) ...",
                    distinctContents.last()
                )
            } else {
                distinctContents
            }

            val combinedContent = compactedContents.joinToString("\n\n")

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
