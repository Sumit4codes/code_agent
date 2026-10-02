package com.codeagent.feature.settings

import com.codeagent.core.ai.ModelFetcher
import com.codeagent.core.data.ApiKeyStorage
import com.codeagent.core.data.ProviderConfigDao
import com.codeagent.core.data.ProviderConfigEntity
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.model.PopularProviders
import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private class TestProviderConfigDao : ProviderConfigDao {
    val configs = MutableStateFlow<List<ProviderConfigEntity>>(emptyList())

    override fun getAll(): Flow<List<ProviderConfigEntity>> = configs
    override suspend fun getAllList(): List<ProviderConfigEntity> = configs.value
    override suspend fun getDefault(): ProviderConfigEntity? = configs.value.firstOrNull { it.isDefault }
    override fun getDefaultFlow(): Flow<ProviderConfigEntity?> = configs.map { it.firstOrNull { it.isDefault } }
    override suspend fun getById(id: String): ProviderConfigEntity? = configs.value.firstOrNull { it.id == id }

    override suspend fun upsert(config: ProviderConfigEntity) {
        val current = configs.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) current[index] = config else current.add(config)
        configs.value = current
    }

    override suspend fun delete(config: ProviderConfigEntity) {
        configs.value = configs.value.filter { it.id != config.id }
    }

    override suspend fun deleteById(id: String) {
        configs.value = configs.value.filter { it.id != id }
    }

    override suspend fun setDefault(providerId: String) {
        configs.value = configs.value.map { it.copy(isDefault = it.id == providerId) }
    }
}

private class TestApiKeyStorage : ApiKeyStorage {
    val keys = mutableMapOf<String, String>()
    override fun storeKey(providerId: String, apiKey: String) { keys[providerId] = apiKey }
    override fun getKey(providerId: String): String? = keys[providerId]
    override fun removeKey(providerId: String) { keys.remove(providerId) }
}

private class FakeModelFetcher : ModelFetcher {
    var responseToReturn: Result<List<String>> = Result.success(listOf("gpt-4o", "gpt-4o-mini", "o3-mini"))

    override suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        providerType: ProviderType
    ): Result<List<String>> = responseToReturn
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: TestProviderConfigDao
    private lateinit var keyStorage: TestApiKeyStorage
    private lateinit var repository: SettingsRepository
    private lateinit var modelFetcher: FakeModelFetcher

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = TestProviderConfigDao()
        keyStorage = TestApiKeyStorage()
        repository = SettingsRepository(dao, keyStorage)
        modelFetcher = FakeModelFetcher()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state observes saved providers and sets active provider`() = runTest {
        repository.saveProvider(
            ProviderConfig(
                id = "openai",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                name = "OpenAI",
                model = "gpt-4o",
                baseUrl = "https://api.openai.com/v1",
                isDefault = true
            ),
            apiKey = "sk-test"
        )

        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.providers.size)
        assertEquals("openai", state.activeProvider?.id)
        assertEquals("gpt-4o", state.activeProvider?.model)
        assertTrue(state.activeProvider?.hasApiKey == true)
    }

    @Test
    fun `openAddProviderDialog pre-fills template values and allows custom`() = runTest {
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher)
        advanceUntilIdle()

        viewModel.openAddProviderDialog(PopularProviders.OPENROUTER)
        var editor = viewModel.state.value.editorState
        assertTrue(editor.isOpen)
        assertEquals("OpenRouter", editor.name)
        assertEquals("https://openrouter.ai/api/v1", editor.baseUrl)
        assertEquals("openrouter", editor.selectedTemplateId)

        // Select Custom template
        viewModel.selectPresetTemplate(PopularProviders.CUSTOM)
        editor = viewModel.state.value.editorState
        assertEquals("custom", editor.selectedTemplateId)
        assertTrue(editor.providerId.startsWith("custom"))
    }

    @Test
    fun `fetchModelsForEditor populates availableModels dynamically`() = runTest {
        modelFetcher.responseToReturn = Result.success(listOf("deepseek-chat", "deepseek-reasoner"))

        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher)
        advanceUntilIdle()

        viewModel.openAddProviderDialog(PopularProviders.DEEPSEEK)
        viewModel.updateEditorApiKey("sk-deepseek")
        viewModel.fetchModelsForEditor()
        advanceUntilIdle()

        val editor = viewModel.state.value.editorState
        assertFalse(editor.isFetchingModels)
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), editor.availableModels)
        assertEquals("deepseek-chat", editor.model)
        assertTrue(editor.connectionTestSuccess == true)
    }

    @Test
    fun `saveProviderFromEditor persists provider and sets active`() = runTest {
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher)
        advanceUntilIdle()

        viewModel.openAddProviderDialog(PopularProviders.GROQ)
        viewModel.updateEditorApiKey("gsk-test")
        viewModel.updateEditorModel("llama-3.3-70b-versatile")
        viewModel.saveProviderFromEditor()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.editorState.isOpen)
        assertEquals(1, viewModel.state.value.providers.size)
        assertEquals("groq", viewModel.state.value.activeProvider?.id)
        assertEquals("llama-3.3-70b-versatile", viewModel.state.value.activeProvider?.model)
        assertEquals("gsk-test", repository.getApiKey("groq"))
    }

    @Test
    fun `setActiveProvider updates default provider`() = runTest {
        repository.saveProvider(
            ProviderConfig(id = "p1", providerType = ProviderType.OPENAI_COMPATIBLE, name = "P1", model = "m1", baseUrl = "https://a.com", isDefault = true)
        )
        repository.saveProvider(
            ProviderConfig(id = "p2", providerType = ProviderType.OPENAI_COMPATIBLE, name = "P2", model = "m2", baseUrl = "https://b.com", isDefault = false)
        )

        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher)
        advanceUntilIdle()

        assertEquals("p1", viewModel.state.value.activeProvider?.id)

        viewModel.setActiveProvider("p2")
        advanceUntilIdle()

        assertEquals("p2", viewModel.state.value.activeProvider?.id)
    }

    @Test
    fun `deleteProvider removes provider from repository`() = runTest {
        repository.saveProvider(
            ProviderConfig(id = "p1", providerType = ProviderType.OPENAI_COMPATIBLE, name = "P1", model = "m1", baseUrl = "https://a.com", isDefault = true)
        )

        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.providers.size)

        viewModel.deleteProvider("p1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.providers.isEmpty())
        assertNull(viewModel.state.value.activeProvider)
    }

    @Test
    fun `connectSync updates account on success`() = runTest {
        val fakeSync = FakeCloudSyncManager()
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher, fakeSync)
        advanceUntilIdle()

        viewModel.openConnectSyncDialog()
        assertTrue(viewModel.state.value.isConnectDialogOpen)

        viewModel.connectSync("ghp_test_token_12345", "my_secret_passphrase")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isConnectDialogOpen)
        assertEquals("octocat", viewModel.state.value.syncAccount.username)
        assertTrue(viewModel.state.value.syncAccount.isConnected)
        assertEquals("ghp_test_token_12345", fakeSync.lastConnectedToken)
        assertEquals("my_secret_passphrase", fakeSync.lastPassphrase)
        assertTrue(viewModel.state.value.userMessage?.contains("@octocat") == true)
    }

    @Test
    fun `connectSync fails with short passphrase`() = runTest {
        val fakeSync = FakeCloudSyncManager()
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher, fakeSync)
        advanceUntilIdle()

        viewModel.connectSync("ghp_valid_token", "123")
        advanceUntilIdle()

        assertEquals("Encryption passphrase must be at least 6 characters", viewModel.state.value.syncError)
        assertNull(fakeSync.lastConnectedToken)
    }

    @Test
    fun `disconnectSync clears account and calls logout`() = runTest {
        val fakeSync = FakeCloudSyncManager()
        fakeSync._account.value = com.codeagent.core.data.sync.SyncAccountInfo(
            provider = com.codeagent.core.data.sync.SyncAccountProvider.GITHUB,
            username = "octocat"
        )
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher, fakeSync)
        advanceUntilIdle()

        assertEquals("octocat", viewModel.state.value.syncAccount.username)

        viewModel.disconnectSync()
        advanceUntilIdle()

        assertTrue(fakeSync.logoutCalled)
        assertFalse(viewModel.state.value.syncAccount.isConnected)
    }

    @Test
    fun `triggerCloudSync and restore use cached passphrase`() = runTest {
        val fakeSync = FakeCloudSyncManager()
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher, fakeSync)
        advanceUntilIdle()

        // Connect sets cached passphrase
        viewModel.connectSync("token", "vault_pass_123")
        advanceUntilIdle()

        // Sync now
        viewModel.triggerCloudSync()
        advanceUntilIdle()
        assertEquals("vault_pass_123", fakeSync.lastPassphrase)
        assertTrue(viewModel.state.value.userMessage?.contains("synced") == true)

        // Restore now
        viewModel.triggerCloudRestore()
        advanceUntilIdle()
        assertEquals("vault_pass_123", fakeSync.lastPassphrase)
        assertTrue(viewModel.state.value.userMessage?.contains("Restored 3") == true)
    }

    @Test
    fun `export and import encrypted backup`() = runTest {
        val fakeSync = FakeCloudSyncManager()
        val viewModel = SettingsViewModel(repository, modelFetcher, testDispatcher, fakeSync)
        advanceUntilIdle()

        viewModel.openBackupDialog()
        assertTrue(viewModel.state.value.isBackupDialogOpen)

        viewModel.exportEncryptedBackup("backup_pass_123")
        advanceUntilIdle()

        assertEquals("{\"version\":1,\"ciphertextBase64\":\"abc\"}", viewModel.state.value.backupExportText)

        viewModel.importEncryptedBackup("{\"version\":1}", "backup_pass_123")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isBackupDialogOpen)
        assertTrue(viewModel.state.value.userMessage?.contains("imported and decrypted 2", ignoreCase = true) == true)
    }
}

private class FakeCloudSyncManager : com.codeagent.core.data.sync.CloudSyncManager {
    val _account = MutableStateFlow(com.codeagent.core.data.sync.SyncAccountInfo())
    override val accountInfo: StateFlow<com.codeagent.core.data.sync.SyncAccountInfo> = _account

    var connectResult: Result<com.codeagent.core.data.sync.SyncAccountInfo> = Result.success(
        com.codeagent.core.data.sync.SyncAccountInfo(
            provider = com.codeagent.core.data.sync.SyncAccountProvider.GITHUB,
            username = "octocat",
            avatarUrl = "https://github.com/octocat.png",
            syncTargetId = "gist_12345"
        )
    )
    var syncResult: Result<com.codeagent.core.data.sync.SyncAccountInfo> = Result.success(
        com.codeagent.core.data.sync.SyncAccountInfo(
            provider = com.codeagent.core.data.sync.SyncAccountProvider.GITHUB,
            username = "octocat",
            lastSyncedAt = 123456789L,
            syncTargetId = "gist_12345"
        )
    )
    var restoreResult: Result<Int> = Result.success(3)
    var exportPayloadText: String = "{\"version\":1,\"ciphertextBase64\":\"abc\"}"
    var importResult: Result<Int> = Result.success(2)

    var lastConnectedToken: String? = null
    var lastPassphrase: String? = null
    var logoutCalled: Boolean = false

    override suspend fun createLocalVault(): com.codeagent.core.data.sync.SyncVault = com.codeagent.core.data.sync.SyncVault()
    override suspend fun restoreVault(vault: com.codeagent.core.data.sync.SyncVault): Int = 0
    override suspend fun exportEncryptedPayloadJson(passphrase: String): String {
        lastPassphrase = passphrase
        return exportPayloadText
    }
    override suspend fun importEncryptedPayloadJson(payloadJson: String, passphrase: String): Result<Int> {
        lastPassphrase = passphrase
        return importResult
    }
    override suspend fun connectGitHub(token: String, passphrase: String): Result<com.codeagent.core.data.sync.SyncAccountInfo> {
        lastConnectedToken = token
        lastPassphrase = passphrase
        if (connectResult.isSuccess) {
            _account.value = connectResult.getOrThrow()
        }
        return connectResult
    }
    override suspend fun syncToGitHub(passphrase: String): Result<com.codeagent.core.data.sync.SyncAccountInfo> {
        lastPassphrase = passphrase
        if (syncResult.isSuccess) {
            _account.value = syncResult.getOrThrow()
        }
        return syncResult
    }
    override suspend fun restoreFromGitHub(passphrase: String): Result<Int> {
        lastPassphrase = passphrase
        return restoreResult
    }
    override fun logout() {
        logoutCalled = true
        _account.value = com.codeagent.core.data.sync.SyncAccountInfo()
    }
}
