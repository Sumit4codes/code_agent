package com.codeagent.core.data.sync

import com.codeagent.core.model.ProviderConfig
import kotlinx.serialization.Serializable

@Serializable
data class SyncVault(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val items: List<SyncProviderItem> = emptyList()
)

@Serializable
data class SyncProviderItem(
    val config: ProviderConfig,
    val apiKey: String? = null
)

@Serializable
data class EncryptedSyncPayload(
    val version: Int = 1,
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 100_000,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class SyncAccountProvider {
    GITHUB,
    GOOGLE_DRIVE,
    LOCAL_BACKUP,
    NONE
}

@Serializable
data class SyncAccountInfo(
    val provider: SyncAccountProvider = SyncAccountProvider.NONE,
    val username: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val lastSyncedAt: Long? = null,
    val syncTargetId: String? = null
) {
    val isConnected: Boolean get() = provider != SyncAccountProvider.NONE && !username.isNullOrBlank()
}
