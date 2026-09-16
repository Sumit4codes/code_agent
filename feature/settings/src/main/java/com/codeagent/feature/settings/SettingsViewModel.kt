package com.codeagent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

enum class ProviderPreset(
    val label: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val model: String,
    val description: String
) {
    OPENAI(
        label = "OpenAI",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.openai.com",
        model = "gpt-4o",
        description = "Official OpenAI API (GPT-4o, GPT-4o-mini)"
    ),
    OPENROUTER(
        label = "OpenRouter",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/v1",
        model = "anthropic/claude-3.7-sonnet",
        description = "Universal API for Claude, DeepSeek, Llama, Qwen"
    ),
    DEEPSEEK(
        label = "DeepSeek",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-chat",
        description = "DeepSeek-V3 & DeepSeek-R1 models"
    ),
    OLLAMA(
        label = "Ollama (Local)",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "http://localhost:11434/v1",
        model = "qwen2.5-coder",
        description = "Run local models privately via Ollama on device/LAN"
    ),
    ANTHROPIC(
        label = "Anthropic",
        providerType = ProviderType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        model = "claude-3-7-sonnet-20250219",
        description = "Direct Anthropic Claude Messages API"
    )
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String
)

data class SettingsState(
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val temperature: Float = 0.7f,
    val maxTokens: String = "4096",
    val systemPrompt: String = "",
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val isSaveError: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: ConnectionTestResult? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSavedSettings()
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val default = settingsRepository.getActiveProvider()
            if (default != null) {
                val apiKey = settingsRepository.getApiKey(default.id) ?: ""
                _state.value = _state.value.copy(
                    providerType = default.providerType,
                    baseUrl = default.baseUrl,
                    apiKey = apiKey,
                    model = default.model,
                    temperature = default.temperature,
                    maxTokens = default.maxTokens.toString(),
                    systemPrompt = default.systemPrompt ?: ""
                )
            }
        }
    }

    fun applyPreset(preset: ProviderPreset) {
        _state.value = _state.value.copy(
            providerType = preset.providerType,
            baseUrl = preset.baseUrl,
            model = preset.model,
            connectionTestResult = null
        )
    }

    fun updateProviderType(type: ProviderType) {
        val defaults = when (type) {
            ProviderType.OPENAI_COMPATIBLE -> "https://api.openai.com" to "gpt-4o"
            ProviderType.ANTHROPIC -> "https://api.anthropic.com" to "claude-3-7-sonnet-20250219"
        }
        _state.value = _state.value.copy(
            providerType = type,
            baseUrl = defaults.first,
            model = defaults.second,
            connectionTestResult = null
        )
    }

    fun updateBaseUrl(url: String) {
        _state.value = _state.value.copy(baseUrl = url, connectionTestResult = null)
    }

    fun updateApiKey(key: String) {
        _state.value = _state.value.copy(apiKey = key, connectionTestResult = null)
    }

    fun updateModel(model: String) {
        _state.value = _state.value.copy(model = model)
    }

    fun updateTemperature(temp: Float) {
        _state.value = _state.value.copy(temperature = (Math.round(temp * 100f) / 100f))
    }

    fun updateMaxTokens(tokens: String) {
        _state.value = _state.value.copy(maxTokens = tokens)
    }

    fun updateSystemPrompt(prompt: String) {
        _state.value = _state.value.copy(systemPrompt = prompt)
    }

    fun dismissSaveMessage() {
        _state.value = _state.value.copy(saveMessage = null)
    }

    fun testConnection() {
        val currentState = _state.value
        val cleanUrl = currentState.baseUrl.trim().trimEnd('/')
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.value = _state.value.copy(
                connectionTestResult = ConnectionTestResult(false, "Invalid URL: Must start with http:// or https://")
            )
            return
        }

        _state.value = _state.value.copy(isTestingConnection = true, connectionTestResult = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = if (cleanUrl.endsWith("/v1")) cleanUrl else "$cleanUrl/v1"
                val testUrl = "$base/models"

                val reqBuilder = Request.Builder()
                    .url(testUrl)
                    .get()

                if (currentState.apiKey.isNotBlank()) {
                    if (currentState.providerType == ProviderType.ANTHROPIC) {
                        reqBuilder.header("x-api-key", currentState.apiKey.trim())
                        reqBuilder.header("anthropic-version", "2023-06-01")
                    } else {
                        reqBuilder.header("Authorization", "Bearer ${currentState.apiKey.trim()}")
                    }
                }

                okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val hasModels = body.contains("\"id\"") || body.contains("\"data\"")
                        val info = if (hasModels) {
                            "Connection verified! (HTTP ${response.code} OK - endpoint responsive)"
                        } else {
                            "Connection verified! (HTTP ${response.code} OK)"
                        }
                        _state.value = _state.value.copy(
                            isTestingConnection = false,
                            connectionTestResult = ConnectionTestResult(true, info)
                        )
                    } else {
                        val errBody = try { response.body?.string()?.take(160) } catch (_: Exception) { null }
                        val detail = if (!errBody.isNullOrBlank()) ": $errBody" else ""
                        _state.value = _state.value.copy(
                            isTestingConnection = false,
                            connectionTestResult = ConnectionTestResult(
                                false,
                                "Server returned HTTP ${response.code} (${response.message})$detail"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = ConnectionTestResult(
                        false,
                        "Connection failed: ${e.localizedMessage ?: e.message ?: "Network error"}"
                    )
                )
            }
        }
    }

    fun saveSettings() {
        val currentState = _state.value
        _state.value = _state.value.copy(isSaving = true, saveMessage = null)

        viewModelScope.launch {
            try {
                val cleanUrl = currentState.baseUrl.trim().trimEnd('/')
                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        saveMessage = "Base URL must start with http:// or https://",
                        isSaveError = true
                    )
                    return@launch
                }

                val id = "provider_${currentState.providerType.name.lowercase()}"
                settingsRepository.saveProvider(
                    ProviderConfig(
                        id = id,
                        providerType = currentState.providerType,
                        name = currentState.providerType.name,
                        model = currentState.model.trim(),
                        baseUrl = cleanUrl,
                        temperature = currentState.temperature,
                        maxTokens = currentState.maxTokens.toIntOrNull() ?: 4096,
                        systemPrompt = currentState.systemPrompt.ifBlank { null },
                        isDefault = true
                    )
                )
                if (currentState.apiKey.isNotBlank()) {
                    settingsRepository.saveApiKey(id, currentState.apiKey.trim())
                }
                _state.value = _state.value.copy(
                    isSaving = false,
                    saveMessage = "Settings saved successfully",
                    isSaveError = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    saveMessage = "Failed to save: ${e.localizedMessage ?: e.message}",
                    isSaveError = true
                )
            }
        }
    }
}
