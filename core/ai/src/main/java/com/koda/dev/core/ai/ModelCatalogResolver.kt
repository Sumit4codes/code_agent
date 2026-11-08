package com.koda.dev.core.ai

/**
 * Resolves context window token limits for AI models using a 4-tier resolution hierarchy:
 * 1. User manual override (from settings)
 * 2. Provider API metadata (reported in /models response)
 * 3. Static known catalog table
 * 4. Architectural model family pattern heuristics
 * 5. Safe fallback default (128,000 tokens)
 */
object ModelCatalogResolver {

    const val DEFAULT_CONTEXT_WINDOW = 128_000

    /**
     * Known static limits for common models (especially when the provider /models endpoint
     * does not return a context length, such as standard OpenAI, Anthropic, or NVIDIA NIM endpoints).
     */
    private val STATIC_CATALOG = mapOf(
        // Gemini family
        "gemini-2.5-pro" to 2_000_000,
        "gemini-2.5-flash" to 1_000_000,
        "gemini-2.0-pro" to 2_000_000,
        "gemini-2.0-flash" to 1_000_000,
        "gemini-1.5-pro" to 2_000_000,
        "gemini-1.5-flash" to 1_000_000,
        "gemini-flash" to 1_000_000,
        "gemini-pro" to 1_000_000,

        // Anthropic family
        "claude-3-7-sonnet" to 200_000,
        "claude-3-5-sonnet" to 200_000,
        "claude-3-5-haiku" to 200_000,
        "claude-3-opus" to 200_000,

        // OpenAI family
        "gpt-4o" to 128_000,
        "gpt-4o-mini" to 128_000,
        "o1" to 200_000,
        "o1-mini" to 128_000,
        "o1-preview" to 128_000,
        "o3" to 200_000,
        "o3-mini" to 200_000,
        "gpt-4-turbo" to 128_000,

        // NVIDIA NIM / Nemotron family
        "nvidia/nemotron-3.5-super-vl-120b-a12b" to 128_000,
        "nvidia/llama-3.1-nemotron-70b-instruct" to 128_000,
        "meta/llama-3.3-70b-instruct" to 128_000,
        "meta/llama-3.1-70b-instruct" to 128_000,
        "meta/llama-3.1-8b-instruct" to 128_000,
        "meta/llama-3.1-405b-instruct" to 128_000,

        // DeepSeek family
        "deepseek/deepseek-r1" to 128_000,
        "deepseek/deepseek-chat" to 128_000,
        "deepseek-r1" to 128_000,
        "deepseek-chat" to 128_000,
        "deepseek-reasoner" to 128_000,
        "deepseek-v3" to 128_000,

        // Qwen family
        "qwen/qwen-2.5-coder-32b-instruct" to 128_000,
        "qwen-2.5-coder-32b-instruct" to 128_000,
        "qwen-2.5-coder-7b-instruct" to 128_000,
        "qwen2.5-coder:7b" to 32_768,
        "qwen2.5-coder:14b" to 32_768,
        "qwen2.5-coder:32b" to 32_768,

        // Mistral family
        "mistral-large-latest" to 128_000,
        "mistral-small-latest" to 128_000,
        "codestral-latest" to 256_000,

        // Test models for context & compaction validation
        "test-1k" to 1_000
    )

    /**
     * Resolves the effective context limit using the 4-tier chain:
     * 1. User manual override (if set and > 0)
     * 2. Provider API metadata (e.g. reported in /models)
     * 3. Static catalog lookup
     * 4. Model family heuristic regex
     * 5. Fallback default (128,000)
     */
    fun resolveContextLimit(
        modelId: String,
        providerReportedWindow: Int? = null,
        userOverride: Int? = null
    ): Int {
        if (userOverride != null && userOverride > 0) {
            return userOverride
        }

        if (providerReportedWindow != null && providerReportedWindow > 0) {
            return providerReportedWindow
        }

        val cleanId = modelId.trim().lowercase()
        if (cleanId.isBlank()) return DEFAULT_CONTEXT_WINDOW

        // Exact match or suffix match in static catalog
        STATIC_CATALOG[cleanId]?.let { return it }
        for ((key, window) in STATIC_CATALOG) {
            if (cleanId.endsWith(key) || cleanId.contains(key)) {
                return window
            }
        }

        // Family pattern heuristics
        return when {
            cleanId.contains("gemini") -> when {
                cleanId.contains("pro") -> 2_000_000
                else -> 1_000_000
            }
            cleanId.contains("claude") -> 200_000
            cleanId.contains("o1") || cleanId.contains("o3") -> 200_000
            cleanId.contains("gpt-4") -> 128_000
            cleanId.contains("nemotron") -> 128_000
            cleanId.contains("llama-3.1") || cleanId.contains("llama-3.2") || cleanId.contains("llama-3.3") -> 128_000
            cleanId.contains("deepseek") -> 128_000
            cleanId.contains("codestral") -> 256_000
            cleanId.contains("qwen-2.5") -> 128_000
            cleanId.contains("qwen") || cleanId.contains("phi") || cleanId.contains("mistral") -> 32_768
            else -> DEFAULT_CONTEXT_WINDOW
        }
    }
}
