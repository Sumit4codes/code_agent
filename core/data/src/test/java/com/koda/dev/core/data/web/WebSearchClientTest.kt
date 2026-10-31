package com.koda.dev.core.data.web

import com.koda.dev.core.model.WebSearchResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchClientTest {

    @Test
    fun `search with empty query returns failure`() = runTest {
        val client = DefaultWebSearchClient()
        val result = client.search("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun `formatResultsForAgent formats list of results with markdown links and snippets`() {
        val client = DefaultWebSearchClient()
        val results = listOf(
            WebSearchResult(
                title = "Flows | Kotlin Documentation",
                url = "https://kotlinlang.org/docs/coroutines-flow.html",
                snippet = "In coroutines, a flow is a type that can emit multiple values sequentially..."
            ),
            WebSearchResult(
                title = "StateFlow and SharedFlow",
                url = "https://developer.android.com/kotlin/flow/stateflow-and-sharedflow",
                snippet = "StateFlow is a state-holder observable flow that emits the current and new state..."
            )
        )

        val formatted = client.formatResultsForAgent("kotlin flow", results)
        assertTrue(formatted.contains("Found 2 web search results"))
        assertTrue(formatted.contains("1. [Flows | Kotlin Documentation](https://kotlinlang.org/docs/coroutines-flow.html)"))
        assertTrue(formatted.contains("2. [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)"))
        assertTrue(formatted.contains("fetch_web_page"))
    }

    @Test
    fun `formatResultsForAgent returns friendly message when no results found`() {
        val client = DefaultWebSearchClient()
        val formatted = client.formatResultsForAgent("xyznonexistent123", emptyList())
        assertEquals("No web search results found for query: \"xyznonexistent123\"", formatted)
    }

    @Test
    fun `DuckDuckGo HTML parser extracts title, unwrapped URL, and snippet`() = runTest {
        val sampleDdgHtml = """
            <!DOCTYPE html>
            <html>
            <body>
            <div class="results">
                <div class="result results_links results_links_deep web-result">
                    <h2 class="result__title">
                        <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fflow.html&rut=123">
                            Kotlin Flow Guide
                        </a>
                    </h2>
                    <a class="result__snippet" href="#">
                        A complete guide to asynchronous streams in Kotlin.
                    </a>
                </div>
                <div class="result results_links results_links_deep web-result">
                    <h2 class="result__title">
                        <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fdeveloper.android.com%2Fflow&rut=456">
                            Android Flows
                        </a>
                    </h2>
                    <a class="result__snippet" href="#">
                        How to use Kotlin flows on Android.
                    </a>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleDdgHtml.toResponseBody())
                    .build()
            }
            .build()

        val client = DefaultWebSearchClient(httpClient = mockHttpClient)
        val result = client.search("kotlin flow", limit = 5)

        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)

        assertEquals("Kotlin Flow Guide", list[0].title)
        assertEquals("https://kotlinlang.org/docs/flow.html", list[0].url)
        assertEquals("A complete guide to asynchronous streams in Kotlin.", list[0].snippet)

        assertEquals("Android Flows", list[1].title)
        assertEquals("https://developer.android.com/flow", list[1].url)
    }

    @Test
    fun `search respects limit parameter`() = runTest {
        val sampleDdgHtml = """
            <div class="result results_links"><h2 class="result__title"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fa.com">A</a></h2></div>
            <div class="result results_links"><h2 class="result__title"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fb.com">B</a></h2></div>
            <div class="result results_links"><h2 class="result__title"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fc.com">C</a></h2></div>
        """.trimIndent()

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleDdgHtml.toResponseBody())
                    .build()
            }
            .build()

        val client = DefaultWebSearchClient(httpClient = mockHttpClient)
        val result = client.search("test", limit = 2)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }
}
