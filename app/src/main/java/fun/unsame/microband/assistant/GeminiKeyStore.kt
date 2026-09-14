package com.unsame.microband.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GeminiKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("microband_secrets", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV)

    fun save(apiKey: String) {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "Gemini API key is empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(cipher.doFinal(normalized.toByteArray()), Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? = runCatching {
        val encrypted = Base64.decode(preferences.getString(KEY_CIPHERTEXT, null), Base64.NO_WRAP)
        val iv = Base64.decode(preferences.getString(KEY_IV, null), Base64.NO_WRAP)
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        }.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        preferences.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "microband_gemini_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_CIPHERTEXT = "gemini_api_key"
        const val KEY_IV = "gemini_api_key_iv"
    }
}
