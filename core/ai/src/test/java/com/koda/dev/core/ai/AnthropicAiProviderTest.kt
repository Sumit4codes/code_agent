package com.koda.dev.core.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.junit.Assert.*
import org.junit.Test

class AnthropicAiProviderTest {

    private val dummySseFactory = object : EventSource.Factory {
        override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
            return object : EventSource {
                override fun cancel() = Unit
                override fun request(): Request = request
            }
        }
    }

    @Test
    fun `listModels sends Anthropic headers and resolves 200k Claude context window`() = runTest {
        var capturedApiKey: String? = null
        var capturedVersion: String? = null
        var capturedAuth: String? = null
        var capturedUrl: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                capturedApiKey = req.header("x-api-key")
                capturedVersion = req.header("anthropic-version")
                capturedAuth = req.header("Authorization")

                val body = """
                    {
                        "data": [
                            {"id": "claude-3-7-sonnet-20250219", "display_name": "Claude 3.7 Sonnet"},
                            {"id": "claude-3-5-haiku-20241022", "display_name": "Claude 3.5 Haiku"}
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val provider = AnthropicAiProvider(
            baseUrl = "https://api.anthropic.com/v1",
            apiKey = "  sk-ant-secret  ",
            client = client,
            sseFactory = dummySseFactory
        )

        val models = provider.listModels()

        assertEquals("https://api.anthropic.com/v1/models", capturedUrl)
        assertEquals("sk-ant-secret", capturedApiKey)
        assertEquals("2023-06-01", capturedVersion)
        assertNull(capturedAuth)
        assertEquals(2, models.size)
        assertEquals("Claude 3.7 Sonnet", models[0].name)
        assertEquals(200_000, models[0].contextWindow)
    }

    @Test
    fun `buildAnthropicRequest formats system prompt, input_schema tools, merged tool results, and temperature clamp`() {
        val provider = AnthropicAiProvider(
            baseUrl = "https://api.anthropic.com/v1",
            apiKey = "sk-ant-test",
            client = OkHttpClient(),
            sseFactory = dummySseFactory
        )

        val request = ChatRequest(
            messages = listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, "Be concise."),
                ChatMessage(ChatMessage.Role.SYSTEM, "Prefer Kotlin."),
                ChatMessage(ChatMessage.Role.USER, "Check two files"),
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = "Checking both now.",
                    toolCalls = listOf(
                        ToolCall("toolu_1", "read_file", """{"path":"a.kt"}"""),
                        ToolCall("toolu_2", "read_file", """{"path":"b.kt"}""")
                    )
                ),
                ChatMessage(ChatMessage.Role.TOOL, "content A", toolCallId = "toolu_1"),
                ChatMessage(ChatMessage.Role.TOOL, "content B", toolCallId = "toolu_2")
            ),
            model = "claude-3-7-sonnet-20250219",
            temperature = 1.6f,
            maxTokens = 4096,
            tools = listOf(
                ToolSpec(
                    name = "read_file",
                    description = "Read file content",
                    parameters = ToolParameters(
                        type = "object",
                        properties = mapOf("path" to PropertyDef("string", "Relative file path")),
                        required = listOf("path")
                    )
                )
            )
        )

        val json = Json.parseToJsonElement(provider.buildAnthropicRequest(request)).jsonObject

        assertEquals("Be concise.\n\nPrefer Kotlin.", json["system"]?.jsonPrimitive?.content)
        assertEquals(1.0, json["temperature"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(4096, json["max_tokens"]?.jsonPrimitive?.int)
        assertEquals(true, json["stream"]?.jsonPrimitive?.boolean)

        val messages = json["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("assistant", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", messages[2].jsonObject["role"]?.jsonPrimitive?.content)

        // Assistant turn has text + 2 tool_use blocks with parsed JsonObject inputs
        val assistantBlocks = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals(3, assistantBlocks.size)
        assertEquals("tool_use", assistantBlocks[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("a.kt", assistantBlocks[1].jsonObject["input"]?.jsonObject?.get("path")?.jsonPrimitive?.content)

        // Two consecutive TOOL messages must merge into one user turn with 2 tool_result blocks
        val toolResultBlocks = messages[2].jsonObject["content"]!!.jsonArray
        assertEquals(2, toolResultBlocks.size)
        assertEquals("tool_result", toolResultBlocks[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("toolu_1", toolResultBlocks[0].jsonObject["tool_use_id"]?.jsonPrimitive?.content)
        assertEquals("toolu_2", toolResultBlocks[1].jsonObject["tool_use_id"]?.jsonPrimitive?.content)

        // Tool definition uses input_schema
        val toolJson = json["tools"]!!.jsonArray[0].jsonObject
        assertEquals("object", toolJson["input_schema"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `buildAnthropicRequest converts orphaned tool result to plain text so role alternation remains valid`() {
        val provider = AnthropicAiProvider(
            baseUrl = "https://api.anthropic.com",
            apiKey = "sk-ant-test",
            client = OkHttpClient(),
            sseFactory = dummySseFactory
        )

        val request = ChatRequest(
            messages = listOf(
                ChatMessage(ChatMessage.Role.TOOL, "stale output after compaction", toolCallId = "old_id"),
                ChatMessage(ChatMessage.Role.USER, "Continue")
            ),
            model = "claude-3-7-sonnet-20250219"
        )

        val json = Json.parseToJsonElement(provider.buildAnthropicRequest(request)).jsonObject
        val messages = json["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        val blocks = messages[0].jsonObject["content"]!!.jsonArray
        assertEquals(2, blocks.size)
        assertEquals("text", blocks[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(blocks[0].jsonObject["text"]!!.jsonPrimitive.content.contains("stale output after compaction"))
        assertEquals("Continue", blocks[1].jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `streamChat emits text, tool calls, single aggregated usage, and done from Anthropic SSE`() = runTest {
        var capturedUrl: String? = null
        var capturedBody: String? = null

        val sseFactory = object : EventSource.Factory {
            override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
                capturedUrl = request.url.toString()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                val source = object : EventSource {
                    override fun cancel() = Unit
                    override fun request(): Request = request
                }
                listener.onEvent(
                    source, null, "message_start",
                    """{"type":"message_start","message":{"id":"msg_1","usage":{"input_tokens":100,"cache_read_input_tokens":50,"output_tokens":1}}}"""
                )
                listener.onEvent(
                    source, null, "content_block_start",
                    """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""
                )
                listener.onEvent(
                    source, null, "content_block_delta",
                    """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Checking "}}"""
                )
                listener.onEvent(
                    source, null, "content_block_delta",
                    """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"files."}}"""
                )
                listener.onEvent(
                    source, null, "content_block_stop",
                    """{"type":"content_block_stop","index":0}"""
                )
                listener.onEvent(
                    source, null, "content_block_start",
                    """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_abc","name":"read_file","input":{}}}"""
                )
                listener.onEvent(
                    source, null, "content_block_delta",
                    """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"path\":"}}"""
                )
                listener.onEvent(
                    source, null, "content_block_delta",
                    """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"Main.kt\"}"}}"""
                )
                listener.onEvent(
                    source, null, "content_block_stop",
                    """{"type":"content_block_stop","index":1}"""
                )
                listener.onEvent(
                    source, null, "message_delta",
                    """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":42}}"""
                )
                listener.onEvent(
                    source, null, "message_stop",
                    """{"type":"message_stop"}"""
                )
                return source
            }
        }

        val provider = AnthropicAiProvider(
            baseUrl = "https://api.anthropic.com/v1/messages",
            apiKey = "sk-ant-123",
            client = OkHttpClient(),
            sseFactory = sseFactory
        )

        val events = provider.streamChat(
            ChatRequest(
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "Inspect Main.kt")),
                model = "claude-3-7-sonnet-20250219"
            )
        ).toList()

        assertEquals("https://api.anthropic.com/v1/messages", capturedUrl)
        assertNotNull(capturedBody)
        assertEquals(
            listOf(
                ChatStreamEvent.TextDelta("Checking "),
                ChatStreamEvent.TextDelta("files."),
                ChatStreamEvent.ToolCallStart("toolu_abc", "read_file"),
                ChatStreamEvent.ToolCallArgsDelta("toolu_abc", """{"path":"""),
                ChatStreamEvent.ToolCallArgsDelta("toolu_abc", """"Main.kt"}"""),
                ChatStreamEvent.ToolCallComplete(ToolCall("toolu_abc", "read_file", """{"path":"Main.kt"}""")),
                ChatStreamEvent.Usage(promptTokens = 150, completionTokens = 42),
                ChatStreamEvent.Done
            ),
            events
        )
    }

    @Test
    fun `streamChat handles parameterless tool calls and inline SSE errors`() = runTest {
        val paramlessFactory = object : EventSource.Factory {
            override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
                val source = object : EventSource {
                    override fun cancel() = Unit
                    override fun request(): Request = request
                }
                listener.onEvent(
                    source, null, "content_block_start",
                    """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_list","name":"list_tasks","input":{}}}"""
                )
                listener.onEvent(
                    source, null, "content_block_stop",
                    """{"type":"content_block_stop","index":0}"""
                )
                listener.onEvent(source, null, "message_stop", """{"type":"message_stop"}""")
                return source
            }
        }

        val provider = AnthropicAiProvider(
            baseUrl = "https://api.anthropic.com",
            apiKey = "sk-ant",
            client = OkHttpClient(),
            sseFactory = paramlessFactory
        )

        val events = provider.streamChat(
            ChatRequest(messages = listOf(ChatMessage(ChatMessage.Role.USER, "list")), model = "claude-3-7-sonnet-20250219")
        ).toList()

        assertTrue(events.contains(ChatStreamEvent.ToolCallComplete(ToolCall("toolu_list", "list_tasks", "{}"))))
        assertEquals(ChatStreamEvent.Done, events.last())

        val errorFactory = object : EventSource.Factory {
            override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
                val source = object : EventSource {
                    override fun cancel() = Unit
                    override fun request(): Request = request
                }
                listener.onEvent(
                    source, null, "error",
                    """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
                )
                return source
            }
        }

        val errorProvider = AnthropicAiProvider(
            baseUrl = "https://api.anthropic.com",
            apiKey = "sk-ant",
            client = OkHttpClient(),
            sseFactory = errorFactory
        )

        val errEvents = errorProvider.streamChat(
            ChatRequest(messages = listOf(ChatMessage(ChatMessage.Role.USER, "hi")), model = "claude-3-7-sonnet-20250219")
        ).toList()

        assertEquals(1, errEvents.size)
        val err = errEvents.single() as ChatStreamEvent.Error
        assertTrue(err.message.contains("overloaded_error"))
        assertTrue(err.message.contains("Overloaded"))
    }
}
