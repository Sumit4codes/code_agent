package com.codeagent.core.data

import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val providerConfigDao: ProviderConfigDao,
    private val apiKeyStorage: ApiKeyStorage
) {
    suspend fun getActiveProvider(): ProviderConfig? {
        return providerConfigDao.getDefault()?.toModel()
    }

    fun getActiveProviderFlow(): Flow<ProviderConfig?> =
        providerConfigDao.getDefaultFlow().map { it?.toModel() }

    fun getAllProvidersFlow(): Flow<List<ProviderConfig>> =
        providerConfigDao.getAll().map { list -> list.map { it.toModel() } }

    suspend fun getAllProviders(): List<ProviderConfig> =
        providerConfigDao.getAllList().map { it.toModel() }

    suspend fun getProvider(id: String): ProviderConfig? {
        return providerConfigDao.getById(id)?.toModel()
    }

    suspend fun saveProvider(config: ProviderConfig, apiKey: String? = null) {
        val all = providerConfigDao.getAllList()
        val shouldBeDefault = config.isDefault || all.isEmpty()
        val toSave = config.copy(isDefault = shouldBeDefault)
        providerConfigDao.upsert(toSave.toEntity())
        if (shouldBeDefault) {
            providerConfigDao.setDefault(toSave.id)
        }
        if (apiKey != null) {
            apiKeyStorage.storeKey(toSave.id, apiKey.trim())
        }
    }

    suspend fun setActiveProvider(providerId: String) {
        providerConfigDao.setDefault(providerId)
    }

    suspend fun saveApiKey(providerId: String, apiKey: String) {
        apiKeyStorage.storeKey(providerId, apiKey.trim())
    }

    suspend fun getApiKey(providerId: String): String? {
        return apiKeyStorage.getKey(providerId)
    }

    suspend fun deleteProvider(config: ProviderConfig) {
        deleteProviderById(config.id)
    }

    suspend fun deleteProviderById(providerId: String) {
        val wasDefault = providerConfigDao.getById(providerId)?.isDefault == true
        providerConfigDao.deleteById(providerId)
        apiKeyStorage.removeKey(providerId)
        if (wasDefault) {
            val remaining = providerConfigDao.getAllList()
            if (remaining.isNotEmpty()) {
                providerConfigDao.setDefault(remaining.first().id)
            }
        }
    }
}

fun ProviderConfigEntity.toModel() = ProviderConfig(
    id = id,
    providerType = try { ProviderType.valueOf(providerType) } catch (_: Exception) { ProviderType.OPENAI_COMPATIBLE },
    name = name,
    model = model,
    baseUrl = baseUrl,
    temperature = temperature,
    maxTokens = maxTokens,
    systemPrompt = systemPrompt,
    isDefault = isDefault
)

fun ProviderConfig.toEntity() = ProviderConfigEntity(
    id = id,
    providerType = providerType.name,
    name = name,
    model = model,
    baseUrl = baseUrl,
    temperature = temperature,
    maxTokens = maxTokens,
    systemPrompt = systemPrompt,
    isDefault = isDefault
)
