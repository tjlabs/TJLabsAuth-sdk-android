package com.tjlabs.tjlabsauth_sdk_android

import android.util.Log

internal object TJLabsAuthPerf {
    private const val TAG = "TJLabsAuth_PERF"

    fun newSession(label: String): Session {
        return if (TJAuthLogger.isEnabled()) ActiveSession(label) else NoopSession
    }

    interface Session {
        fun markStart()
        fun mark(phase: String): Long
        fun record(phase: String, elapsedMs: Long)
        fun end(extra: String = "")
        val label: String
    }

    private object NoopSession : Session {
        override val label: String = ""
        override fun markStart() {}
        override fun mark(phase: String): Long = 0L
        override fun record(phase: String, elapsedMs: Long) {}
        override fun end(extra: String) {}
    }

    private class ActiveSession(override val label: String) : Session {
        private val start: Long = System.nanoTime()
        private var lastMark: Long = start
        private val phases: LinkedHashMap<String, Long> = LinkedHashMap()

        override fun markStart() {
            lastMark = System.nanoTime()
        }

        override fun mark(phase: String): Long {
            val now = System.nanoTime()
            val elapsedMs = (now - lastMark) / 1_000_000
            phases[phase] = (phases[phase] ?: 0L) + elapsedMs
            lastMark = now
            Log.i(TAG, "[$label] $phase=${elapsedMs}ms")
            return elapsedMs
        }

        override fun record(phase: String, elapsedMs: Long) {
            phases[phase] = (phases[phase] ?: 0L) + elapsedMs
            Log.i(TAG, "[$label] $phase=${elapsedMs}ms")
        }

        override fun end(extra: String) {
            val total = (System.nanoTime() - start) / 1_000_000
            val breakdown = phases.entries.joinToString(", ") { "${it.key}=${it.value}ms" }
            val tail = if (extra.isBlank()) "" else " $extra"
            Log.i(TAG, "[$label] total=${total}ms { $breakdown }$tail")
        }
    }
}
