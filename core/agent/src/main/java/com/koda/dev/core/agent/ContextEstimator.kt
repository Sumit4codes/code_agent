package com.koda.dev.core.agent

import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ModelCatalogResolver
import com.koda.dev.core.ai.ToolSpec
import kotlinx.serialization.Serializable

@Serializable
data class ContextUsageState(
    val usedTokens: Int = 0,
    val maxTokens: Int = ModelCatalogResolver.DEFAULT_CONTEXT_WINDOW,
    val messagesTokens: Int = 0,
    val toolsTokens: Int = 0,
    val systemTokens: Int = 0,
    val metaTokens: Int = 256,
    val lastInputTokens: Int? = null,
    val lastOutputTokens: Int? = null
) {
    val remainingTokens: Int get() = (maxTokens - usedTokens).coerceAtLeast(0)
    val fraction: Float get() = if (maxTokens > 0) (usedTokens.toFloat() / maxTokens.toFloat()).coerceIn(0f, 1f) else 0f
    val percentage: Float get() = fraction * 100f
}

object ContextEstimator {

    private const val META_TOKENS_RESERVE = 256

    /**
     * Estimates token consumption using the fast standard 4-characters-per-token heuristic.
     */
    fun estimate(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        tools: List<ToolSpec> = emptyList(),
        maxTokens: Int = ModelCatalogResolver.DEFAULT_CONTEXT_WINDOW,
        lastInputTokens: Int? = null,
        lastOutputTokens: Int? = null
    ): ContextUsageState {
        var msgChars = 0
        for (m in messages) {
            msgChars += m.content.length
            for (tc in m.toolCalls) {
                msgChars += tc.arguments.length + tc.name.length
            }
        }
        val messagesTokens = (msgChars / 4).coerceAtLeast(0)

        var toolChars = 0
        for (t in tools) {
            toolChars += t.name.length + t.description.length + (t.parameters.properties.size * 50)
        }
        val toolsTokens = (toolChars / 4).coerceAtLeast(0)

        val systemTokens = ((systemPrompt?.length ?: 0) / 4).coerceAtLeast(0)

        val totalUsed = messagesTokens + toolsTokens + systemTokens + META_TOKENS_RESERVE

        return ContextUsageState(
            usedTokens = totalUsed,
            maxTokens = maxTokens,
            messagesTokens = messagesTokens,
            toolsTokens = toolsTokens,
            systemTokens = systemTokens,
            metaTokens = META_TOKENS_RESERVE,
            lastInputTokens = lastInputTokens,
            lastOutputTokens = lastOutputTokens
        )
    }

    /**
     * Formats token count with unit suffixes (e.g. 1.2k, 128k, 1.5M).
     */
    fun formatTokenCount(tokens: Int): String {
        val t = tokens.coerceAtLeast(0)
        return when {
            t >= 1_000_000 -> {
                val d = t / 1_000_000.0
                if (d >= 10.0) "%.0fM".format(d) else "%.1fM".format(d)
            }
            t >= 1_000 -> {
                val d = t / 1_000.0
                if (d >= 10.0) "%.0fk".format(d) else "%.1fk".format(d)
            }
            else -> t.toString()
        }
    }
}
