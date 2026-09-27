package com.codeagent.core.ai

import com.codeagent.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

interface ModelFetcher {
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        providerType: ProviderType
    ): Result<List<String>>
}

@Singleton
class DefaultModelFetcher @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) : ModelFetcher {

    override suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        providerType: ProviderType
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid URL: Must start with http:// or https://")
            )
        }

        val modelsUrl = resolveModelsUrl(cleanUrl, providerType)
        val reqBuilder = Request.Builder().url(modelsUrl).get()

        if (providerType == ProviderType.ANTHROPIC) {
            if (apiKey.isNotBlank()) {
                reqBuilder.header("x-api-key", apiKey.trim())
                reqBuilder.header("anthropic-version", "2023-06-01")
            }
        } else {
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
            }
            if (cleanUrl.contains("openrouter")) {
                reqBuilder.header("HTTP-Referer", "https://codeagent.local")
                reqBuilder.header("X-Title", "CodeAgent")
            }
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = try {
                        response.body?.string()?.take(200)
                    } catch (_: Exception) {
                        null
                    }
                    val message = when (response.code) {
                        401 -> "Authentication failed: Invalid API key (HTTP 401)"
                        403 -> "Access forbidden: Check API key permissions (HTTP 403)"
                        404 -> "Models endpoint not found at $modelsUrl (HTTP 404)"
                        else -> "Server returned HTTP ${response.code}: ${response.message}"
                    }
                    val detail = if (!errBody.isNullOrBlank()) ": $errBody" else ""
                    return@withContext Result.failure(Exception("$message$detail"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response received from server"))
                val models = parseModels(body)
                if (models.isEmpty()) {
                    return@withContext Result.failure(Exception("No models found in response from $modelsUrl"))
                }
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(
                Exception("Connection failed: ${e.localizedMessage ?: e.message ?: "Network error"}")
            )
        }
    }

    private fun resolveModelsUrl(baseUrl: String, providerType: ProviderType): String {
        return if (providerType == ProviderType.ANTHROPIC) {
            if (baseUrl.endsWith("/models")) baseUrl
            else if (baseUrl.endsWith("/v1")) "$baseUrl/models"
            else "$baseUrl/v1/models"
        } else {
            if (baseUrl.endsWith("/models")) baseUrl
            else if (
                baseUrl.endsWith("/v1") ||
                baseUrl.endsWith("/openai") ||
                baseUrl.endsWith("/v1beta/openai") ||
                baseUrl.contains("/v1/") ||
                baseUrl.contains("/openai/")
            ) "$baseUrl/models"
            else "$baseUrl/v1/models"
        }
    }

    private fun parseModels(responseBody: String): List<String> {
        val root = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }

        val rawList = mutableListOf<String>()

        // 1. Check "data" array (OpenAI, OpenRouter, Anthropic, DeepSeek, Groq, Gemini)
        root["data"]?.jsonArray?.forEach { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: obj["display_name"]?.jsonPrimitive?.contentOrNull
            if (!id.isNullOrBlank()) rawList.add(id)
        }

        // 2. Check "models" array (Ollama, etc.)
        if (rawList.isEmpty()) {
            root["models"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                if (!id.isNullOrBlank()) rawList.add(id)
            }
        }

        // Filter non-chat models if there are many models (e.g. OpenAI returns 80+ including whisper, tts, embeddings)
        val nonChatPrefixes = listOf(
            "tts-",
            "whisper-",
            "dall-e-",
            "text-embedding-",
            "text-moderation-",
            "babbage-",
            "davinci-"
        )
        val filtered = if (rawList.size > 12) {
            rawList.filterNot { model -> nonChatPrefixes.any { model.startsWith(it) } }
        } else rawList

        return filtered.distinct().sortedWith { a, b ->
            val aScore = getPriorityScore(a)
            val bScore = getPriorityScore(b)
            if (aScore != bScore) bScore.compareTo(aScore)
            else a.compareTo(b, ignoreCase = true)
        }
    }

    private fun getPriorityScore(model: String): Int {
        val lower = model.lowercase()
        return when {
            lower.contains("claude-3-7") -> 100
            lower.contains("claude-3-5-sonnet") -> 95
            lower.contains("gpt-4o") && !lower.contains("mini") -> 90
            lower.contains("o3-mini") || lower.contains("o1") -> 88
            lower.contains("gpt-4o-mini") -> 85
            lower.contains("deepseek-r1") -> 82
            lower.contains("deepseek-chat") -> 80
            lower.contains("gemini-2.5") || lower.contains("gemini-2.0") -> 78
            lower.contains("llama-3.3") -> 75
            lower.contains("qwen2.5-coder") -> 70
            lower.contains("claude") -> 65
            lower.contains("gpt") -> 60
            lower.contains("deepseek") -> 55
            lower.contains("coder") -> 50
            lower.contains("chat") || lower.contains("instruct") -> 40
            else -> 10
        }
    }
}
