package com.koda.dev.core.data.web

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class WebPageFetcherTest {

    private val redirectTestHost = "koda-ssrf-test.invalid"

    private fun startRedirectServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add(
                "Location",
                "http://127.0.0.1:${server.address.port}/secret"
            )
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/secret") { exchange ->
            val body = "INTERNAL-SECRET".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/redirect-safe") { exchange ->
            exchange.responseHeaders.add("Location", "/final")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/final") { exchange ->
            val body = "SAFE-REDIRECT".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }

    private fun redirectTestClient(connectTimeoutSeconds: Long = 5): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname == redirectTestHost) {
                        listOf(InetAddress.getByName("127.0.0.1"))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
            })
            .build()

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
    fun `fetch blocks redirects to loopback addresses`() = runTest {
        val server = startRedirectServer()
        try {
            val fetcher = DefaultWebPageFetcher(httpClient = redirectTestClient(), enforceSsrfDns = false)
            val result = fetcher.fetch("http://$redirectTestHost:${server.address.port}/redirect")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SecurityException)
            assertFalse(result.getOrNull()?.contains("INTERNAL-SECRET") == true)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `fetch follows redirects to allowed hosts`() = runTest {
        val server = startRedirectServer()
        try {
            val fetcher = DefaultWebPageFetcher(httpClient = redirectTestClient(), enforceSsrfDns = false)
            val result = fetcher.fetch("http://$redirectTestHost:${server.address.port}/redirect-safe")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().contains("SAFE-REDIRECT"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `fetch rejects unspecified addresses`() = runTest {
        val fetcher = DefaultWebPageFetcher(httpClient = redirectTestClient(connectTimeoutSeconds = 1))
        val result = fetcher.fetch("http://0.0.0.0:1/")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `fetch rejects IPv6 unique local addresses`() = runTest {
        val fetcher = DefaultWebPageFetcher(httpClient = redirectTestClient(connectTimeoutSeconds = 1))
        val result = fetcher.fetch("http://[fc00::1]/")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `fetch rejects carrier-grade NAT addresses`() = runTest {
        val fetcher = DefaultWebPageFetcher(httpClient = redirectTestClient(connectTimeoutSeconds = 1))
        val result = fetcher.fetch("http://100.64.0.1/")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `fetch blocks loopback and metadata URLs with characters rejected by java_net_URI`() = runTest {
        val server = startRedirectServer()
        try {
            val fetcher = DefaultWebPageFetcher()
            val bypassUrls = listOf(
                "http://127.0.0.1:${server.address.port}/secret?q=|",
                "http://127.0.0.1:${server.address.port}/secret?q={test}",
                "http://127.0.0.1:${server.address.port}/secret^test",
                "http://127.0.0.1:${server.address.port}/secret\\test",
                "http://169.254.169.254/latest/meta-data/?|"
            )
            for (url in bypassUrls) {
                val result = fetcher.fetch(url)
                assertTrue("Expected SSRF failure for $url", result.isFailure)
                assertTrue(
                    "Expected SecurityException for $url, got ${result.exceptionOrNull()}",
                    result.exceptionOrNull() is SecurityException
                )
                assertFalse(result.getOrNull()?.contains("INTERNAL-SECRET") == true)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `fetch blocks case-insensitive cloud metadata hostnames`() = runTest {
        val fetcher = DefaultWebPageFetcher()
        val result = fetcher.fetch("http://METADATA.GOOGLE.INTERNAL/computeMetadata/v1/")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `fetch blocks loopback addresses returned by a custom DNS resolver`() = runTest {
        val fetcher = DefaultWebPageFetcher(httpClient = redirectTestClient())
        val result = fetcher.fetch("http://$redirectTestHost:1/")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `fetch rejects malformed HTTP URLs instead of failing open`() = runTest {
        val fetcher = DefaultWebPageFetcher()
        val result = fetcher.fetch("http://")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
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
