package com.tjlabs.tjlabsauth_sdk_android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeychainHelper private constructor(context: Context) {

    private val sharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        //SharedPreference를 저장할때 암호화하여 저장
        //AES256_SIV : key 암호화에 사용된 방식
        //AES256_GCM : value 암호화에 사용된 방식
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun load(key: String): String? {
        return try {
            sharedPreferences.getString(key, null)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(key: String) {
        sharedPreferences.edit().remove(key).apply()
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