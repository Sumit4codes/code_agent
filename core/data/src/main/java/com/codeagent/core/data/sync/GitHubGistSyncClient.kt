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
}
