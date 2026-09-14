package com.codeagent.core.ai

import com.codeagent.core.ai.ChatMessage.Role
import com.codeagent.core.ai.ChatStreamEvent.*
import com.codeagent.core.model.ToolNames
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

    private fun buildUrl(path: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (trimmed.endsWith("/v1")) {
            "$trimmed$cleanPath"
        } else {
            "$trimmed/v1$cleanPath"
        }
    }

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildUrl("/models"))
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = Json.parseToJsonElement(body).jsonObject
                json["data"]?.jsonArray?.map { element ->
                    val obj = element.jsonObject
                    ModelInfo(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        name = obj["id"]?.jsonPrimitive?.content ?: ""
                    )
                } ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
        val jsonBody = buildOpenAiRequest(request)
        val httpRequest = Request.Builder()
            .url(buildUrl("/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

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
                            json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                                ?: json["message"]?.jsonPrimitive?.contentOrNull
                                ?: errorBody
                        } catch (_: Exception) {
                            errorBody
                        }
                    }
                    response != null -> "HTTP error ${response.code}: ${response.message.ifBlank { "Request failed" }}"
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
            Json.encodeToJsonElement(request.tools.map { tool ->
                buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", Json.encodeToJsonElement(tool.parameters))
                    })
                }
            })
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

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
