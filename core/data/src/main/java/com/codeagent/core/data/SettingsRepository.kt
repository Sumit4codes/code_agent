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

    suspend fun getProvider(id: String): ProviderConfig? {
        return providerConfigDao.getById(id)?.toModel()
    }

    suspend fun saveProvider(config: ProviderConfig) {
        providerConfigDao.upsert(config.toEntity())
    }

    suspend fun saveApiKey(providerId: String, apiKey: String) {
        apiKeyStorage.storeKey(providerId, apiKey)
    }

    suspend fun getApiKey(providerId: String): String? {
        return apiKeyStorage.getKey(providerId)
    }

    suspend fun deleteProvider(config: ProviderConfig) {
        providerConfigDao.delete(config.toEntity())
        apiKeyStorage.removeKey(config.id)
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
