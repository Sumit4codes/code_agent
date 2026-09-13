package com.codeagent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val temperature: String = "0.7",
    val maxTokens: String = "4096",
    val systemPrompt: String = "",
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
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
                    temperature = default.temperature.toString(),
                    maxTokens = default.maxTokens.toString(),
                    systemPrompt = default.systemPrompt ?: ""
                )
            }
        }
    }

    fun updateProviderType(type: ProviderType) {
        val defaults = when (type) {
            ProviderType.OPENAI_COMPATIBLE -> "https://api.openai.com" to "gpt-4o"
            ProviderType.ANTHROPIC -> "https://api.anthropic.com" to "claude-sonnet-4-20250514"
        }
        _state.value = _state.value.copy(
            providerType = type,
            baseUrl = defaults.first,
            model = defaults.second
        )
    }

    fun updateBaseUrl(url: String) { _state.value = _state.value.copy(baseUrl = url) }
    fun updateApiKey(key: String) { _state.value = _state.value.copy(apiKey = key) }
    fun updateModel(model: String) { _state.value = _state.value.copy(model = model) }
    fun updateTemperature(temp: String) { _state.value = _state.value.copy(temperature = temp) }
    fun updateMaxTokens(tokens: String) { _state.value = _state.value.copy(maxTokens = tokens) }
    fun updateSystemPrompt(prompt: String) { _state.value = _state.value.copy(systemPrompt = prompt) }

    fun saveSettings() {
        val state = _state.value
        _state.value = _state.value.copy(isSaving = true, savedSuccessfully = null)

        viewModelScope.launch {
            try {
                val cleanUrl = state.baseUrl.trim().trimEnd('/')
                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                    _state.value = _state.value.copy(isSaving = false, savedSuccessfully = false)
                    return@launch
                }

                val id = "provider_${state.providerType.name.lowercase()}"
                settingsRepository.saveProvider(
                    ProviderConfig(
                        id = id,
                        providerType = state.providerType,
                        name = state.providerType.name,
                        model = state.model.trim(),
                        baseUrl = cleanUrl,
                        temperature = state.temperature.toFloatOrNull() ?: 0.7f,
                        maxTokens = state.maxTokens.toIntOrNull() ?: 4096,
                        systemPrompt = state.systemPrompt.ifBlank { null },
                        isDefault = true
                    )
                )
                if (state.apiKey.isNotBlank()) {
                    settingsRepository.saveApiKey(id, state.apiKey.trim())
                }
                _state.value = _state.value.copy(isSaving = false, savedSuccessfully = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, savedSuccessfully = false)
            }
        }
    }
}
