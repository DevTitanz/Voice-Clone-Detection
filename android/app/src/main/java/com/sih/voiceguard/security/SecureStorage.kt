package com.sih.voiceguard.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure token and preference storage backed by Android Keystore and EncryptedSharedPreferences.
 * Never stores plain passwords; encrypts short-lived JWT access tokens at rest.
 */
class SecureStorage(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "voiceguard_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_AUTH_TOKEN).apply()
    }

    fun saveAiApiKey(apiKey: String) {
        prefs.edit().putString(KEY_AI_API_KEY, apiKey.trim()).apply()
    }

    fun getAiApiKey(): String? {
        return prefs.getString(KEY_AI_API_KEY, null)
    }

    fun clearAiApiKey() {
        prefs.edit().remove(KEY_AI_API_KEY).apply()
    }

    companion object {
        private const val KEY_AUTH_TOKEN = "jwt_access_token"
        private const val KEY_AI_API_KEY = "external_ai_api_key"
    }
}
