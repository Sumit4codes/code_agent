package com.koda.dev.core.ai

import com.koda.dev.core.ai.ChatMessage.Role
import com.koda.dev.core.ai.ChatStreamEvent.*
import com.koda.dev.core.model.ToolNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSource.Factory
import okhttp3.sse.EventSourceListener
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val sseFactory: Factory
) : AiProvider {

    private fun isKeyless(): Boolean =
        apiKey.isBlank() ||
        apiKey.equals("keyless", ignoreCase = true) ||
        apiKey.equals("koda-community-free", ignoreCase = true) ||
        baseUrl.contains("kilo.ai", ignoreCase = true)

    private fun buildUrl(path: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (
            trimmed.endsWith("/v1") ||
            trimmed.endsWith("/openai") ||
            trimmed.endsWith("/v1beta/openai") ||
            trimmed.endsWith("/gateway") ||
            trimmed.contains("/v1/") ||
            trimmed.contains("/openai/") ||
            trimmed.contains("/gateway/")
        ) {
            "$trimmed$cleanPath"
        } else {
            "$trimmed/v1$cleanPath"
        }
    }

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(buildUrl("/models"))
            .header("User-Agent", CLIENT_USER_AGENT)
            .header("X-Title", CLIENT_TITLE)
            .header("HTTP-Referer", CLIENT_REFERER)
            .get()

        if (!isKeyless()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = Json.parseToJsonElement(body).jsonObject
                val isKilo = baseUrl.contains("kilo.ai", ignoreCase = true)
                json["data"]?.jsonArray?.mapNotNull { element ->
                    val obj = element.jsonObject
                    val isFree = obj["isFree"]?.jsonPrimitive?.booleanOrNull
                    if (isKilo && isFree == false) {
                        return@mapNotNull null
                    }
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val window = obj["context_window"]
                    val ctx = sequenceOf(
                        obj["context_length"],
                        obj["max_context_tokens"],
                        obj["max_tokens"],
                        window,
                        window?.let { (it as? JsonObject)?.get("tokens") }
                    ).mapNotNull { it?.jsonPrimitive?.intOrNull }.firstOrNull()

                    val resolvedContext = ModelCatalogResolver.resolveContextLimit(
                        modelId = id,
                        providerReportedWindow = ctx
                    )

                    ModelInfo(
                        id = id,
                        name = id,
                        contextWindow = resolvedContext
                    )
                } ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
        val jsonBody = buildOpenAiRequest(request)
        val reqBuilder = Request.Builder()
            .url(buildUrl("/chat/completions"))
            .header("Content-Type", "application/json")
            .header("User-Agent", CLIENT_USER_AGENT)
            .header("X-Title", CLIENT_TITLE)
            .header("HTTP-Referer", CLIENT_REFERER)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

        if (!isKeyless()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }
        val httpRequest = reqBuilder.build()

        val eventSource = sseFactory.newEventSource(httpRequest, object : EventSourceListener() {
            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(Done)
                    close()
                    return
                }
                try {
                    val json = Json.parseToJsonElement(data).jsonObject
                    val choices = json["choices"]?.jsonArray ?: return
                    for (choice in choices) {
                        val delta = choice.jsonObject["delta"]?.jsonObject ?: continue
                        val content = delta["content"]?.jsonPrimitive?.contentOrNull

                        if (content != null) {
                            trySend(TextDelta(content))
                        }

                        val toolCalls = delta["tool_calls"]?.jsonArray
                        if (toolCalls != null) {
                            for (tc in toolCalls) {
                                val tcObj = tc.jsonObject
                                val tcId = tcObj["id"]?.jsonPrimitive?.contentOrNull
                                val tcFunction = tcObj["function"]?.jsonObject
                                val tcName = tcFunction?.get("name")?.jsonPrimitive?.contentOrNull
                                val tcArgs = tcFunction?.get("arguments")?.jsonPrimitive?.contentOrNull

                                if (tcId != null && tcName != null) {
                                    val cleanName = ToolNames.normalize(tcName)
                                    trySend(ToolCallStart(tcId, cleanName))
                                }
                                if (tcArgs != null) {
                                    trySend(ToolCallArgsDelta(tcId ?: "", tcArgs))
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }

            override fun onFailure(source: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val errorBody = try {
                    response?.body?.string()
                } catch (_: Exception) {
                    null
                } finally {
                    try { response?.close() } catch (_: Exception) {}
                }

                val errorMsg = when {
                    !errorBody.isNullOrBlank() -> {
                        try {
                            val json = Json.parseToJsonElement(errorBody).jsonObject
                            val detail = json["detail"]?.jsonPrimitive?.contentOrNull
                            val title = json["title"]?.jsonPrimitive?.contentOrNull
                            val parsed = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                                ?: json["message"]?.jsonPrimitive?.contentOrNull
                                ?: detail
                                ?: title
                                ?: errorBody

                            if (detail != null && detail.contains("Not found for account", ignoreCase = true)) {
                                "NVIDIA NIM (404): $detail\n\nCause: Public API Endpoints are not active for your personal account on build.nvidia.com or this model is restricted. Please check your account on build.nvidia.com or try another model (e.g. meta/llama-3.3-70b-instruct)."
                            } else if (response?.code == 429 || parsed.contains("limit reached", ignoreCase = true) || parsed.contains("rate limit", ignoreCase = true) || parsed.contains("PROMOTION_MODEL_LIMIT_REACHED", ignoreCase = true)) {
                                if (baseUrl.contains("kilo.ai", ignoreCase = true) || isKeyless()) {
                                    "Koda Community Free Quota Reached (HTTP 429): $parsed\n\nHourly community quota has been reached for your IP. Allowance automatically replenishes over an hourly window, or configure your own provider in Settings."
                                } else {
                                    "Rate Limit Reached (HTTP 429): $parsed"
                                }
                            } else {
                                parsed
                            }
                        } catch (_: Exception) {
                            if (response?.code == 429) {
                                if (baseUrl.contains("kilo.ai", ignoreCase = true) || isKeyless()) {
                                    "Koda Community Free Quota Reached (HTTP 429): $errorBody\n\nHourly community quota has been reached for your IP. Allowance automatically replenishes over an hourly window, or configure your own provider in Settings."
                                } else {
                                    "Rate Limit Reached (HTTP 429): $errorBody"
                                }
                            } else {
                                errorBody
                            }
                        }
                    }
                    response != null -> {
                        if (response.code == 429 && (baseUrl.contains("kilo.ai", ignoreCase = true) || isKeyless())) {
                            "Koda Community Free Quota Reached (HTTP 429): Hourly community gateway allowance reached for your IP. It automatically replenishes over an hourly window, or you can switch to your own API key in Settings."
                        } else {
                            "HTTP error ${response.code}: ${response.message.ifBlank { "Request failed" }}"
                        }
                    }
                    t != null -> t.message ?: "Connection error"
                    else -> "Unknown SSE stream error"
                }
                trySend(ChatStreamEvent.Error(errorMsg, t))
                close()
            }

            override fun onClosed(source: EventSource) {
                trySend(Done)
                close()
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }

    private fun buildOpenAiRequest(request: ChatRequest): String {
        val messages = request.messages.map { msg ->
            buildJsonObject {
                put("role", when (msg.role) {
                    Role.SYSTEM -> "system"
                    Role.USER -> "user"
                    Role.ASSISTANT -> "assistant"
                    Role.TOOL -> "tool"
                })
                put("content", msg.content)
                if (msg.toolCalls.isNotEmpty()) {
                    put("tool_calls", Json.encodeToJsonElement(msg.toolCalls.map { tc ->
                        buildJsonObject {
                            put("id", tc.id)
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tc.name)
                                put("arguments", tc.arguments)
                            })
                        }
                    }))
                }
                if (msg.toolCallId != null) {
                    put("tool_call_id", msg.toolCallId)
                }
            }
        }

        val toolsJson = if (request.tools.isNotEmpty()) {
            buildJsonArray {
                request.tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", buildJsonObject {
                                put("type", tool.parameters.type.ifBlank { "object" })
                                put("properties", buildJsonObject {
                                    tool.parameters.properties.forEach { (propName, propDef) ->
                                        put(propName, buildJsonObject {
                                            put("type", propDef.type)
                                            put("description", propDef.description)
                                        })
                                    }
                                })
                                put("required", buildJsonArray {
                                    tool.parameters.required.forEach { add(JsonPrimitive(it)) }
                                })
                            })
                        })
                    })
                }
            }
        } else null

        return buildJsonObject {
            put("model", request.model)
            put("messages", Json.encodeToJsonElement(messages))
            put("temperature", request.temperature.toDouble())
            put("max_tokens", request.maxTokens)
            put("stream", true)
            if (toolsJson != null) put("tools", toolsJson)
        }.toString()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val CLIENT_USER_AGENT = "KodaDev/1.0 (Android)"
        const val CLIENT_TITLE = "Koda Dev"
        const val CLIENT_REFERER = "https://github.com/Sumit4codes/code_agent"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
