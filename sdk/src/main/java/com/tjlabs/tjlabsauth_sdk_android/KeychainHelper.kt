package com.tjlabs.tjlabsauth_sdk_android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeychainHelper private constructor(private val context: Context) {

    // EncryptedSharedPreferences.create(...) on first call triggers a synchronous
    // Android KeyStore master-key generation that costs 100–500ms on real devices.
    // We expose ensureReady() / isReady() so callers can pre-warm this off the main
    // thread (see TJLabsAuthManager.prewarm). The lazy initializer keeps the original
    // behaviour as a safety fallback for callers that did not pre-warm.
    @Volatile
    private var _prefs: SharedPreferences? = null

    private val prefs: SharedPreferences
        get() = _prefs ?: synchronized(this) {
            _prefs ?: createPrefs().also { _prefs = it }
        }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns true if the encrypted prefs handle (master key) has already been created. */
    fun isReady(): Boolean = _prefs != null

    /** Forces master-key generation. Cheap if already initialised; expensive on first call. */
    fun ensureReady() {
        prefs
    }

    fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /**
     * Writes [pairs] in a single commit() so the entry is durable before this call
     * returns. Used for access-token persistence — apply() may not flush before
     * a force-kill, which would force a redundant auth() on the next cold start.
     */
    fun saveSyncBatch(pairs: Map<String, String>): Boolean {
        val editor = prefs.edit()
        pairs.forEach { (k, v) -> editor.putString(k, v) }
        return editor.commit()
    }

    fun load(key: String): String? {
        return try {
            prefs.getString(key, null)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        @Volatile
        private var instance: KeychainHelper? = null

        fun getInstance(context: Context): KeychainHelper {
            return instance ?: synchronized(this) {
                instance ?: KeychainHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
