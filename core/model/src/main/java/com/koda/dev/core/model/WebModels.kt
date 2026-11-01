package com.koda.dev.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

@Serializable
enum class WebSearchEngine(val displayName: String) {
    DUCKDUCKGO("DuckDuckGo (Free / No Key)"),
    TAVILY("Tavily AI Search"),
    BRAVE("Brave Search"),
    SEARXNG("SearXNG / Custom")
}
