package com.codeagent.core.ai

import com.codeagent.core.model.ProviderType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DefaultModelFetcherTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `fetchModels fails on invalid url`() = runTest {
        val client = OkHttpClient()
        val fetcher = DefaultModelFetcher(client, json)
        val result = fetcher.fetchModels("not-a-url", "key", ProviderType.OPENAI_COMPATIBLE)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Must start with http") == true)
    }

    @Test
    fun `fetchModels parses OpenAI format response and sorts flagship models first`() = runTest {
        var requestedUrl: String? = null
        var authHeader: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requestedUrl = request.url.toString()
                authHeader = request.header("Authorization")

                val mockBody = """
                    {
                        "data": [
                            {"id": "text-embedding-3-small"},
                            {"id": "gpt-4o-mini"},
                            {"id": "gpt-4o"},
                            {"id": "o3-mini"}
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

        val fetcher = DefaultModelFetcher(client, json)
        val result = fetcher.fetchModels("https://api.openai.com/v1", "sk-test", ProviderType.OPENAI_COMPATIBLE)

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals("https://api.openai.com/v1/models", requestedUrl)
        assertEquals("Bearer sk-test", authHeader)
        assertEquals("gpt-4o", models[0])
        assertEquals("o3-mini", models[1])
        assertEquals("gpt-4o-mini", models[2])
    }

    @Test
    fun `fetchModels sets correct Anthropic headers and parses data display_name`() = runTest {
        var requestedUrl: String? = null
        var apiKeyHeader: String? = null
        var anthropicVersionHeader: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requestedUrl = request.url.toString()
                apiKeyHeader = request.header("x-api-key")
                anthropicVersionHeader = request.header("anthropic-version")

                val mockBody = """
                    {
                        "data": [
                            {"id": "claude-3-7-sonnet-20250219", "display_name": "Claude 3.7 Sonnet"},
                            {"id": "claude-3-5-haiku-20241022", "display_name": "Claude 3.5 Haiku"}
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

        val fetcher = DefaultModelFetcher(client, json)
        val result = fetcher.fetchModels("https://api.anthropic.com/v1", "sk-ant-test", ProviderType.ANTHROPIC)

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals("https://api.anthropic.com/v1/models", requestedUrl)
        assertEquals("sk-ant-test", apiKeyHeader)
        assertEquals("2023-06-01", anthropicVersionHeader)
        assertEquals("claude-3-7-sonnet-20250219", models[0])
    }

    @Test
    fun `fetchModels parses Ollama models list format`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val mockBody = """
                    {
                        "models": [
                            {"name": "qwen2.5-coder:latest"},
                            {"name": "llama3.2:latest"}
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

        val fetcher = DefaultModelFetcher(client, json)
        val result = fetcher.fetchModels("http://localhost:11434/v1", "", ProviderType.OPENAI_COMPATIBLE)

        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertTrue(models.contains("qwen2.5-coder:latest"))
        assertTrue(models.contains("llama3.2:latest"))
    }
}
