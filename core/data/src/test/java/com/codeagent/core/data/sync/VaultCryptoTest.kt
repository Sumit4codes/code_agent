package com.codeagent.core.data.sync

import com.codeagent.core.model.ProviderConfig
import com.codeagent.core.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class VaultCryptoTest {

    @Test
    fun encryptAndDecrypt_roundTripSucceeds() {
        val originalVault = SyncVault(
            version = 1,
            items = listOf(
                SyncProviderItem(
                    config = ProviderConfig(
                        id = "provider-1",
                        providerType = ProviderType.OPENAI_COMPATIBLE,
                        name = "DeepSeek Production",
                        model = "deepseek-coder",
                        baseUrl = "https://api.deepseek.com/v1",
                        temperature = 0.5f,
                        isDefault = true
                    ),
                    apiKey = "sk-deepseek-secret-12345"
                ),
                SyncProviderItem(
                    config = ProviderConfig(
                        id = "provider-2",
                        providerType = ProviderType.ANTHROPIC,
                        name = "Claude 3.5 Sonnet",
                        model = "claude-3-5-sonnet",
                        baseUrl = "https://api.anthropic.com/v1"
                    ),
                    apiKey = "sk-ant-api03-very-secret-key"
                )
            )
        )

        val passphrase = "my-super-secret-sync-passphrase-2026!"
        val encrypted = VaultCrypto.encryptVault(originalVault, passphrase)

        assertNotNull(encrypted.saltBase64)
        assertNotNull(encrypted.ivBase64)
        assertNotNull(encrypted.ciphertextBase64)
        assertEquals("AES-256-GCM", encrypted.algorithm)
        assertEquals(100_000, encrypted.iterations)

        // Ensure ciphertext does NOT contain plain API keys
        val ciphertextString = String(java.util.Base64.getDecoder().decode(encrypted.ciphertextBase64))
        assertTrue(!ciphertextString.contains("sk-deepseek-secret-12345"))
        assertTrue(!ciphertextString.contains("sk-ant-api03-very-secret-key"))

        // Decrypt
        val decrypted = VaultCrypto.decryptVault(encrypted, passphrase)

        assertEquals(2, decrypted.items.size)
        assertEquals("provider-1", decrypted.items[0].config.id)
        assertEquals("sk-deepseek-secret-12345", decrypted.items[0].apiKey)
        assertEquals("provider-2", decrypted.items[1].config.id)
        assertEquals("sk-ant-api03-very-secret-key", decrypted.items[1].apiKey)
    }

    @Test(expected = Exception::class)
    fun decrypt_withWrongPassphrase_fails() {
        val vault = SyncVault(
            items = listOf(
                SyncProviderItem(
                    config = ProviderConfig(
                        id = "test-1",
                        providerType = ProviderType.OPENAI_COMPATIBLE,
                        name = "Test",
                        model = "gpt-4o",
                        baseUrl = "https://api.openai.com/v1"
                    ),
                    apiKey = "sk-test"
                )
            )
        )

        val encrypted = VaultCrypto.encryptVault(vault, "correct-passphrase")
        VaultCrypto.decryptVault(encrypted, "wrong-passphrase")
    }

    @Test
    fun encrypt_twoInvocationsProduceDifferentCiphertextAndIV() {
        val vault = SyncVault(items = emptyList())
        val passphrase = "same-passphrase"

        val enc1 = VaultCrypto.encryptVault(vault, passphrase)
        val enc2 = VaultCrypto.encryptVault(vault, passphrase)

        assertNotEquals(enc1.saltBase64, enc2.saltBase64)
        assertNotEquals(enc1.ivBase64, enc2.ivBase64)
        assertNotEquals(enc1.ciphertextBase64, enc2.ciphertextBase64)
    }
}
