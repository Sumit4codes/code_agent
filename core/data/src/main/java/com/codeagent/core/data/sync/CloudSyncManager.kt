package com.codeagent.core.data.sync

import com.codeagent.core.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface CloudSyncManager {
    val accountInfo: StateFlow<SyncAccountInfo>
    suspend fun createLocalVault(): SyncVault
    suspend fun restoreVault(vault: SyncVault): Int
    suspend fun exportEncryptedPayloadJson(passphrase: String): String
    suspend fun importEncryptedPayloadJson(payloadJson: String, passphrase: String): Result<Int>
    suspend fun connectGitHub(token: String, passphrase: String): Result<SyncAccountInfo>
    suspend fun syncToGitHub(passphrase: String): Result<SyncAccountInfo>
    suspend fun restoreFromGitHub(passphrase: String): Result<Int>
    fun logout()
}

@Singleton
class DefaultCloudSyncManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gitHubGistSyncClient: GitHubGistSyncClient,
    private val syncAccountStorage: SyncAccountStorage
) : CloudSyncManager {
    private val _accountInfo = MutableStateFlow(
        try { syncAccountStorage.getAccountInfo() ?: SyncAccountInfo() } catch (_: Exception) { SyncAccountInfo() }
    )
    override val accountInfo: StateFlow<SyncAccountInfo> = _accountInfo.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun createLocalVault(): SyncVault = withContext(Dispatchers.IO) {
        val providers = settingsRepository.getAllProviders()
        val items = providers.map { provider ->
            val key = settingsRepository.getApiKey(provider.id)
            SyncProviderItem(config = provider, apiKey = key)
        }
        SyncVault(items = items)
    }

    override suspend fun restoreVault(vault: SyncVault): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (item in vault.items) {
            settingsRepository.saveProvider(item.config, item.apiKey)
            count++
        }
        count
    }

    override suspend fun exportEncryptedPayloadJson(passphrase: String): String = withContext(Dispatchers.IO) {
        val vault = createLocalVault()
        val encrypted = VaultCrypto.encryptVault(vault, passphrase)
        json.encodeToString(EncryptedSyncPayload.serializer(), encrypted)
    }

    override suspend fun importEncryptedPayloadJson(payloadJson: String, passphrase: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val payload = json.decodeFromString(EncryptedSyncPayload.serializer(), payloadJson)
            val vault = VaultCrypto.decryptVault(payload, passphrase)
            val count = restoreVault(vault)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun connectGitHub(token: String, passphrase: String): Result<SyncAccountInfo> = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        val profileRes = gitHubGistSyncClient.getProfile(trimmedToken)
        if (profileRes.isFailure) {
            return@withContext Result.failure(profileRes.exceptionOrNull() ?: Exception("Failed to authenticate with GitHub"))
        }
        val (username, avatarUrl) = profileRes.getOrThrow()

        syncAccountStorage.saveToken(trimmedToken)

        // Check if an existing vault already exists on GitHub
        val gistIdRes = gitHubGistSyncClient.findVaultGistId(trimmedToken)
        val existingGistId = gistIdRes.getOrNull()

        val info = SyncAccountInfo(
            provider = SyncAccountProvider.GITHUB,
            username = username,
            avatarUrl = avatarUrl,
            lastSyncedAt = null,
            syncTargetId = existingGistId
        )
        syncAccountStorage.saveAccountInfo(info)
        _accountInfo.value = info

        // If an existing vault exists on GitHub, restore it!
        // If none exists, upload local vault!
        if (existingGistId != null) {
            val restoreRes = restoreFromGitHub(passphrase)
            if (restoreRes.isFailure) {
                // Return failure if passphrase was wrong so user knows immediately
                return@withContext Result.failure(restoreRes.exceptionOrNull() ?: Exception("Decryption failed"))
            }
        } else {
            val syncRes = syncToGitHub(passphrase)
            if (syncRes.isFailure) {
                return@withContext Result.failure(syncRes.exceptionOrNull() ?: Exception("Failed to upload vault"))
            }
        }

        Result.success(_accountInfo.value)
    }

    override suspend fun syncToGitHub(passphrase: String): Result<SyncAccountInfo> = withContext(Dispatchers.IO) {
        val token = syncAccountStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("No GitHub account connected"))

        val payloadJson = exportEncryptedPayloadJson(passphrase)
        val currentInfo = _accountInfo.value
        val uploadRes = gitHubGistSyncClient.uploadVault(token, payloadJson, currentInfo.syncTargetId)

        if (uploadRes.isFailure) {
            return@withContext Result.failure(uploadRes.exceptionOrNull() ?: Exception("Failed to sync to GitHub"))
        }

        val gistId = uploadRes.getOrThrow()
        val updatedInfo = currentInfo.copy(
            lastSyncedAt = System.currentTimeMillis(),
            syncTargetId = gistId
        )
        syncAccountStorage.saveAccountInfo(updatedInfo)
        _accountInfo.value = updatedInfo
        Result.success(updatedInfo)
    }

    override suspend fun restoreFromGitHub(passphrase: String): Result<Int> = withContext(Dispatchers.IO) {
        val token = syncAccountStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("No GitHub account connected"))

        var gistId = _accountInfo.value.syncTargetId
        if (gistId.isNullOrBlank()) {
            val findRes = gitHubGistSyncClient.findVaultGistId(token)
            gistId = findRes.getOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No encrypted vault found on your GitHub account"))
        }

        val downloadRes = gitHubGistSyncClient.downloadVault(token, gistId)
        if (downloadRes.isFailure) {
            return@withContext Result.failure(downloadRes.exceptionOrNull() ?: Exception("Failed to download vault from GitHub"))
        }

        val payloadJson = downloadRes.getOrThrow()
        val importRes = importEncryptedPayloadJson(payloadJson, passphrase)
        if (importRes.isSuccess) {
            val updatedInfo = _accountInfo.value.copy(
                lastSyncedAt = System.currentTimeMillis(),
                syncTargetId = gistId
            )
            syncAccountStorage.saveAccountInfo(updatedInfo)
            _accountInfo.value = updatedInfo
        }
        importRes
    }

    override fun logout() {
        syncAccountStorage.clear()
        _accountInfo.value = SyncAccountInfo()
    }
}

class NoOpCloudSyncManager : CloudSyncManager {
    override val accountInfo: StateFlow<SyncAccountInfo> = MutableStateFlow(SyncAccountInfo()).asStateFlow()
    override suspend fun createLocalVault(): SyncVault = SyncVault()
    override suspend fun restoreVault(vault: SyncVault): Int = 0
    override suspend fun exportEncryptedPayloadJson(passphrase: String): String = "{}"
    override suspend fun importEncryptedPayloadJson(payloadJson: String, passphrase: String): Result<Int> = Result.success(0)
    override suspend fun connectGitHub(token: String, passphrase: String): Result<SyncAccountInfo> = Result.success(SyncAccountInfo())
    override suspend fun syncToGitHub(passphrase: String): Result<SyncAccountInfo> = Result.success(SyncAccountInfo())
    override suspend fun restoreFromGitHub(passphrase: String): Result<Int> = Result.success(0)
    override fun logout() {}
}
