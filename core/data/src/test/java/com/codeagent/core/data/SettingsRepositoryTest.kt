package com.codeagent.core.data

import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakeApiKeyStorage : ApiKeyStorage {
    private val keys = mutableMapOf<String, String>()

    override fun storeKey(providerId: String, apiKey: String) {
        keys[providerId] = apiKey
    }

    override fun getKey(providerId: String): String? = keys[providerId]

    override fun removeKey(providerId: String) {
        keys.remove(providerId)
    }
}

class FakeProviderConfigDao : ProviderConfigDao {
    private val configs = MutableStateFlow<List<ProviderConfigEntity>>(emptyList())

    override fun getAll(): Flow<List<ProviderConfigEntity>> = configs

    override suspend fun getDefault(): ProviderConfigEntity? =
        configs.value.firstOrNull { it.isDefault }

    override fun getDefaultFlow(): Flow<ProviderConfigEntity?> =
        configs.map { list -> list.firstOrNull { it.isDefault } }

    override suspend fun getById(id: String): ProviderConfigEntity? =
        configs.value.firstOrNull { it.id == id }

    override suspend fun upsert(config: ProviderConfigEntity) {
        val current = configs.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(config)
        }
        configs.value = current
    }

    override suspend fun delete(config: ProviderConfigEntity) {
        configs.value = configs.value.filter { it.id != config.id }
    }

    override suspend fun getAllList(): List<ProviderConfigEntity> = configs.value

    override suspend fun deleteById(id: String) {
        configs.value = configs.value.filter { it.id != id }
    }

    override suspend fun setDefault(providerId: String) {
        configs.value = configs.value.map {
            it.copy(isDefault = (it.id == providerId))
        }
    }
}

class SettingsRepositoryTest {

    private lateinit var dao: FakeProviderConfigDao
    private lateinit var keyStorage: FakeApiKeyStorage
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        dao = FakeProviderConfigDao()
        keyStorage = FakeApiKeyStorage()
        repository = SettingsRepository(dao, keyStorage)
    }

    @Test
    fun `saveProvider and getActiveProvider return default config`() = runTest {
        val config = ProviderConfig(
            id = "p1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "OpenAI",
            model = "gpt-4o",
            baseUrl = "https://api.openai.com/v1",
            isDefault = true
        )

        repository.saveProvider(config)

        val active = repository.getActiveProvider()
        assertNotNull(active)
        assertEquals("p1", active?.id)
        assertEquals("gpt-4o", active?.model)

        val activeFlow = repository.getActiveProviderFlow().first()
        assertEquals("p1", activeFlow?.id)
    }

    @Test
    fun `saveApiKey and getApiKey correctly store and retrieve keys`() = runTest {
        repository.saveApiKey("p1", "sk-test-key-12345")
        val key = repository.getApiKey("p1")
        assertEquals("sk-test-key-12345", key)
    }

    @Test
    fun `deleteProvider removes config and api key`() = runTest {
        val config = ProviderConfig(
            id = "p2",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "Groq",
            model = "llama-3",
            baseUrl = "https://api.groq.com/openai/v1",
            isDefault = true
        )
        repository.saveProvider(config)
        repository.saveApiKey("p2", "gsk-secret")

        repository.deleteProvider(config)

        assertNull(repository.getProvider("p2"))
        assertNull(repository.getApiKey("p2"))
        assertNull(repository.getActiveProvider())
    }

    @Test
    fun `multiple providers can be stored and active provider switched`() = runTest {
        val p1 = ProviderConfig(
            id = "openai",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "OpenAI",
            model = "gpt-4o",
            baseUrl = "https://api.openai.com/v1",
            isDefault = true
        )
        val p2 = ProviderConfig(
            id = "deepseek",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "DeepSeek",
            model = "deepseek-chat",
            baseUrl = "https://api.deepseek.com/v1",
            isDefault = false
        )

        repository.saveProvider(p1, "sk-openai")
        repository.saveProvider(p2, "sk-deepseek")

        assertEquals(2, repository.getAllProviders().size)
        assertEquals("openai", repository.getActiveProvider()?.id)
        assertEquals("sk-openai", repository.getApiKey("openai"))
        assertEquals("sk-deepseek", repository.getApiKey("deepseek"))

        // Switch active provider
        repository.setActiveProvider("deepseek")
        assertEquals("deepseek", repository.getActiveProvider()?.id)

        // Delete active provider and verify next becomes active
        repository.deleteProviderById("deepseek")
        assertEquals(1, repository.getAllProviders().size)
        assertEquals("openai", repository.getActiveProvider()?.id)
    }
}
