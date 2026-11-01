package com.koda.dev.core.data.web

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPageFetcherTest {

    @Test
    fun `fetch rejects unsupported protocols`() = runTest {
        val fetcher = DefaultWebPageFetcher()
        val fileResult = fetcher.fetch("file:///etc/passwd")
        assertTrue(fileResult.isFailure)
        assertTrue(fileResult.exceptionOrNull()?.message?.contains("Only HTTP and HTTPS") == true)

        val javascriptResult = fetcher.fetch("javascript:alert(1)")
        assertTrue(javascriptResult.isFailure)
    }

    @Test
    fun `fetch blocks cloud metadata endpoints`() = runTest {
        val fetcher = DefaultWebPageFetcher()
        val result = fetcher.fetch("http://169.254.169.254/latest/meta-data")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()?.message?.contains("cloud metadata") == true)
    }

    @Test
    fun `fetch converts HTML headings, paragraphs, lists, code, and tables to markdown`() = runTest {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Coroutines Overview</title>
                <meta name="description" content="Kotlin coroutines guide">
            </head>
            <body>
                <header><nav><a href="/home">Home</a></nav></header>
                <main>
                    <h1>Introduction</h1>
                    <p>Coroutines simplify asynchronous programming.</p>
                    <h2>Key Concepts</h2>
                    <ul>
                        <li>Suspend functions</li>
                        <li>Builders</li>
                    </ul>
                    <pre><code class="language-kotlin">suspend fun loadData(): String { return "data" }</code></pre>
                    <p>Use inline `delay()`.</p>
                    <table>
                        <tr><th>Feature</th><th>Status</th></tr>
                        <tr><td>Flow</td><td>Stable</td></tr>
                    </table>
                </main>
                <footer><p>Copyright 2026</p></footer>
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
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body(sampleHtml.toResponseBody())
                    .build()
            }
            .build()

        val fetcher = DefaultWebPageFetcher(httpClient = mockHttpClient)
        val result = fetcher.fetch("https://kotlinlang.org/docs/coroutines.html")

        assertTrue(result.isSuccess)
        val markdown = result.getOrThrow()

        // Verify title & metadata
        assertTrue(markdown.contains("# Coroutines Overview"))
        assertTrue(markdown.contains("**URL**: https://kotlinlang.org/docs/coroutines.html"))
        assertTrue(markdown.contains("**Summary**: Kotlin coroutines guide"))

        // Verify noise tags stripped
        assertFalse(markdown.contains("Home"))
        assertFalse(markdown.contains("Copyright 2026"))

        // Verify markdown elements
        assertTrue(markdown.contains("## Key Concepts"))
        assertTrue(markdown.contains("- Suspend functions"))
        assertTrue(markdown.contains("- Builders"))
        assertTrue(markdown.contains("```kotlin"))
        assertTrue(markdown.contains("suspend fun loadData(): String"))
        assertTrue(markdown.contains("`delay()`"))
        assertTrue(markdown.contains("| Feature | Status |"))
        assertTrue(markdown.contains("| Flow | Stable |"))
    }

    @Test
    fun `fetch supports pagination via start_line and end_line`() = runTest {
        val paragraphs = (1..100).joinToString("\n") { "<p>Paragraph line $it</p>" }
        val sampleHtml = "<html><head><title>Test Page</title></head><body><main>$paragraphs</main></body></html>"

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html")
                    .body(sampleHtml.toResponseBody())
                    .build()
            }
            .build()

        val fetcher = DefaultWebPageFetcher(httpClient = mockHttpClient)

        val pagedResult = fetcher.fetch("https://example.com/paged", startLine = 5, endLine = 10)
        assertTrue(pagedResult.isSuccess)
        val content = pagedResult.getOrThrow()

        assertTrue(content.contains("Paragraph line"))
        assertTrue(content.contains("Showing lines 5 to 10"))
    }

    @Test
    fun `fetch directly returns markdown or plain text responses`() = runTest {
        val rawMarkdown = "# Direct Markdown\n\nThis is raw markdown content."

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/markdown; charset=utf-8")
                    .body(rawMarkdown.toResponseBody())
                    .build()
            }
            .build()

        val fetcher = DefaultWebPageFetcher(httpClient = mockHttpClient)
        val result = fetcher.fetch("https://raw.githubusercontent.com/repo/README.md")

        assertTrue(result.isSuccess)
        assertEquals(rawMarkdown, result.getOrThrow().trim())
    }
}
