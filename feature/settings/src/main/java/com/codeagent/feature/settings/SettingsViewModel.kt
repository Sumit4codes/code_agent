package com.codeagent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.ai.ModelFetcher
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.model.PopularProvider
import com.codeagent.core.model.PopularProviders
import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import com.codeagent.core.data.sync.CloudSyncManager
import com.codeagent.core.data.sync.GitHubDeviceCodeResponse
import com.codeagent.core.data.sync.GitHubDevicePollResult
import com.codeagent.core.data.sync.NoOpCloudSyncManager
import com.codeagent.core.data.sync.SyncAccountInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUiModel(
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val model: String,
    val isDefault: Boolean,
    val hasApiKey: Boolean,
    val apiKey: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String? = null
)

data class ProviderEditorState(
    val isOpen: Boolean = false,
    val isEditing: Boolean = false,
    val providerId: String = "",
    val name: String = "",
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val isDefault: Boolean = true,
    val availableModels: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val modelFetchError: String? = null,
    val connectionTestSuccess: Boolean? = null,
    val connectionTestMessage: String? = null,
    val selectedTemplateId: String = "openai"
)

enum class SyncAction {
    SYNC,
    RESTORE
}

data class GitHubDeviceAuthState(
    val isAuthorizing: Boolean = false,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val isPolling: Boolean = false,
    val error: String? = null
)

data class SettingsUiState(
    val providers: List<ProviderUiModel> = emptyList(),
    val activeProvider: ProviderUiModel? = null,
    val editorState: ProviderEditorState = ProviderEditorState(),
    val globalSystemPrompt: String = "",
    val globalTemperature: Float = 0.7f,
    val globalMaxTokens: String = "4096",
    val isSaving: Boolean = false,
    val userMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val syncAccount: SyncAccountInfo = SyncAccountInfo(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isConnectDialogOpen: Boolean = false,
    val isPassphrasePromptOpen: Boolean = false,
    val pendingSyncAction: SyncAction? = null,
    val isBackupDialogOpen: Boolean = false,
    val backupExportText: String? = null,
    val deviceAuthState: GitHubDeviceAuthState = GitHubDeviceAuthState(),
    val terminalFontSizeSp: Int = 14
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelFetcher: ModelFetcher,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    private var cachedPassphrase: String? = null
    private var deviceAuthJob: Job? = null

    constructor(
        settingsRepository: SettingsRepository,
        modelFetcher: ModelFetcher,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) : this(settingsRepository, modelFetcher, NoOpCloudSyncManager()) {
        this.ioDispatcher = ioDispatcher
    }

    constructor(
        settingsRepository: SettingsRepository,
        modelFetcher: ModelFetcher,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        cloudSyncManager: CloudSyncManager
    ) : this(settingsRepository, modelFetcher, cloudSyncManager) {
        this.ioDispatcher = ioDispatcher
    }

    private val _state = MutableStateFlow(
        SettingsUiState(
            terminalFontSizeSp = settingsRepository.getTerminalFontSize()
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observeProviders()
        observeSyncAccount()
        observeTerminalFontSize()
    }

    private fun observeTerminalFontSize() {
        viewModelScope.launch {
            settingsRepository.getTerminalFontSizeFlow().collect { sizeSp ->
                _state.value = _state.value.copy(terminalFontSizeSp = sizeSp)
            }
        }
    }

    fun updateTerminalFontSize(newSizeSp: Int) {
        _state.value = _state.value.copy(terminalFontSizeSp = newSizeSp)
        viewModelScope.launch {
            settingsRepository.saveTerminalFontSize(newSizeSp)
        }
    }

    private fun observeSyncAccount() {
        viewModelScope.launch {
            cloudSyncManager.accountInfo.collect { accountInfo ->
                _state.value = _state.value.copy(syncAccount = accountInfo)
            }
        }
    }

    private fun observeProviders() {
        viewModelScope.launch {
            combine(
                settingsRepository.getAllProvidersFlow(),
                settingsRepository.getActiveProviderFlow()
            ) { allConfigs, activeConfig ->
                val uiList = allConfigs.map { config ->
                    val key = settingsRepository.getApiKey(config.id) ?: ""
                    ProviderUiModel(
                        id = config.id,
                        name = config.name,
                        providerType = config.providerType,
                        baseUrl = config.baseUrl,
                        model = config.model,
                        isDefault = config.isDefault,
                        hasApiKey = key.isNotBlank(),
                        apiKey = key,
                        temperature = config.temperature,
                        maxTokens = config.maxTokens,
                        systemPrompt = config.systemPrompt
                    )
                }
                val activeUi = uiList.firstOrNull { it.isDefault } ?: uiList.firstOrNull()
                Pair(uiList, activeUi)
            }.collect { (uiList, activeUi) ->
                _state.value = _state.value.copy(
                    providers = uiList,
                    activeProvider = activeUi,
                    globalTemperature = activeUi?.temperature ?: _state.value.globalTemperature,
                    globalMaxTokens = (activeUi?.maxTokens ?: 4096).toString(),
                    globalSystemPrompt = activeUi?.systemPrompt ?: _state.value.globalSystemPrompt
                )
            }
        }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setActiveProvider(providerId)
                val name = _state.value.providers.firstOrNull { it.id == providerId }?.name ?: providerId
                _state.value = _state.value.copy(
                    userMessage = "Active provider set to $name",
                    isErrorMessage = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    userMessage = "Failed to activate provider: ${e.message}",
                    isErrorMessage = true
                )
            }
        }
    }

    fun quickSelectModel(providerId: String, newModel: String) {
        viewModelScope.launch {
            val provider = settingsRepository.getProvider(providerId) ?: return@launch
            settingsRepository.saveProvider(provider.copy(model = newModel))
            _state.value = _state.value.copy(
                userMessage = "Model set to $newModel",
                isErrorMessage = false
            )
        }
    }

    fun openAddProviderDialog(preset: PopularProvider = PopularProviders.OPENAI) {
        val hasExisting = _state.value.providers.isNotEmpty()
        val templateId = preset.id
        val generatedId = if (preset.isCustom) "custom_${System.currentTimeMillis()}" else preset.id
        // If a provider with preset.id already exists, suffix timestamp to allow multiple instances
        val finalId = if (_state.value.providers.any { it.id == generatedId }) {
            "${preset.id}_${System.currentTimeMillis() % 10000}"
        } else {
            generatedId
        }

        _state.value = _state.value.copy(
            editorState = ProviderEditorState(
                isOpen = true,
                isEditing = false,
                providerId = finalId,
                name = preset.name,
                providerType = preset.providerType,
                baseUrl = preset.defaultBaseUrl,
                apiKey = "",
                model = preset.defaultModel,
                isDefault = !hasExisting,
                availableModels = preset.fallbackModels,
                isFetchingModels = false,
                modelFetchError = null,
                connectionTestSuccess = null,
                connectionTestMessage = null,
                selectedTemplateId = templateId
            )
        )
    }

    fun selectPresetTemplate(preset: PopularProvider) {
        val current = _state.value.editorState
        val generatedId = if (preset.isCustom) "custom_${System.currentTimeMillis()}" else preset.id
        val finalId = if (!current.isEditing && _state.value.providers.any { it.id == generatedId }) {
            "${preset.id}_${System.currentTimeMillis() % 10000}"
        } else if (!current.isEditing) {
            generatedId
        } else {
            current.providerId
        }

        _state.value = _state.value.copy(
            editorState = current.copy(
                providerId = finalId,
                name = if (!current.isEditing) preset.name else current.name,
                providerType = preset.providerType,
                baseUrl = preset.defaultBaseUrl,
                model = preset.defaultModel,
                availableModels = preset.fallbackModels,
                selectedTemplateId = preset.id,
                connectionTestSuccess = null,
                connectionTestMessage = null,
                modelFetchError = null
            )
        )
    }

    fun openEditProviderDialog(provider: ProviderUiModel) {
        viewModelScope.launch {
            val key = settingsRepository.getApiKey(provider.id) ?: ""
            val matchedTemplate = PopularProviders.findById(provider.id)
                ?: PopularProviders.allPopular.firstOrNull { it.defaultBaseUrl == provider.baseUrl }
                ?: PopularProviders.CUSTOM

            val initialModels = matchedTemplate.fallbackModels.toMutableList()
            if (provider.model.isNotBlank() && !initialModels.contains(provider.model)) {
                initialModels.add(0, provider.model)
            }

            _state.value = _state.value.copy(
                editorState = ProviderEditorState(
                    isOpen = true,
                    isEditing = true,
                    providerId = provider.id,
                    name = provider.name,
                    providerType = provider.providerType,
                    baseUrl = provider.baseUrl,
                    apiKey = key,
                    model = provider.model,
                    isDefault = provider.isDefault,
                    availableModels = initialModels,
                    isFetchingModels = false,
                    modelFetchError = null,
                    connectionTestSuccess = null,
                    connectionTestMessage = null,
                    selectedTemplateId = matchedTemplate.id
                )
            )
        }
    }

    fun updateEditorName(name: String) {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(name = name)
        )
    }

    fun updateEditorBaseUrl(url: String) {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(
                baseUrl = url,
                connectionTestSuccess = null,
                connectionTestMessage = null,
                modelFetchError = null
            )
        )
    }

    fun updateEditorApiKey(key: String) {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(
                apiKey = key,
                connectionTestSuccess = null,
                connectionTestMessage = null,
                modelFetchError = null
            )
        )
    }

    fun updateEditorModel(model: String) {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(model = model)
        )
    }

    fun updateEditorProviderType(type: ProviderType) {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(providerType = type)
        )
    }

    fun updateEditorIsDefault(isDefault: Boolean) {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(isDefault = isDefault)
        )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(
            editorState = _state.value.editorState.copy(isOpen = false)
        )
    }

    fun fetchModelsForEditor() {
        val editor = _state.value.editorState
        val cleanUrl = editor.baseUrl.trim().trimEnd('/')
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.value = _state.value.copy(
                editorState = editor.copy(
                    modelFetchError = "Base URL must start with http:// or https://",
                    connectionTestSuccess = false,
                    connectionTestMessage = "Invalid URL"
                )
            )
            return
        }

        _state.value = _state.value.copy(
            editorState = editor.copy(
                isFetchingModels = true,
                modelFetchError = null,
                connectionTestSuccess = null,
                connectionTestMessage = null
            )
        )

        viewModelScope.launch(ioDispatcher) {
            val result = modelFetcher.fetchModels(
                baseUrl = cleanUrl,
                apiKey = editor.apiKey,
                providerType = editor.providerType
            )

            result.fold(
                onSuccess = { models ->
                    val chosenModel = if (models.contains(editor.model)) {
                        editor.model
                    } else if (editor.model.isNotBlank()) {
                        editor.model
                    } else {
                        models.firstOrNull() ?: ""
                    }

                    _state.value = _state.value.copy(
                        editorState = _state.value.editorState.copy(
                            isFetchingModels = false,
                            availableModels = models,
                            model = chosenModel,
                            modelFetchError = null,
                            connectionTestSuccess = true,
                            connectionTestMessage = "Connection verified! Fetched ${models.size} models from endpoint."
                        )
                    )
                },
                onFailure = { error ->
                    val fallback = PopularProviders.findById(editor.selectedTemplateId)?.fallbackModels
                        ?: editor.availableModels

                    _state.value = _state.value.copy(
                        editorState = _state.value.editorState.copy(
                            isFetchingModels = false,
                            availableModels = fallback,
                            modelFetchError = error.message ?: "Failed to fetch models",
                            connectionTestSuccess = false,
                            connectionTestMessage = error.message ?: "Connection test failed"
                        )
                    )
                }
            )
        }
    }

    fun saveProviderFromEditor() {
        val editor = _state.value.editorState
        val trimmedName = editor.name.trim()
        val trimmedUrl = editor.baseUrl.trim().trimEnd('/')

        if (trimmedName.isBlank()) {
            _state.value = _state.value.copy(
                userMessage = "Provider name cannot be empty",
                isErrorMessage = true
            )
            return
        }

        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            _state.value = _state.value.copy(
                userMessage = "Base URL must start with http:// or https://",
                isErrorMessage = true
            )
            return
        }

        val chosenModel = editor.model.trim().ifBlank {
            editor.availableModels.firstOrNull() ?: "gpt-4o"
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                val config = ProviderConfig(
                    id = editor.providerId,
                    providerType = editor.providerType,
                    name = trimmedName,
                    model = chosenModel,
                    baseUrl = trimmedUrl,
                    temperature = _state.value.globalTemperature,
                    maxTokens = _state.value.globalMaxTokens.toIntOrNull() ?: 4096,
                    systemPrompt = _state.value.globalSystemPrompt.ifBlank { null },
                    isDefault = editor.isDefault
                )

                settingsRepository.saveProvider(config, apiKey = editor.apiKey)

                if (editor.isDefault) {
                    settingsRepository.setActiveProvider(config.id)
                }

                _state.value = _state.value.copy(
                    isSaving = false,
                    editorState = editor.copy(isOpen = false),
                    userMessage = "Saved provider '$trimmedName'",
                    isErrorMessage = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    userMessage = "Failed to save provider: ${e.message}",
                    isErrorMessage = true
                )
            }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.deleteProviderById(providerId)
                _state.value = _state.value.copy(
                    userMessage = "Provider deleted",
                    isErrorMessage = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    userMessage = "Failed to delete: ${e.message}",
                    isErrorMessage = true
                )
            }
        }
    }

    fun updateGlobalTemperature(temp: Float) {
        val rounded = Math.round(temp * 100f) / 100f
        _state.value = _state.value.copy(globalTemperature = rounded)
        syncActiveProviderPreferences(temp = rounded)
    }

    fun updateGlobalMaxTokens(tokens: String) {
        _state.value = _state.value.copy(globalMaxTokens = tokens)
        val intVal = tokens.toIntOrNull() ?: 4096
        syncActiveProviderPreferences(tokens = intVal)
    }

    fun updateGlobalSystemPrompt(prompt: String) {
        _state.value = _state.value.copy(globalSystemPrompt = prompt)
        syncActiveProviderPreferences(prompt = prompt)
    }

    private fun syncActiveProviderPreferences(
        temp: Float? = null,
        tokens: Int? = null,
        prompt: String? = null
    ) {
        viewModelScope.launch {
            val active = settingsRepository.getActiveProvider() ?: return@launch
            val updated = active.copy(
                temperature = temp ?: active.temperature,
                maxTokens = tokens ?: active.maxTokens,
                systemPrompt = prompt ?: active.systemPrompt
            )
            settingsRepository.saveProvider(updated)
        }
    }

    fun dismissUserMessage() {
        _state.value = _state.value.copy(userMessage = null)
    }

    fun openConnectSyncDialog() {
        _state.value = _state.value.copy(
            isConnectDialogOpen = true,
            syncError = null,
            deviceAuthState = GitHubDeviceAuthState()
        )
    }

    fun closeConnectSyncDialog() {
        cancelGitHubDeviceFlow()
        _state.value = _state.value.copy(
            isConnectDialogOpen = false,
            syncError = null,
            deviceAuthState = GitHubDeviceAuthState()
        )
    }

    private var activeDeviceCodeResponse: GitHubDeviceCodeResponse? = null
    private var activeDevicePassphrase: String? = null
    private var activeClientId: String? = null

    fun startGitHubDeviceFlow(passphrase: String, clientId: String? = null) {
        if (passphrase.length < 6) {
            _state.value = _state.value.copy(
                syncError = "Encryption passphrase must be at least 6 characters"
            )
            return
        }

        activeDevicePassphrase = passphrase
        activeClientId = clientId

        viewModelScope.launch(ioDispatcher) {
            _state.value = _state.value.copy(
                isSyncing = true,
                syncError = null,
                deviceAuthState = GitHubDeviceAuthState(isAuthorizing = true)
            )

            val codeResult = cloudSyncManager.requestGitHubDeviceCode(clientId)
            codeResult.fold(
                onSuccess = { codeResponse ->
                    activeDeviceCodeResponse = codeResponse
                    _state.value = _state.value.copy(
                        deviceAuthState = GitHubDeviceAuthState(
                            isAuthorizing = true,
                            userCode = codeResponse.userCode,
                            verificationUri = codeResponse.verificationUri,
                            isPolling = true
                        )
                    )

                    deviceAuthJob?.cancel()
                    deviceAuthJob = viewModelScope.launch(ioDispatcher) {
                        val authResult = cloudSyncManager.awaitGitHubDeviceLogin(clientId, codeResponse, passphrase)
                        authResult.fold(
                            onSuccess = { info ->
                                cachedPassphrase = passphrase
                                activeDeviceCodeResponse = null
                                activeDevicePassphrase = null
                                _state.value = _state.value.copy(
                                    isSyncing = false,
                                    isConnectDialogOpen = false,
                                    deviceAuthState = GitHubDeviceAuthState(),
                                    syncError = null,
                                    userMessage = "Connected to GitHub as @${info.username ?: "user"}! Vault synchronized.",
                                    isErrorMessage = false
                                )
                            },
                            onFailure = { error ->
                                val msg = if (error.message?.contains("Tag", ignoreCase = true) == true ||
                                    error.message?.contains("AEAD", ignoreCase = true) == true
                                ) {
                                    "Decryption failed: Incorrect passphrase for the existing vault on GitHub."
                                } else {
                                    error.message ?: "GitHub authorization failed"
                                }
                                _state.value = _state.value.copy(
                                    isSyncing = false,
                                    syncError = msg,
                                    deviceAuthState = _state.value.deviceAuthState.copy(isPolling = false, error = msg)
                                )
                            }
                        )
                    }
                },
                onFailure = { error ->
                    val msg = error.message ?: "Failed to initiate GitHub authorization"
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        syncError = msg,
                        deviceAuthState = GitHubDeviceAuthState(error = msg)
                    )
                }
            )
        }
    }

    fun checkGitHubDeviceAuthNow() {
        val codeResponse = activeDeviceCodeResponse ?: return
        val passphrase = activeDevicePassphrase ?: return
        val clientId = activeClientId

        viewModelScope.launch(ioDispatcher) {
            _state.value = _state.value.copy(
                isSyncing = true,
                deviceAuthState = _state.value.deviceAuthState.copy(isPolling = true, error = null)
            )

            val pollResult = cloudSyncManager.pollGitHubDeviceOnce(clientId, codeResponse.deviceCode)
            when (pollResult) {
                is GitHubDevicePollResult.Success -> {
                    val authResult = cloudSyncManager.connectGitHub(pollResult.accessToken, passphrase)
                    authResult.fold(
                        onSuccess = { info ->
                            cachedPassphrase = passphrase
                            activeDeviceCodeResponse = null
                            activeDevicePassphrase = null
                            deviceAuthJob?.cancel()
                            deviceAuthJob = null
                            _state.value = _state.value.copy(
                                isSyncing = false,
                                isConnectDialogOpen = false,
                                deviceAuthState = GitHubDeviceAuthState(),
                                syncError = null,
                                userMessage = "Connected to GitHub as @${info.username ?: "user"}! Vault synchronized.",
                                isErrorMessage = false
                            )
                        },
                        onFailure = { error ->
                            val msg = if (error.message?.contains("Tag", ignoreCase = true) == true ||
                                error.message?.contains("AEAD", ignoreCase = true) == true
                            ) {
                                "Decryption failed: Incorrect passphrase for the existing vault on GitHub."
                            } else {
                                error.message ?: "Failed to connect to GitHub"
                            }
                            _state.value = _state.value.copy(
                                isSyncing = false,
                                syncError = msg,
                                deviceAuthState = _state.value.deviceAuthState.copy(isPolling = false, error = msg)
                            )
                        }
                    )
                }
                is GitHubDevicePollResult.Pending -> {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        deviceAuthState = _state.value.deviceAuthState.copy(
                            isPolling = true,
                            error = "Authorization pending on GitHub. Make sure you tapped 'Authorize CodeAgent' in your browser."
                        )
                    )
                }
                is GitHubDevicePollResult.SlowDown -> {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        deviceAuthState = _state.value.deviceAuthState.copy(isPolling = true)
                    )
                }
                is GitHubDevicePollResult.Error -> {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        syncError = pollResult.message,
                        deviceAuthState = _state.value.deviceAuthState.copy(isPolling = false, error = pollResult.message)
                    )
                }
            }
        }
    }

    fun cancelGitHubDeviceFlow() {
        deviceAuthJob?.cancel()
        deviceAuthJob = null
        activeDeviceCodeResponse = null
        activeDevicePassphrase = null
        _state.value = _state.value.copy(
            isSyncing = false,
            deviceAuthState = GitHubDeviceAuthState()
        )
    }

    fun connectSync(token: String, passphrase: String) {
        if (token.isBlank()) {
            _state.value = _state.value.copy(syncError = "GitHub Personal Access Token is required")
            return
        }
        if (passphrase.length < 6) {
            _state.value = _state.value.copy(syncError = "Encryption passphrase must be at least 6 characters")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _state.value = _state.value.copy(isSyncing = true, syncError = null)
            val result = cloudSyncManager.connectGitHub(token.trim(), passphrase)
            result.fold(
                onSuccess = { info ->
                    cachedPassphrase = passphrase
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        isConnectDialogOpen = false,
                        syncError = null,
                        userMessage = "Connected to GitHub as @${info.username ?: "user"}! Vault synchronized.",
                        isErrorMessage = false
                    )
                },
                onFailure = { error ->
                    val msg = if (error.message?.contains("Tag", ignoreCase = true) == true ||
                        error.message?.contains("AEAD", ignoreCase = true) == true
                    ) {
                        "Decryption failed: Incorrect passphrase for the existing vault on GitHub."
                    } else {
                        error.message ?: "Failed to connect to GitHub"
                    }
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        syncError = msg
                    )
                }
            )
        }
    }

    fun disconnectSync() {
        viewModelScope.launch(ioDispatcher) {
            cloudSyncManager.logout()
            cachedPassphrase = null
            _state.value = _state.value.copy(
                userMessage = "GitHub Sync disconnected.",
                isErrorMessage = false
            )
        }
    }

    fun triggerCloudSync(passphrase: String? = null) {
        val finalPass = passphrase ?: cachedPassphrase
        if (finalPass.isNullOrBlank()) {
            _state.value = _state.value.copy(
                isPassphrasePromptOpen = true,
                pendingSyncAction = SyncAction.SYNC
            )
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _state.value = _state.value.copy(isSyncing = true, isPassphrasePromptOpen = false)
            val result = cloudSyncManager.syncToGitHub(finalPass)
            result.fold(
                onSuccess = {
                    cachedPassphrase = finalPass
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        userMessage = "Vault encrypted and synced to GitHub Gist!",
                        isErrorMessage = false
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        userMessage = "Sync failed: ${error.message}",
                        isErrorMessage = true
                    )
                }
            )
        }
    }

    fun triggerCloudRestore(passphrase: String? = null) {
        val finalPass = passphrase ?: cachedPassphrase
        if (finalPass.isNullOrBlank()) {
            _state.value = _state.value.copy(
                isPassphrasePromptOpen = true,
                pendingSyncAction = SyncAction.RESTORE
            )
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _state.value = _state.value.copy(isSyncing = true, isPassphrasePromptOpen = false)
            val result = cloudSyncManager.restoreFromGitHub(finalPass)
            result.fold(
                onSuccess = { count ->
                    cachedPassphrase = finalPass
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        userMessage = "Restored $count provider configuration(s) from GitHub Gist!",
                        isErrorMessage = false
                    )
                },
                onFailure = { error ->
                    val msg = if (error.message?.contains("Tag", ignoreCase = true) == true ||
                        error.message?.contains("AEAD", ignoreCase = true) == true
                    ) {
                        "Decryption failed: Incorrect passphrase"
                    } else {
                        error.message ?: "Failed to restore from GitHub"
                    }
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        userMessage = "Restore failed: $msg",
                        isErrorMessage = true
                    )
                }
            )
        }
    }

    fun closePassphrasePrompt() {
        _state.value = _state.value.copy(
            isPassphrasePromptOpen = false,
            pendingSyncAction = null
        )
    }

    fun submitPassphrasePrompt(passphrase: String) {
        val action = _state.value.pendingSyncAction ?: return
        if (action == SyncAction.SYNC) {
            triggerCloudSync(passphrase)
        } else {
            triggerCloudRestore(passphrase)
        }
    }

    fun openBackupDialog() {
        _state.value = _state.value.copy(
            isBackupDialogOpen = true,
            backupExportText = null,
            syncError = null
        )
    }

    fun closeBackupDialog() {
        _state.value = _state.value.copy(
            isBackupDialogOpen = false,
            backupExportText = null,
            syncError = null
        )
    }

    fun exportEncryptedBackup(passphrase: String) {
        if (passphrase.length < 6) {
            _state.value = _state.value.copy(syncError = "Passphrase must be at least 6 characters")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                val jsonText = cloudSyncManager.exportEncryptedPayloadJson(passphrase)
                _state.value = _state.value.copy(
                    backupExportText = jsonText,
                    syncError = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(syncError = "Export failed: ${e.message}")
            }
        }
    }

    fun importEncryptedBackup(payloadJson: String, passphrase: String) {
        if (payloadJson.isBlank()) {
            _state.value = _state.value.copy(syncError = "Backup JSON is required")
            return
        }
        if (passphrase.isBlank()) {
            _state.value = _state.value.copy(syncError = "Passphrase is required")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val result = cloudSyncManager.importEncryptedPayloadJson(payloadJson.trim(), passphrase)
            result.fold(
                onSuccess = { count ->
                    _state.value = _state.value.copy(
                        isBackupDialogOpen = false,
                        backupExportText = null,
                        syncError = null,
                        userMessage = "Successfully imported and decrypted $count provider(s)!",
                        isErrorMessage = false
                    )
                },
                onFailure = { error ->
                    val msg = if (error.message?.contains("Tag", ignoreCase = true) == true ||
                        error.message?.contains("AEAD", ignoreCase = true) == true
                    ) {
                        "Decryption failed: Incorrect passphrase"
                    } else {
                        error.message ?: "Failed to import backup"
                    }
                    _state.value = _state.value.copy(syncError = msg)
                }
            )
        }
    }
}
