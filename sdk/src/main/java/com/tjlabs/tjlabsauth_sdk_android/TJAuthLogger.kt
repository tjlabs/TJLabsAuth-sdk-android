package com.tjlabs.tjlabsauth_sdk_android

import android.util.Log

object TJAuthLogger {
    private const val DEFAULT_TAG = "TJLabsAuth"

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var tag: String = DEFAULT_TAG

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled

    fun setTag(value: String) {
        tag = value.ifBlank { DEFAULT_TAG }
    }

    fun d(message: String) {
        if (!enabled) return
        Log.d(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (!enabled) return
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
