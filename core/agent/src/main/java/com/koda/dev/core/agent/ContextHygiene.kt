package com.koda.dev.core.agent

import com.koda.dev.core.ai.ChatMessage

/**
 * Manages working memory hygiene for the agent:
 * - Truncates older tool outputs in prompt memory to prevent context exhaustion
 * - Leaves the persistent database (Room SQLite) completely untouched so full logs are always visible to the user
 */
object ContextHygiene {

    const val RECENT_FULL_TOOLS = 6
    const val MAX_STALE_TOOL_CHARS = 4_000

    /**
     * Slices conversation history for model consumption: finds the last compaction summary
     * (if any) and retains that summary plus everything that occurred after it.
     * All messages prior to the compaction boundary are excluded from working prompt memory
     * because their contents are already represented in the summary.
     */
    fun sliceAfterCompaction(history: List<ChatMessage>): List<ChatMessage> {
        if (history.isEmpty()) return history

        val lastSummaryIdx = history.indexOfLast {
            (it.role == ChatMessage.Role.SYSTEM && it.content.startsWith(HistoryCompactor.COMPACTION_PREFIX)) ||
            (it.role == ChatMessage.Role.ASSISTANT && it.content.startsWith("### 📦 Context Compacted"))
        }
        if (lastSummaryIdx < 0) return history

        val summaryMsg = history[lastSummaryIdx]
        val modelSummaryMsg = if (summaryMsg.role == ChatMessage.Role.SYSTEM) {
            summaryMsg
        } else {
            val stripped = summaryMsg.content.removePrefix("### 📦 Context Compacted").trim()
            ChatMessage(
                role = ChatMessage.Role.SYSTEM,
                content = "${HistoryCompactor.COMPACTION_PREFIX}\n$stripped"
            )
        }

        return listOf(modelSummaryMsg) + history.subList(lastSummaryIdx + 1, history.size)
    }

    /**
     * Shrinks older tool execution results in the working prompt context sent to the LLM,
     * while leaving the full un-truncated output intact in the database for the user.
     *
     * The newest [RECENT_FULL_TOOLS] tool results are retained 100% verbatim.
     * Older tool results exceeding [MAX_STALE_TOOL_CHARS] retain head (2,000) and tail (2,000)
     * with an explicit truncation marker.
     */
    fun shrinkToolResults(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return messages

        // Find indices of all TOOL messages
        val toolIndices = mutableListOf<Int>()
        for ((idx, msg) in messages.withIndex()) {
            if (msg.role == ChatMessage.Role.TOOL) {
                toolIndices.add(idx)
            }
        }

        if (toolIndices.size <= RECENT_FULL_TOOLS) {
            return messages
        }

        // Indices of tools that must be kept 100% intact (the newest RECENT_FULL_TOOLS)
        val protectedIndices = toolIndices.takeLast(RECENT_FULL_TOOLS).toSet()

        return messages.mapIndexed { idx, msg ->
            if (msg.role == ChatMessage.Role.TOOL && !protectedIndices.contains(idx)) {
                truncateStaleToolOutput(msg)
            } else {
                msg
            }
        }
    }

    private fun truncateStaleToolOutput(msg: ChatMessage): ChatMessage {
        val text = msg.content
        if (text.length <= MAX_STALE_TOOL_CHARS) return msg

        val half = MAX_STALE_TOOL_CHARS / 2
        val head = text.substring(0, half).trimEnd()
        val tail = text.substring(text.length - half).trimStart()
        val omitted = text.length - (head.length + tail.length)

        val compactedContent = buildString {
            append(head)
            append("\n\n[... $omitted characters omitted from active prompt context; full logs preserved in session database ...]\n\n")
            append(tail)
        }

        return msg.copy(content = compactedContent)
    }
}
