package com.codeagent.core.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubGistSyncClient @Inject constructor() {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val DEFAULT_CLIENT_ID = "Ov23lit7n9oR2mYw8x4z"
        private const val GIST_FILENAME = "codeagent_encrypted_vault.json"
        private const val GIST_DESCRIPTION = "CodeAgent Encrypted Vault (Zero-Knowledge E2EE)"
    }

    suspend fun getProfile(token: String): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("GitHub API error: ${response.code} ${response.message}"))
                }
                val body = response.body.string()
                val obj = json.parseToJsonElement(body).jsonObject
                val login = obj["login"]?.jsonPrimitive?.content ?: "GitHub User"
                val avatar = obj["avatar_url"]?.jsonPrimitive?.content
                Result.success(Pair(login, avatar))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findVaultGistId(token: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/gists?per_page=100")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("GitHub API error: ${response.code}"))
                }
                val body = response.body.string()
                val gists = json.parseToJsonElement(body)
                if (gists !is kotlinx.serialization.json.JsonArray) {
                    return@withContext Result.success(null)
                }

                for (item in gists) {
                    val obj = item.jsonObject
                    val files = obj["files"]?.jsonObject ?: continue
                    if (files.containsKey(GIST_FILENAME)) {
                        val id = obj["id"]?.jsonPrimitive?.content
                        return@withContext Result.success(id)
                    }
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadVault(
        token: String,
        encryptedPayloadJson: String,
        existingGistId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBodyJson = buildJsonObject {
                put("description", GIST_DESCRIPTION)
                if (existingGistId == null) {
                    put("public", false)
                }
                putJsonObject("files") {
                    putJsonObject(GIST_FILENAME) {
                        put("content", encryptedPayloadJson)
                    }
                }
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestBodyJson.toRequestBody(mediaType)

            val request = if (!existingGistId.isNullOrBlank()) {
                Request.Builder()
                    .url("https://api.github.com/gists/$existingGistId")
                    .patch(requestBody)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
            } else {
                Request.Builder()
                    .url("https://api.github.com/gists")
                    .post(requestBody)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
            }

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to upload vault: ${response.code} ${response.message}"))
                }
                val body = response.body.string()
                val obj = json.parseToJsonElement(body).jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@withContext Result.failure(IOException("Missing gist ID"))
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadVault(token: String, gistId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/gists/$gistId")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to download vault: ${response.code}"))
                }
                val body = response.body.string()
                val obj = json.parseToJsonElement(body).jsonObject
                val files = obj["files"]?.jsonObject ?: return@withContext Result.failure(IOException("No files in gist"))
                val vaultFile = files[GIST_FILENAME]?.jsonObject ?: return@withContext Result.failure(IOException("Vault file not found in gist"))
                val content = vaultFile["content"]?.jsonPrimitive?.content ?: return@withContext Result.failure(IOException("Empty vault content"))
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestDeviceCode(clientId: String = DEFAULT_CLIENT_ID): Result<GitHubDeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", "gist")
                .build()

            val request = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                val obj = json.parseToJsonElement(body).jsonObject

                if (!response.isSuccessful || obj.containsKey("error")) {
                    val desc = obj["error_description"]?.jsonPrimitive?.content
                        ?: obj["error"]?.jsonPrimitive?.content
                        ?: "Failed to initialize device authorization (${response.code})"
                    return@withContext Result.failure(IOException(desc))
                }

                val deviceCode = obj["device_code"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(IOException("Missing device_code"))
                val userCode = obj["user_code"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(IOException("Missing user_code"))
                val verificationUri = obj["verification_uri"]?.jsonPrimitive?.content
                    ?: "https://github.com/login/device"
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 900
                val interval = obj["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5

                Result.success(
                    GitHubDeviceCodeResponse(
                        deviceCode = deviceCode,
                        userCode = userCode,
                        verificationUri = verificationUri,
                        expiresInSeconds = expiresIn,
                        intervalSeconds = interval
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollDeviceToken(clientId: String = DEFAULT_CLIENT_ID, deviceCode: String): GitHubDevicePollResult = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()

            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                val obj = json.parseToJsonElement(body).jsonObject

                if (obj.containsKey("access_token")) {
                    val token = obj["access_token"]?.jsonPrimitive?.content ?: ""
                    return@withContext GitHubDevicePollResult.Success(token)
                }

                val error = obj["error"]?.jsonPrimitive?.content ?: "unknown_error"
                when (error) {
                    "authorization_pending" -> GitHubDevicePollResult.Pending
                    "slow_down" -> {
                        val newInterval = (obj["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5) + 5
                        GitHubDevicePollResult.SlowDown(newInterval)
                    }
                    "expired_token" -> GitHubDevicePollResult.Error("The verification code has expired. Please try again.")
                    "access_denied" -> GitHubDevicePollResult.Error("Authorization was cancelled on GitHub.")
                    else -> {
                        val desc = obj["error_description"]?.jsonPrimitive?.content ?: error
                        GitHubDevicePollResult.Error(desc)
                    }
                }
            }
        } catch (e: Exception) {
            GitHubDevicePollResult.Error(e.message ?: "Network error during authorization")
        }
    }
}
