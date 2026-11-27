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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Anthropic Messages API (`/v1/messages`) streaming provider with tool calling,
 * prompt-cache-aware token reporting, and strict role-alternation message normalization.
 */
class AnthropicAiProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OpenAiCompatibleProvider.defaultClient(),
    private val sseFactory: Factory
) : AiProvider {

    private data class ToolBlockState(
        val id: String,
        val name: String,
        val initialInput: JsonObject?,
        val argsBuffer: StringBuilder = StringBuilder()
    )

    private fun buildUrl(path: String): String {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val trimmed = baseUrl.trim().trimEnd('/')
            .removeSuffix("/messages")
            .removeSuffix("/models")
            .trimEnd('/')
        return if (trimmed.endsWith("/v1") || trimmed.contains("/v1/")) {
            "$trimmed$cleanPath"
        } else {
            "$trimmed/v1$cleanPath"
        }
    }

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(buildUrl("/models"))
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("User-Agent", OpenAiCompatibleProvider.CLIENT_USER_AGENT)
            .get()

        if (apiKey.isNotBlank()) {
            reqBuilder.header("x-api-key", apiKey.trim())
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = jsonParser.parseToJsonElement(body).jsonObject
                root["data"]?.jsonArray?.mapNotNull { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val displayName = obj["display_name"]?.jsonPrimitive?.contentOrNull?.ifBlank { id } ?: id
                    val ctx = sequenceOf(
                        obj["context_length"],
                        obj["max_context_tokens"],
                        obj["max_tokens"]
                    ).mapNotNull { it?.jsonPrimitive?.intOrNull }.firstOrNull()

                    ModelInfo(
                        id = id,
                        name = displayName,
                        contextWindow = ModelCatalogResolver.resolveContextLimit(
                            modelId = id,
                            providerReportedWindow = ctx
                        )
                    )
                } ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
        val jsonBody = buildAnthropicRequest(request)
        val reqBuilder = Request.Builder()
            .url(buildUrl("/messages"))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("User-Agent", OpenAiCompatibleProvider.CLIENT_USER_AGENT)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

        if (apiKey.isNotBlank()) {
            reqBuilder.header("x-api-key", apiKey.trim())
        }

        val activeToolBlocks = mutableMapOf<Int, ToolBlockState>()
        var inputTokens = 0
        var outputTokens = 0
        var usageEmitted = false
        val terminalEventSent = AtomicBoolean(false)

        fun flushOpenToolBlocks() {
            if (activeToolBlocks.isEmpty()) return
            activeToolBlocks.keys.sorted().forEach { idx ->
                val block = activeToolBlocks.remove(idx) ?: return@forEach
                trySend(ToolCallComplete(ToolCall(id = block.id, name = block.name, arguments = resolveToolArgs(block))))
            }
        }

        fun emitUsageOnce() {
            if (!usageEmitted && (inputTokens > 0 || outputTokens > 0)) {
                usageEmitted = true
                trySend(Usage(inputTokens, outputTokens))
            }
        }

        val eventSource = sseFactory.newEventSource(reqBuilder.build(), object : EventSourceListener() {
            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                if (data == "[DONE]") {
                    if (terminalEventSent.compareAndSet(false, true)) {
                        flushOpenToolBlocks()
                        emitUsageOnce()
                        trySend(Done)
                        close()
                    }
                    return
                }

                try {
                    val json = jsonParser.parseToJsonElement(data).jsonObject
                    val eventType = json["type"]?.jsonPrimitive?.contentOrNull ?: type ?: return

                    when (eventType) {
                        "message_start" -> {
                            val usage = json["message"]?.jsonObject?.get("usage")?.jsonObject
                            if (usage != null) {
                                val baseIn = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                                val cacheCreate = usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                                val cacheRead = usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                                inputTokens = baseIn + cacheCreate + cacheRead
                                usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let { out ->
                                    if (out > outputTokens) outputTokens = out
                                }
                            }
                        }

                        "content_block_start" -> {
                            val index = json["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val block = json["content_block"]?.jsonObject ?: return
                            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                                "text" -> {
                                    val initialText = block["text"]?.jsonPrimitive?.contentOrNull
                                    if (!initialText.isNullOrEmpty()) {
                                        trySend(TextDelta(initialText))
                                    }
                                }
                                "tool_use" -> {
                                    val tcId = block["id"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
                                        ?: "toolu_$index"
                                    val rawName = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                    val cleanName = ToolNames.normalize(rawName)
                                    val initialInput = block["input"] as? JsonObject
                                    activeToolBlocks[index] = ToolBlockState(
                                        id = tcId,
                                        name = cleanName,
                                        initialInput = initialInput
                                    )
                                    trySend(ToolCallStart(tcId, cleanName))
                                }
                            }
                        }

                        "content_block_delta" -> {
                            val index = json["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val delta = json["delta"]?.jsonObject ?: return
                            when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                                "text_delta" -> {
                                    val text = delta["text"]?.jsonPrimitive?.contentOrNull
                                    if (!text.isNullOrEmpty()) {
                                        trySend(TextDelta(text))
                                    }
                                }
                                "input_json_delta" -> {
                                    val partialJson = delta["partial_json"]?.jsonPrimitive?.contentOrNull
                                    if (!partialJson.isNullOrEmpty()) {
                                        val block = activeToolBlocks[index]
                                        if (block != null) {
                                            block.argsBuffer.append(partialJson)
                                            trySend(ToolCallArgsDelta(block.id, partialJson))
                                        } else {
                                            trySend(ToolCallArgsDelta("", partialJson))
                                        }
                                    }
                                }
                            }
                        }

                        "content_block_stop" -> {
                            val index = json["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val block = activeToolBlocks.remove(index)
                            if (block != null) {
                                trySend(
                                    ToolCallComplete(
                                        ToolCall(
                                            id = block.id,
                                            name = block.name,
                                            arguments = resolveToolArgs(block)
                                        )
                                    )
                                )
                            }
                        }

                        "message_delta" -> {
                            val usage = json["usage"]?.jsonObject
                            usage?.get("output_tokens")?.jsonPrimitive?.intOrNull?.let { out ->
                                outputTokens = out
                            }
                            usage?.get("input_tokens")?.jsonPrimitive?.intOrNull?.let { inTokens ->
                                if (inTokens > 0) inputTokens = inTokens
                            }
                        }

                        "message_stop" -> {
                            if (terminalEventSent.compareAndSet(false, true)) {
                                flushOpenToolBlocks()
                                emitUsageOnce()
                                trySend(Done)
                                close()
                            }
                        }

                        "error" -> {
                            if (terminalEventSent.compareAndSet(false, true)) {
                                val errObj = json["error"]?.jsonObject
                                val errType = errObj?.get("type")?.jsonPrimitive?.contentOrNull
                                val errMsg = errObj?.get("message")?.jsonPrimitive?.contentOrNull
                                    ?: json["message"]?.jsonPrimitive?.contentOrNull
                                    ?: data
                                val formatted = if (!errType.isNullOrBlank()) "$errType: $errMsg" else errMsg
                                trySend(Error(formatted))
                                close()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore malformed or non-JSON keepalive frames
                }
            }

            override fun onFailure(source: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val errorBody = try {
                    response?.body?.string()
                } catch (_: Exception) {
                    null
                } finally {
                    try {
                        response?.close()
                    } catch (_: Exception) {
                    }
                }

                if (!terminalEventSent.compareAndSet(false, true)) return

                val errorMsg = formatHttpFailure(response?.code, response?.message, errorBody, t)
                trySend(Error(errorMsg, t))
                close()
            }

            override fun onClosed(source: EventSource) {
                if (terminalEventSent.compareAndSet(false, true)) {
                    flushOpenToolBlocks()
                    emitUsageOnce()
                    trySend(Done)
                    close()
                }
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }

    private fun resolveToolArgs(block: ToolBlockState): String {
        return when {
            block.argsBuffer.isNotEmpty() -> block.argsBuffer.toString()
            block.initialInput != null && block.initialInput.isNotEmpty() -> block.initialInput.toString()
            else -> "{}"
        }
    }

    internal fun buildAnthropicRequest(request: ChatRequest): String {
        val systemText = request.messages
            .filter { it.role == Role.SYSTEM && it.content.isNotBlank() }
            .joinToString("\n\n") { it.content.trim() }

        val turns = mutableListOf<Pair<String, MutableList<JsonObject>>>()
        var pendingToolUseIds = emptySet<String>()

        fun appendTurn(role: String, blocks: List<JsonObject>) {
            if (blocks.isEmpty()) return
            val last = turns.lastOrNull()
            if (last != null && last.first == role) {
                last.second.addAll(blocks)
            } else {
                turns.add(role to blocks.toMutableList())
            }
        }

        for (msg in request.messages) {
            when (msg.role) {
                Role.SYSTEM -> Unit
                Role.USER -> {
                    pendingToolUseIds = emptySet()
                    appendTurn(
                        "user",
                        listOf(
                            buildJsonObject {
                                put("type", "text")
                                put("text", msg.content.ifEmpty { " " })
                            }
                        )
                    )
                }
                Role.TOOL -> {
                    val toolCallId = msg.toolCallId?.trim().orEmpty()
                    val isMatchedToLastAssistant = toolCallId.isNotEmpty() && pendingToolUseIds.contains(toolCallId)
                    val block = if (isMatchedToLastAssistant) {
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", toolCallId)
                            put("content", msg.content)
                        }
                    } else {
                        // Orphaned tool result (e.g., after compaction) must be plain text so Anthropic stays valid
                        buildJsonObject {
                            put("type", "text")
                            put("text", "[Tool output]: ${msg.content}".trimEnd())
                        }
                    }
                    appendTurn("user", listOf(block))
                }
                Role.ASSISTANT -> {
                    val blocks = mutableListOf<JsonObject>()
                    if (msg.content.isNotBlank()) {
                        blocks.add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", msg.content)
                            }
                        )
                    }
                    val emittedIds = mutableSetOf<String>()
                    msg.toolCalls.forEach { tc ->
                        val tcId = tc.id.ifBlank { "toolu_${emittedIds.size}" }
                        emittedIds.add(tcId)
                        blocks.add(
                            buildJsonObject {
                                put("type", "tool_use")
                                put("id", tcId)
                                put("name", tc.name)
                                put("input", parseToolInputObject(tc.arguments))
                            }
                        )
                    }
                    if (blocks.isEmpty()) {
                        blocks.add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", " ")
                            }
                        )
                    }
                    appendTurn("assistant", blocks)
                    pendingToolUseIds = emittedIds
                }
            }
        }

        if (turns.isEmpty() || turns.first().first != "user") {
            turns.add(
                0,
                "user" to mutableListOf(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "Hello")
                    }
                )
            )
        }

        val messagesJson = buildJsonArray {
            turns.forEach { (role, blocks) ->
                add(
                    buildJsonObject {
                        put("role", role)
                        put("content", JsonArray(blocks))
                    }
                )
            }
        }

        val toolsJson = if (request.tools.isNotEmpty()) {
            buildJsonArray {
                request.tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put(
                                "input_schema",
                                buildJsonObject {
                                    put("type", tool.parameters.type.ifBlank { "object" })
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            tool.parameters.properties.forEach { (propName, propDef) ->
                                                put(
                                                    propName,
                                                    buildJsonObject {
                                                        put("type", propDef.type)
                                                        put("description", propDef.description)
                                                    }
                                                )
                                            }
                                        }
                                    )
                                    put(
                                        "required",
                                        buildJsonArray {
                                            tool.parameters.required.forEach { add(JsonPrimitive(it)) }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            }
        } else {
            null
        }

        return buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens.coerceAtLeast(1))
            put("temperature", request.temperature.toDouble().coerceIn(0.0, 1.0))
            put("stream", true)
            if (systemText.isNotBlank()) {
                put("system", systemText)
            }
            put("messages", messagesJson)
            if (toolsJson != null) {
                put("tools", toolsJson)
            }
        }.toString()
    }

    private fun parseToolInputObject(arguments: String): JsonObject {
        val trimmed = arguments.trim()
        if (trimmed.isEmpty()) return buildJsonObject {}
        return try {
            val element = jsonParser.parseToJsonElement(trimmed)
            element as? JsonObject ?: buildJsonObject { put("raw", arguments) }
        } catch (_: Exception) {
            buildJsonObject { put("raw", arguments) }
        }
    }

    private fun formatHttpFailure(
        code: Int?,
        httpMessage: String?,
        errorBody: String?,
        t: Throwable?
    ): String {
        if (!errorBody.isNullOrBlank()) {
            val parsed = try {
                val root = jsonParser.parseToJsonElement(errorBody).jsonObject
                val errObj = root["error"]?.jsonObject
                errObj?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: root["message"]?.jsonPrimitive?.contentOrNull
                    ?: errorBody
            } catch (_: Exception) {
                errorBody
            }

            return when (code) {
                401 -> "Anthropic Authentication Failed (HTTP 401): $parsed"
                403 -> "Anthropic Access Forbidden (HTTP 403): $parsed"
                429 -> "Anthropic Rate Limit Reached (HTTP 429): $parsed"
                529 -> "Anthropic API Overloaded (HTTP 529): $parsed"
                else -> parsed
            }
        }
        return when {
            code != null -> "HTTP error $code: ${httpMessage?.ifBlank { null } ?: "Request failed"}"
            t != null -> t.message ?: "Connection error"
            else -> "Unknown Anthropic SSE stream error"
        }
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
