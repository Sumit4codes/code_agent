package com.koda.dev.core.data

interface ApiKeyStorage {
    fun storeKey(providerId: String, apiKey: String)
    fun getKey(providerId: String): String?
    fun removeKey(providerId: String)
}
