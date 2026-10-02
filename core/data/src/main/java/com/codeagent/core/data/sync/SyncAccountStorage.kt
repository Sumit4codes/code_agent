package com.codeagent.core.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncAccountStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "sync_secure_account",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun saveToken(token: String) {
        prefs.edit().putString("sync_token", token).apply()
    }

    fun getToken(): String? = prefs.getString("sync_token", null)

    fun saveAccountInfo(info: SyncAccountInfo) {
        val serialized = json.encodeToString(SyncAccountInfo.serializer(), info)
        prefs.edit().putString("sync_info", serialized).apply()
    }

    fun getAccountInfo(): SyncAccountInfo? {
        val raw = prefs.getString("sync_info", null) ?: return null
        return try {
            json.decodeFromString(SyncAccountInfo.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
