package com.codeagent.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PopularProvider(
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val fallbackModels: List<String>,
    val apiKeyPlaceholder: String,
    val apiKeyHelpUrl: String? = null,
    val description: String,
    val isCustom: Boolean = false
)

object PopularProviders {
    val OPENAI = PopularProvider(
        id = "openai",
        name = "OpenAI",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        fallbackModels = listOf("gpt-4o", "gpt-4o-mini", "o3-mini", "o1", "gpt-4-turbo"),
        apiKeyPlaceholder = "sk-proj-...",
        apiKeyHelpUrl = "https://platform.openai.com/api-keys",
        description = "Official OpenAI GPT-4o, GPT-4o-mini, and o-series reasoning models"
    )

    val ANTHROPIC = PopularProvider(
        id = "anthropic",
        name = "Anthropic",
        providerType = ProviderType.ANTHROPIC,
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-7-sonnet-20250219",
        fallbackModels = listOf(
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022"
        ),
        apiKeyPlaceholder = "sk-ant-api03-...",
        apiKeyHelpUrl = "https://console.anthropic.com/settings/keys",
        description = "Direct Anthropic Claude 3.7 Sonnet & 3.5 Sonnet Messages API"
    )

    val OPENROUTER = PopularProvider(
        id = "openrouter",
        name = "OpenRouter",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "anthropic/claude-3.7-sonnet",
        fallbackModels = listOf(
            "anthropic/claude-3.7-sonnet",
            "deepseek/deepseek-r1",
            "deepseek/deepseek-chat",
            "meta-llama/llama-3.3-70b-instruct",
            "google/gemini-2.5-flash",
            "qwen/qwen-2.5-coder-32b-instruct"
        ),
        apiKeyPlaceholder = "sk-or-v1-...",
        apiKeyHelpUrl = "https://openrouter.ai/keys",
        description = "Universal API for 200+ models from Claude, OpenAI, Meta, and DeepSeek"
    )

    val DEEPSEEK = PopularProvider(
        id = "deepseek",
        name = "DeepSeek",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        fallbackModels = listOf("deepseek-chat", "deepseek-reasoner"),
        apiKeyPlaceholder = "sk-...",
        apiKeyHelpUrl = "https://platform.deepseek.com/api_keys",
        description = "DeepSeek-V3 coding and DeepSeek-R1 reasoning models"
    )

    val GROQ = PopularProvider(
        id = "groq",
        name = "Groq",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        fallbackModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen-2.5-coder-32b"
        ),
        apiKeyPlaceholder = "gsk_...",
        apiKeyHelpUrl = "https://console.groq.com/keys",
        description = "Ultra high-speed LPU inference for Llama 3.3 and Qwen Coder"
    )

    val GEMINI = PopularProvider(
        id = "gemini",
        name = "Google Gemini",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModel = "gemini-2.5-flash",
        fallbackModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
        apiKeyPlaceholder = "AIzaSy...",
        apiKeyHelpUrl = "https://aistudio.google.com/apikey",
        description = "Google AI Studio OpenAI endpoint for Gemini 2.5 Flash & Pro"
    )

    val OLLAMA = PopularProvider(
        id = "ollama",
        name = "Ollama (Local)",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "http://localhost:11434/v1",
        defaultModel = "qwen2.5-coder",
        fallbackModels = listOf("qwen2.5-coder", "llama3.2", "deepseek-r1", "codellama"),
        apiKeyPlaceholder = "Optional (leave blank for local)",
        apiKeyHelpUrl = "https://ollama.com",
        description = "Run open-weight models privately on local device or LAN"
    )

    val CUSTOM = PopularProvider(
        id = "custom",
        name = "Custom Provider",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        defaultBaseUrl = "http://localhost:8000/v1",
        defaultModel = "",
        fallbackModels = emptyList(),
        apiKeyPlaceholder = "API Key (if required)",
        apiKeyHelpUrl = null,
        description = "Connect to any OpenAI or Anthropic compatible API server",
        isCustom = true
    )

    val allPopular: List<PopularProvider> = listOf(
        OPENROUTER,
        OPENAI,
        ANTHROPIC,
        DEEPSEEK,
        GROQ,
        GEMINI,
        OLLAMA,
        CUSTOM
    )

    fun findById(id: String): PopularProvider? =
        allPopular.firstOrNull { it.id == id }
}
