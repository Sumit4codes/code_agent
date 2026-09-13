package com.codeagent.core.data

interface ApiKeyStorage {
    fun storeKey(providerId: String, apiKey: String)
    fun getKey(providerId: String): String?
    fun removeKey(providerId: String)
}
