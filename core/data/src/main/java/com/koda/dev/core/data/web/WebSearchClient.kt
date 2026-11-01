package com.koda.dev.core.data.web

import com.koda.dev.core.model.WebSearchEngine
import com.koda.dev.core.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface WebSearchClient {
    suspend fun search(query: String, limit: Int = 5): Result<List<WebSearchResult>>
    fun formatResultsForAgent(query: String, results: List<WebSearchResult>): String
}

class DefaultWebSearchClient(
    private val preferencesRepository: WebSearchPreferencesRepository? = null,
    private val httpClient: OkHttpClient = defaultHttpClient()
) : WebSearchClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, limit: Int): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Search query cannot be empty"))
        }

        val clampedLimit = limit.coerceIn(1, 10)
        val engine = preferencesRepository?.getSearchEngine() ?: WebSearchEngine.DUCKDUCKGO
        val apiKey = preferencesRepository?.getApiKey(engine) ?: ""

        when (engine) {
            WebSearchEngine.DUCKDUCKGO -> searchDuckDuckGo(trimmedQuery, clampedLimit)
            WebSearchEngine.TAVILY -> {
                if (apiKey.isBlank()) {
                    // Fallback to DuckDuckGo if Tavily key is missing
                    searchDuckDuckGo(trimmedQuery, clampedLimit)
                } else {
                    searchTavily(trimmedQuery, clampedLimit, apiKey)
                }
            }
            WebSearchEngine.BRAVE -> {
                if (apiKey.isBlank()) {
                    searchDuckDuckGo(trimmedQuery, clampedLimit)
                } else {
                    searchBrave(trimmedQuery, clampedLimit, apiKey)
                }
            }
            WebSearchEngine.SEARXNG -> {
                val customUrl = preferencesRepository?.getSearxngUrl() ?: ""
                if (customUrl.isBlank()) {
                    searchDuckDuckGo(trimmedQuery, clampedLimit)
                } else {
                    searchSearxng(trimmedQuery, clampedLimit, customUrl)
                }
            }
        }
    }

    private fun searchDuckDuckGo(query: String, limit: Int): Result<List<WebSearchResult>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"

        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Try DDG Lite fallback
                    return searchDuckDuckGoLite(query, limit)
                }
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html, "https://html.duckduckgo.com")
                val resultElements = doc.select(".result.results_links")
                val results = mutableListOf<WebSearchResult>()

                for (elem in resultElements) {
                    if (results.size >= limit) break
                    val titleLink = elem.selectFirst("h2.result__title a.result__a") ?: continue
                    val title = titleLink.text().trim()
                    val rawHref = titleLink.attr("href")
                    val snippet = elem.selectFirst(".result__snippet")?.text()?.trim() ?: ""

                    val targetUrl = unwrapDuckDuckGoUrl(rawHref)
                    if (targetUrl.isNotBlank() && title.isNotBlank()) {
                        results.add(WebSearchResult(title = title, url = targetUrl, snippet = snippet))
                    }
                }

                if (results.isEmpty()) {
                    searchDuckDuckGoLite(query, limit)
                } else {
                    Result.success(results)
                }
            }
        } catch (_: Exception) {
            searchDuckDuckGoLite(query, limit)
        }
    }

    private fun searchDuckDuckGoLite(query: String, limit: Int): Result<List<WebSearchResult>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://lite.duckduckgo.com/lite/"
        val formBody = okhttp3.FormBody.Builder()
            .add("q", query)
            .build()

        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .post(formBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("DuckDuckGo returned HTTP ${response.code}"))
                }
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html, "https://lite.duckduckgo.com")
                val results = mutableListOf<WebSearchResult>()

                val linkElements = doc.select("a.result-link")
                val snippetElements = doc.select("td.result-snippet")

                for (i in linkElements.indices) {
                    if (results.size >= limit) break
                    val link = linkElements[i]
                    val title = link.text().trim()
                    val href = link.attr("href")
                    val snippet = if (i < snippetElements.size) snippetElements[i].text().trim() else ""

                    val targetUrl = unwrapDuckDuckGoUrl(href)
                    if (targetUrl.isNotBlank() && title.isNotBlank()) {
                        results.add(WebSearchResult(title = title, url = targetUrl, snippet = snippet))
                    }
                }

                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun unwrapDuckDuckGoUrl(rawUrl: String): String {
        return try {
            if (rawUrl.contains("uddg=")) {
                val fullUrl = when {
                    rawUrl.startsWith("//") -> "https:$rawUrl"
                    rawUrl.startsWith("/") -> "https://duckduckgo.com$rawUrl"
                    else -> rawUrl
                }
                val httpUrl = fullUrl.toHttpUrlOrNull()
                val uddg = httpUrl?.queryParameter("uddg")
                if (!uddg.isNullOrBlank()) {
                    return URLDecoder.decode(uddg, "UTF-8")
                }
                val regex = Regex("""[?&]uddg=([^&]+)""")
                val match = regex.find(fullUrl)
                if (match != null) {
                    val encoded = match.groupValues[1]
                    return URLDecoder.decode(encoded, "UTF-8")
                }
            }
            when {
                rawUrl.startsWith("//") -> "https:$rawUrl"
                rawUrl.startsWith("/") -> "https://duckduckgo.com$rawUrl"
                else -> rawUrl
            }
        } catch (_: Exception) {
            rawUrl
        }
    }

    private fun searchTavily(query: String, limit: Int, apiKey: String): Result<List<WebSearchResult>> {
        val payload = """{"query":${Json.encodeToString(kotlinx.serialization.serializer(), query)},"max_results":$limit}"""
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Tavily returned HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return Result.success(emptyList())
                val jsonObject = json.parseToJsonElement(body).jsonObject
                val resultsArray = jsonObject["results"]?.jsonArray ?: return Result.success(emptyList())

                val list = resultsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val url = obj["url"]?.jsonPrimitive?.content ?: ""
                    val content = obj["content"]?.jsonPrimitive?.content ?: ""
                    if (title.isNotBlank() && url.isNotBlank()) {
                        WebSearchResult(title, url, content)
                    } else null
                }
                Result.success(list.take(limit))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchBrave(query: String, limit: Int, apiKey: String): Result<List<WebSearchResult>> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=$limit")
            .header("X-Subscription-Token", apiKey)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Brave Search returned HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return Result.success(emptyList())
                val jsonObject = json.parseToJsonElement(body).jsonObject
                val web = jsonObject["web"]?.jsonObject
                val resultsArray = web?.get("results")?.jsonArray ?: return Result.success(emptyList())

                val list = resultsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val url = obj["url"]?.jsonPrimitive?.content ?: ""
                    val description = obj["description"]?.jsonPrimitive?.content ?: ""
                    if (title.isNotBlank() && url.isNotBlank()) {
                        WebSearchResult(title, url, description)
                    } else null
                }
                Result.success(list.take(limit))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchSearxng(query: String, limit: Int, instanceUrl: String): Result<List<WebSearchResult>> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val baseUrl = instanceUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/search?q=$encoded&format=json")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("SearXNG returned HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return Result.success(emptyList())
                val jsonObject = json.parseToJsonElement(body).jsonObject
                val resultsArray = jsonObject["results"]?.jsonArray ?: return Result.success(emptyList())

                val list = resultsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val url = obj["url"]?.jsonPrimitive?.content ?: ""
                    val content = obj["content"]?.jsonPrimitive?.content ?: ""
                    if (title.isNotBlank() && url.isNotBlank()) {
                        WebSearchResult(title, url, content)
                    } else null
                }
                Result.success(list.take(limit))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun formatResultsForAgent(query: String, results: List<WebSearchResult>): String {
        if (results.isEmpty()) {
            return "No web search results found for query: \"$query\""
        }
        return buildString {
            appendLine("Found ${results.size} web search results for \"$query\":\n")
            results.forEachIndexed { index, r ->
                appendLine("${index + 1}. [${r.title}](${r.url})")
                if (r.snippet.isNotBlank()) {
                    appendLine("   ${r.snippet}")
                }
                appendLine()
            }
            append("Tip: Use `fetch_web_page(url = \"...\")` to read the complete documentation or page content.")
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; KodaDev) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
