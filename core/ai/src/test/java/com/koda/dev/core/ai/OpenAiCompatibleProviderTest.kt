package com.koda.dev.core.ai

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

class OpenAiCompatibleProviderTest {

    private val dummySseFactory = object : EventSource.Factory {
        override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
            return object : EventSource {
                override fun cancel() {}
                override fun request(): Request = request
            }
        }
    }

    @Test
    fun `listModels omits auth header for keyless kilo gateway and filters free models`() = runTest {
        var capturedAuth: String? = null
        var capturedUserAgent: String? = null
        var capturedTitle: String? = null
        var capturedReferer: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedAuth = request.header("Authorization")
                capturedUserAgent = request.header("User-Agent")
                capturedTitle = request.header("X-Title")
                capturedReferer = request.header("HTTP-Referer")

                val mockBody = """
                    {
                        "data": [
                            {"id": "kilo-auto/efficient", "isFree": false, "context_length": 1000000},
                            {"id": "kilo-auto/free", "isFree": true, "context_length": 256000},
                            {"id": "nvidia/nemotron-3.5-super-vl:free", "isFree": true, "context_length": 1000000},
                            {"id": "paid/claude-3.5-sonnet", "isFree": false, "context_length": 200000}
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val provider = OpenAiCompatibleProvider(
            baseUrl = "https://api.kilo.ai/api/gateway",
            apiKey = "",
            client = client,
            sseFactory = dummySseFactory
        )

        val models = provider.listModels()

        assertNull("Authorization header must be omitted for keyless kilo requests", capturedAuth)
        assertEquals("KodaDev/1.0 (Android)", capturedUserAgent)
        assertEquals("Koda Dev", capturedTitle)
        assertEquals("https://github.com/Sumit4codes/code_agent", capturedReferer)

        assertEquals(2, models.size)
        val freeAuto = models.first { it.id == "kilo-auto/free" }
        assertEquals(256_000, freeAuto.contextWindow)

        val nemotron = models.first { it.id == "nvidia/nemotron-3.5-super-vl:free" }
        assertEquals(1_000_000, nemotron.contextWindow)
    }

    @Test
    fun `listModels attaches auth header when api key is provided for standard endpoint`() = runTest {
        var capturedAuth: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedAuth = request.header("Authorization")

                val mockBody = """
                    {
                        "data": [
                            {"id": "gpt-4o", "context_length": 128000}
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val provider = OpenAiCompatibleProvider(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-secret",
            client = client,
            sseFactory = dummySseFactory
        )

        val models = provider.listModels()
        assertEquals("Bearer sk-test-secret", capturedAuth)
        assertEquals(1, models.size)
        assertEquals("gpt-4o", models[0].id)
    }

    @Test
    fun `streamChat formats tool parameters with explicit type object schema`() = runTest {
        var capturedRequestBody: String? = null

        val capturingSseFactory = object : EventSource.Factory {
            override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedRequestBody = buffer.readUtf8()
                return object : EventSource {
                    override fun cancel() {}
                    override fun request(): Request = request
                }
            }
        }

        val provider = OpenAiCompatibleProvider(
            baseUrl = "https://api.kilo.ai/api/gateway",
            apiKey = "",
            client = OkHttpClient(),
            sseFactory = capturingSseFactory
        )

        val tool = ToolSpec(
            name = "list_files",
            description = "List files in directory",
            parameters = ToolParameters(
                type = "object",
                properties = mapOf(
                    "path" to PropertyDef("string", "Directory path")
                ),
                required = listOf("path")
            )
        )

        val chatRequest = ChatRequest(
            messages = listOf(ChatMessage(ChatMessage.Role.USER, "hi")),
            model = "kilo-auto/free",
            tools = listOf(tool)
        )

        val job = backgroundScope.launch {
            provider.streamChat(chatRequest).collect {}
        }
        kotlinx.coroutines.delay(50)
        job.cancel()

        assertNotNull(capturedRequestBody)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(capturedRequestBody!!).jsonObject
        val toolsArray = json["tools"]?.jsonArray
        assertNotNull(toolsArray)
        val fnObj = toolsArray!![0].jsonObject["function"]?.jsonObject
        assertNotNull(fnObj)
        val paramsObj = fnObj!!["parameters"]?.jsonObject
        assertNotNull(paramsObj)
        assertEquals("object", paramsObj!!["type"]?.jsonPrimitive?.content)
    }
}
