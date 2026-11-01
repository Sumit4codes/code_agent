package com.koda.dev.core.data.web

import android.content.Context
import android.content.SharedPreferences
import com.koda.dev.core.data.ApiKeyStorage
import com.koda.dev.core.model.WebSearchEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface WebSearchPreferencesRepository {
    val searchEngineFlow: Flow<WebSearchEngine>
    val searchEnabledFlow: Flow<Boolean>
    val searxngUrlFlow: Flow<String>

    fun getSearchEngine(): WebSearchEngine
    fun isSearchEnabled(): Boolean
    fun getSearxngUrl(): String
    fun getApiKey(engine: WebSearchEngine): String?

    suspend fun setSearchEngine(engine: WebSearchEngine)
    suspend fun setSearchEnabled(enabled: Boolean)
    suspend fun setSearxngUrl(url: String)
    suspend fun setApiKey(engine: WebSearchEngine, apiKey: String)
}

@Singleton
class DefaultWebSearchPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyStorage: ApiKeyStorage
) : WebSearchPreferencesRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("web_search_preferences", Context.MODE_PRIVATE)
    }

    private val _searchEngineFlow = MutableStateFlow(loadSearchEngine())
    override val searchEngineFlow: Flow<WebSearchEngine> = _searchEngineFlow.asStateFlow()

    private val _searchEnabledFlow = MutableStateFlow(loadSearchEnabled())
    override val searchEnabledFlow: Flow<Boolean> = _searchEnabledFlow.asStateFlow()

    private val _searxngUrlFlow = MutableStateFlow(loadSearxngUrl())
    override val searxngUrlFlow: Flow<String> = _searxngUrlFlow.asStateFlow()

    private fun loadSearchEngine(): WebSearchEngine {
        val name = prefs.getString(KEY_SEARCH_ENGINE, WebSearchEngine.DUCKDUCKGO.name)
        return try {
            WebSearchEngine.valueOf(name ?: WebSearchEngine.DUCKDUCKGO.name)
        } catch (_: Exception) {
            WebSearchEngine.DUCKDUCKGO
        }
    }

    private fun loadSearchEnabled(): Boolean = prefs.getBoolean(KEY_SEARCH_ENABLED, true)

    private fun loadSearxngUrl(): String = prefs.getString(KEY_SEARXNG_URL, "") ?: ""

    override fun getSearchEngine(): WebSearchEngine = _searchEngineFlow.value

    override fun isSearchEnabled(): Boolean = _searchEnabledFlow.value

    override fun getSearxngUrl(): String = _searxngUrlFlow.value

    override fun getApiKey(engine: WebSearchEngine): String? {
        val keyId = "websearch_${engine.name.lowercase()}"
        return apiKeyStorage.getKey(keyId)
    }

    override suspend fun setSearchEngine(engine: WebSearchEngine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine.name).apply()
        _searchEngineFlow.value = engine
    }

    override suspend fun setSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH_ENABLED, enabled).apply()
        _searchEnabledFlow.value = enabled
    }

    override suspend fun setSearxngUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_SEARXNG_URL, trimmed).apply()
        _searxngUrlFlow.value = trimmed
    }

    override suspend fun setApiKey(engine: WebSearchEngine, apiKey: String) {
        val keyId = "websearch_${engine.name.lowercase()}"
        apiKeyStorage.storeKey(keyId, apiKey.trim())
    }

    companion object {
        private const val KEY_SEARCH_ENGINE = "web_search_engine"
        private const val KEY_SEARCH_ENABLED = "web_search_enabled"
        private const val KEY_SEARXNG_URL = "web_search_searxng_url"
    }
}
