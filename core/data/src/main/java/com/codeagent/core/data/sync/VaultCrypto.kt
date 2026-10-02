package com.codeagent.core.data.sync

import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val secureRandom = SecureRandom()

    fun encryptVault(vault: SyncVault, passphrase: String): EncryptedSyncPayload {
        require(passphrase.isNotBlank()) { "Sync passphrase must not be blank" }

        val serialized = json.encodeToString(SyncVault.serializer(), vault)
        val plaintextBytes = serialized.toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val ciphertext = cipher.doFinal(plaintextBytes)

        return EncryptedSyncPayload(
            version = 1,
            algorithm = "AES-256-GCM",
            kdf = KDF_ALGORITHM,
            iterations = ITERATIONS,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun decryptVault(payload: EncryptedSyncPayload, passphrase: String): SyncVault {
        require(passphrase.isNotBlank()) { "Sync passphrase must not be blank" }

        val salt = Base64.getDecoder().decode(payload.saltBase64)
        val iv = Base64.getDecoder().decode(payload.ivBase64)
        val ciphertext = Base64.getDecoder().decode(payload.ciphertextBase64)

        val secretKey = deriveKey(passphrase, salt, payload.iterations)

        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        val jsonString = String(decryptedBytes, Charsets.UTF_8)

        return json.decodeFromString(SyncVault.serializer(), jsonString)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
