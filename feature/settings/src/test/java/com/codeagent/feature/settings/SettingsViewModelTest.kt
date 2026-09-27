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
}
