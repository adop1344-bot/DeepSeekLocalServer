package com.rikkahub.deepseeklocal.data.local.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for the DeepSeek web token and optional official API key.
 * Uses Jetpack Security Crypto with an AES-256-GCM master key in the Android Keystore.
 */
class TokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Returns the stored web token, or null if unset. */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Persists the web token. */
    fun setToken(value: String) {
        prefs.edit().putString(KEY_TOKEN, value).apply()
    }

    /** Removes the stored web token. */
    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /** Returns the optional official API key, or null. */
    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    /** Persists the official API key. */
    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API, value).apply()
    }

    companion object {
        private const val FILE_NAME = "deepseek_secure_prefs"
        private const val KEY_TOKEN = "deepseek_web_token"
        private const val KEY_API = "deepseek_api_key"
    }
}
