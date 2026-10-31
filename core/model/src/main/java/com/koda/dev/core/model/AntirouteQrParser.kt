package com.koda.dev.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AntirouteConnectionConfig(
    val baseUrl: String,
    val apiKey: String = AntirouteQrParser.DEFAULT_API_KEY,
    val model: String = AntirouteQrParser.DEFAULT_MODEL,
    val name: String = AntirouteQrParser.DEFAULT_NAME
)

object AntirouteQrParser {
    const val DEFAULT_API_KEY = "sk-antigravity-claude"
    const val DEFAULT_MODEL = "gemini-3.8-flash-high"
    const val DEFAULT_NAME = "Antiroute Tunnel"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawText: String): Result<AntirouteConnectionConfig> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Scanned QR code is empty"))
        }

        // 1. JSON Payload format
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val jsonResult = parseJson(trimmed)
            if (jsonResult != null) return Result.success(jsonResult)
        }

        // 2. Custom antiroute:// URI scheme
        if (trimmed.startsWith("antiroute://", ignoreCase = true)) {
            val uriResult = parseAntirouteUri(trimmed)
            if (uriResult != null) return Result.success(uriResult)
        }

        // 3. Key-Value format (e.g. url=...\nkey=...)
        if (trimmed.contains("url=") || trimmed.contains("baseUrl=") || trimmed.contains("endpoint=")) {
            val kvResult = parseKeyValues(trimmed)
            if (kvResult != null) return Result.success(kvResult)
        }

        // 4. Standard HTTP / HTTPS URL
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val urlResult = parseHttpUrl(trimmed)
            if (urlResult != null) return Result.success(urlResult)
        }

        return Result.failure(IllegalArgumentException("Unrecognized Antiroute QR code format: $trimmed"))
    }

    private fun parseJson(text: String): AntirouteConnectionConfig? {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val url = root["baseUrl"]?.jsonPrimitive?.content
                ?: root["url"]?.jsonPrimitive?.content
                ?: root["host"]?.jsonPrimitive?.content
                ?: return null

            val apiKey = root["apiKey"]?.jsonPrimitive?.content
                ?: root["key"]?.jsonPrimitive?.content
                ?: DEFAULT_API_KEY

            val model = root["model"]?.jsonPrimitive?.content
                ?: root["defaultModel"]?.jsonPrimitive?.content
                ?: DEFAULT_MODEL

            val name = root["name"]?.jsonPrimitive?.content ?: DEFAULT_NAME

            AntirouteConnectionConfig(
                baseUrl = normalizeBaseUrl(url),
                apiKey = apiKey.ifBlank { DEFAULT_API_KEY },
                model = model.ifBlank { DEFAULT_MODEL },
                name = name.ifBlank { DEFAULT_NAME }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAntirouteUri(uriString: String): AntirouteConnectionConfig? {
        return try {
            val withoutScheme = uriString.substringAfter("antiroute://").trim()
            if (withoutScheme.startsWith("http://") || withoutScheme.startsWith("https://")) {
                return parseHttpUrl(withoutScheme)
            }

            // Could be antiroute://connect?url=...&key=...
            val queryPart = withoutScheme.substringAfter("?", "")
            if (queryPart.isNotEmpty()) {
                val params = parseQueryParams(queryPart)
                val url = params["url"] ?: params["baseUrl"] ?: params["host"]
                if (url != null) {
                    val key = params["key"] ?: params["apiKey"] ?: DEFAULT_API_KEY
                    val model = params["model"] ?: DEFAULT_MODEL
                    val name = params["name"] ?: DEFAULT_NAME
                    return AntirouteConnectionConfig(
                        baseUrl = normalizeBaseUrl(url),
                        apiKey = key.ifBlank { DEFAULT_API_KEY },
                        model = model.ifBlank { DEFAULT_MODEL },
                        name = name.ifBlank { DEFAULT_NAME }
                    )
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseKeyValues(text: String): AntirouteConnectionConfig? {
        return try {
            val lines = text.lines()
            val map = mutableMapOf<String, String>()
            for (line in lines) {
                if (line.contains("=")) {
                    val k = line.substringBefore("=").trim().lowercase()
                    val v = line.substringAfter("=").trim()
                    map[k] = v
                }
            }
            val url = map["url"] ?: map["baseurl"] ?: map["endpoint"] ?: map["host"] ?: return null
            val key = map["apikey"] ?: map["key"] ?: DEFAULT_API_KEY
            val model = map["model"] ?: DEFAULT_MODEL
            val name = map["name"] ?: DEFAULT_NAME

            AntirouteConnectionConfig(
                baseUrl = normalizeBaseUrl(url),
                apiKey = key.ifBlank { DEFAULT_API_KEY },
                model = model.ifBlank { DEFAULT_MODEL },
                name = name.ifBlank { DEFAULT_NAME }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHttpUrl(urlString: String): AntirouteConnectionConfig? {
        return try {
            val urlWithoutQuery = urlString.substringBefore("?")
            val queryPart = if (urlString.contains("?")) urlString.substringAfter("?") else ""
            val params = if (queryPart.isNotEmpty()) parseQueryParams(queryPart) else emptyMap()

            val apiKey = params["apiKey"] ?: params["key"] ?: DEFAULT_API_KEY
            val model = params["model"] ?: DEFAULT_MODEL
            val name = params["name"] ?: DEFAULT_NAME

            AntirouteConnectionConfig(
                baseUrl = normalizeBaseUrl(urlWithoutQuery),
                apiKey = apiKey.ifBlank { DEFAULT_API_KEY },
                model = model.ifBlank { DEFAULT_MODEL },
                name = name.ifBlank { DEFAULT_NAME }
            )
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        // Strip endpoint suffixes if user/tool scanned full endpoint URL
        clean = clean.removeSuffix("/chat/completions")
            .removeSuffix("/models")
            .removeSuffix("/setup")
            .trimEnd('/')

        // Ensure clean ends with /v1
        return if (clean.endsWith("/v1")) {
            clean
        } else {
            "$clean/v1"
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            if (pair.contains("=")) {
                val k = pair.substringBefore("=").trim()
                val v = pair.substringAfter("=").trim()
                result[k] = v
            }
        }
        return result
    }
}
