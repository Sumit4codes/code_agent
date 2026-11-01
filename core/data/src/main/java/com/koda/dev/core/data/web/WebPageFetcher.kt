package com.koda.dev.core.data.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface WebPageFetcher {
    suspend fun fetch(url: String, startLine: Int? = null, endLine: Int? = null): Result<String>
}

class DefaultWebPageFetcher(
    private val httpClient: OkHttpClient = defaultHttpClient()
) : WebPageFetcher {

    override suspend fun fetch(url: String, startLine: Int?, endLine: Int?): Result<String> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()

        // 1. Validate URL protocol
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid URL protocol. Only HTTP and HTTPS are supported: $url"))
        }

        // 2. SSRF Protection: Check host
        val ssrfError = validateSsrfSafety(trimmedUrl)
        if (ssrfError != null) {
            return@withContext Result.failure(SecurityException(ssrfError))
        }

        // 3. HTTP Request
        val request = Request.Builder()
            .url(trimmedUrl)
            .header("User-Agent", DefaultWebSearchClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain,text/markdown;q=0.8,*/*;q=0.5")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP request failed with status ${response.code}: ${response.message.ifBlank { "Error" }}"))
                }

                val body = response.body
                    ?: return@withContext Result.failure(Exception("Response body was empty"))

                // Limit download to MAX_DOWNLOAD_BYTES (5 MB)
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                val source = body.source()
                source.request(MAX_DOWNLOAD_BYTES.toLong())
                val buffer = source.buffer
                val bytesToRead = minOf(buffer.size, MAX_DOWNLOAD_BYTES.toLong())
                val rawContent = buffer.clone().readString(bytesToRead, Charsets.UTF_8)

                val markdownContent = if (contentType.contains("text/markdown") ||
                    contentType.contains("text/plain") ||
                    contentType.contains("application/json")) {
                    rawContent
                } else {
                    htmlToMarkdown(rawContent, trimmedUrl)
                }

                // 4. Line pagination and truncation (similar to read_file)
                val allLines = markdownContent.lines()
                val totalLines = allLines.size
                val effectiveStart = (startLine ?: 1).coerceAtLeast(1)

                if (effectiveStart > totalLines && totalLines > 0) {
                    return@withContext Result.success(
                        "[URL: $trimmedUrl]\n(0 lines shown of $totalLines total)\n[start_line $effectiveStart is beyond end of page ($totalLines lines total).]"
                    )
                }

                val maxAllowedEnd = effectiveStart + MAX_LINES_PER_READ - 1
                val targetEnd = endLine ?: if (startLine != null) totalLines else minOf(totalLines, MAX_LINES_PER_READ)

                var lineTruncated = false
                val effectiveEnd = if (targetEnd > maxAllowedEnd) {
                    lineTruncated = true
                    maxAllowedEnd.coerceAtMost(totalLines)
                } else {
                    targetEnd.coerceAtMost(totalLines)
                }

                if (endLine == null && startLine == null && totalLines > MAX_LINES_PER_READ) {
                    lineTruncated = true
                }

                val startIndex = (effectiveStart - 1).coerceIn(0, totalLines)
                val endIndex = effectiveEnd.coerceIn(startIndex, totalLines)
                val slicedLines = allLines.subList(startIndex, endIndex)
                val linesText = slicedLines.joinToString("\n")

                // 5. Byte limit: 45 KB
                var byteTruncated = false
                var outputText = linesText
                val utf8Bytes = linesText.toByteArray(Charsets.UTF_8)
                if (utf8Bytes.size > MAX_BYTES_PER_READ) {
                    byteTruncated = true
                    val truncatedString = String(utf8Bytes, 0, MAX_BYTES_PER_READ, Charsets.UTF_8)
                    val lastNewline = truncatedString.lastIndexOf('\n')
                    outputText = if (lastNewline > MAX_BYTES_PER_READ / 2) {
                        truncatedString.substring(0, lastNewline)
                    } else {
                        truncatedString
                    }
                }

                val displayedLineCount = outputText.lines().size
                val notice = buildString {
                    if (byteTruncated) {
                        append("\n\n[Content truncated at 45 KB limit. Showing lines $effectiveStart to ${effectiveStart + displayedLineCount - 1} of $totalLines total lines. Specify 'start_line' and 'end_line' to view subsequent lines.]")
                    } else if (lineTruncated) {
                        val nextStart = effectiveEnd + 1
                        append("\n\n[Showing lines $effectiveStart to $effectiveEnd of $totalLines total lines (capped at $MAX_LINES_PER_READ lines per read). Specify 'start_line=$nextStart' to view remaining lines.]")
                    } else if (effectiveStart > 1 || effectiveEnd < totalLines) {
                        val nextStart = effectiveEnd + 1
                        val moreHint = if (nextStart <= totalLines) " Specify 'start_line=$nextStart' to view remaining lines." else ""
                        append("\n\n[Showing lines $effectiveStart to $effectiveEnd of $totalLines total lines.$moreHint]")
                    }
                }

                Result.success(outputText + notice)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateSsrfSafety(url: String): String? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return "Invalid URL host: $url"

            // Disallow cloud metadata endpoints
            if (host == "169.254.169.254" || host.contains("metadata.google.internal")) {
                return "Access to cloud metadata endpoints is blocked for security: $host"
            }

            // Resolve host IP
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                if (addr.isLoopbackAddress) {
                    return "Access to loopback address ($host) is blocked: ${addr.hostAddress}"
                }
                if (addr.isLinkLocalAddress) {
                    return "Access to link-local address ($host) is blocked: ${addr.hostAddress}"
                }
                if (addr.isSiteLocalAddress) {
                    return "Access to private local network address ($host) is blocked: ${addr.hostAddress}"
                }
            }
            null
        } catch (_: Exception) {
            // If DNS resolution fails, let OkHttp produce the exact connect error
            null
        }
    }

    private fun htmlToMarkdown(html: String, baseUrl: String): String {
        val doc = Jsoup.parse(html, baseUrl)

        val pageTitle = doc.title().trim().ifBlank {
            doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Web Page"
        }
        val metaDesc = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim() ?: ""

        // Strip non-content / noise elements
        doc.select("script, style, noscript, svg, form, iframe, nav, footer, header, aside, .advertisement, .ad, [aria-hidden=true]").remove()

        // Target primary content container if present
        val contentRoot: Element = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst(".markdown-body")
            ?: doc.selectFirst(".content")
            ?: doc.selectFirst(".post-content")
            ?: doc.body()

        val mdBuilder = StringBuilder()

        // Metadata Header
        mdBuilder.appendLine("# $pageTitle")
        mdBuilder.appendLine("**URL**: $baseUrl")
        if (metaDesc.isNotBlank()) {
            mdBuilder.appendLine("**Summary**: $metaDesc")
        }
        mdBuilder.appendLine("\n---\n")

        renderElement(contentRoot, mdBuilder)

        // Clean up excessive blank lines (more than 2 consecutive newlines -> 2)
        return mdBuilder.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun renderElement(element: Element, sb: StringBuilder) {
        for (node in element.childNodes()) {
            renderNode(node, sb)
        }
    }

    private fun renderNode(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    sb.append(text)
                } else if (text.contains(" ")) {
                    sb.append(" ")
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                when (tag) {
                    "h1" -> {
                        sb.append("\n\n# ")
                        renderElement(node, sb)
                        sb.append("\n\n")
                    }
                    "h2" -> {
                        sb.append("\n\n## ")
                        renderElement(node, sb)
                        sb.append("\n\n")
                    }
                    "h3" -> {
                        sb.append("\n\n### ")
                        renderElement(node, sb)
                        sb.append("\n\n")
                    }
                    "h4" -> {
                        sb.append("\n\n#### ")
                        renderElement(node, sb)
                        sb.append("\n\n")
                    }
                    "h5", "h6" -> {
                        sb.append("\n\n##### ")
                        renderElement(node, sb)
                        sb.append("\n\n")
                    }
                    "p" -> {
                        sb.append("\n\n")
                        renderElement(node, sb)
                        sb.append("\n\n")
                    }
                    "pre" -> {
                        val codeElem = node.selectFirst("code")
                        val lang = codeElem?.className()?.substringAfter("language-")?.substringBefore(" ") ?: ""
                        val codeText = (codeElem ?: node).wholeText().trimEnd()
                        sb.append("\n\n```$lang\n")
                        sb.append(codeText)
                        sb.append("\n```\n\n")
                    }
                    "code" -> {
                        if (node.parent()?.tagName()?.lowercase() != "pre") {
                            sb.append("`").append(node.text().trim()).append("`")
                        }
                    }
                    "ul" -> {
                        sb.append("\n")
                        for (li in node.children()) {
                            if (li.tagName().equals("li", ignoreCase = true)) {
                                sb.append("\n- ")
                                renderElement(li, sb)
                            }
                        }
                        sb.append("\n")
                    }
                    "ol" -> {
                        sb.append("\n")
                        var counter = 1
                        for (li in node.children()) {
                            if (li.tagName().equals("li", ignoreCase = true)) {
                                sb.append("\n$counter. ")
                                renderElement(li, sb)
                                counter++
                            }
                        }
                        sb.append("\n")
                    }
                    "blockquote" -> {
                        sb.append("\n\n> ")
                        val text = node.text().trim()
                        sb.append(text.replace("\n", "\n> "))
                        sb.append("\n\n")
                    }
                    "a" -> {
                        val href = node.absUrl("href").ifBlank { node.attr("href") }
                        val text = node.text().trim()
                        if (href.isNotBlank() && text.isNotBlank()) {
                            sb.append("[$text]($href)")
                        } else if (text.isNotBlank()) {
                            sb.append(text)
                        }
                    }
                    "table" -> {
                        renderTable(node, sb)
                    }
                    "br" -> sb.append("\n")
                    "hr" -> sb.append("\n\n---\n\n")
                    else -> renderElement(node, sb)
                }
            }
        }
    }

    private fun renderTable(table: Element, sb: StringBuilder) {
        val rows = table.select("tr")
        if (rows.isEmpty()) return

        sb.append("\n\n")
        var headerRendered = false

        for (row in rows) {
            val ths = row.select("th")
            val tds = row.select("td")

            if (ths.isNotEmpty()) {
                val headerCells = ths.map { it.text().trim() }
                sb.append("| ").append(headerCells.joinToString(" | ")).append(" |\n")
                sb.append("| ").append(headerCells.joinToString(" | ") { "---" }).append(" |\n")
                headerRendered = true
            } else if (tds.isNotEmpty()) {
                val cells = tds.map { it.text().trim() }
                if (!headerRendered) {
                    sb.append("| ").append(cells.indices.joinToString(" | ") { "Col ${it + 1}" }).append(" |\n")
                    sb.append("| ").append(cells.indices.joinToString(" | ") { "---" }).append(" |\n")
                    headerRendered = true
                }
                sb.append("| ").append(cells.joinToString(" | ")).append(" |\n")
            }
        }
        sb.append("\n")
    }

    companion object {
        const val MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024 // 5 MB
        const val MAX_LINES_PER_READ = 600
        const val MAX_BYTES_PER_READ = 46_080 // 45 KB

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
