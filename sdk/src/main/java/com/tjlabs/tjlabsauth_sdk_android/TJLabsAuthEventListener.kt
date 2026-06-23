package com.tjlabs.tjlabsauth_sdk_android

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Emits per-phase elapsed for every OkHttp call this SDK makes. Output is gated through
 * TJAuthLogger so production builds (setLogEnabled(false)) pay no logging cost.
 *
 * Phase semantics (these match OkHttp's actual event firing order, which is NOT obvious
 * from the event names alone):
 *   dns         = name resolution
 *   tcp+tls     = TCP connect + TLS handshake
 *   req_send    = requestHeadersStart → responseHeadersStart
 *                  ≈ time to write request headers + body to the socket
 *   server_wait = responseHeadersStart → responseHeadersEnd
 *                  ≈ time-to-first-byte (server processing + network back to client)
 *   body_read   = responseHeadersEnd → responseBodyEnd
 *                  ≈ time to receive the rest of the response body bytes
 *
 * The split matters because OkHttp's `responseHeadersStart` event fires immediately after
 * the request is written (BEFORE the server has actually responded). Lumping everything
 * after that into a single "rsp_read" phase makes server-side latency invisible.
 */
internal class TJLabsAuthEventListener : EventListener() {
    private var dnsStart: Long = 0L
    private var connectStart: Long = 0L
    private var requestStart: Long = 0L
    private var responseHeadersStartNs: Long = 0L
    private var responseHeadersEndNs: Long = 0L

    override fun dnsStart(call: Call, domainName: String) {
        if (!TJAuthLogger.isEnabled()) return
        dnsStart = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        if (!TJAuthLogger.isEnabled()) return
        val ms = elapsedMs(dnsStart)
        TJLabsAuthPerfHolder.current()?.record("dns", ms)
            ?: TJAuthLogger.d("[PERF] dns=${ms}ms host=$domainName")
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy
    ) {
        if (!TJAuthLogger.isEnabled()) return
        connectStart = System.nanoTime()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        if (!TJAuthLogger.isEnabled()) return
        val ms = elapsedMs(connectStart)
        TJLabsAuthPerfHolder.current()?.record("tcp+tls", ms)
            ?: TJAuthLogger.d("[PERF] tcp+tls=${ms}ms protocol=$protocol")
    }

    override fun requestHeadersStart(call: Call) {
        if (!TJAuthLogger.isEnabled()) return
        requestStart = System.nanoTime()
    }

    override fun responseHeadersStart(call: Call) {
        if (!TJAuthLogger.isEnabled()) return
        val ms = elapsedMs(requestStart)
        responseHeadersStartNs = System.nanoTime()
        TJLabsAuthPerfHolder.current()?.record("req_send", ms)
            ?: TJAuthLogger.d("[PERF] req_send=${ms}ms")
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        if (!TJAuthLogger.isEnabled()) return
        val ms = elapsedMs(responseHeadersStartNs)
        responseHeadersEndNs = System.nanoTime()
        TJLabsAuthPerfHolder.current()?.record("server_wait", ms)
            ?: TJAuthLogger.d("[PERF] server_wait=${ms}ms (TTFB)")
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        if (!TJAuthLogger.isEnabled()) return
        // Prefer responseHeadersEnd as the body-read starting point (the gap between
        // responseHeadersEnd and responseBodyStart is sub-ms in practice).
        val base = if (responseHeadersEndNs != 0L) responseHeadersEndNs else responseHeadersStartNs
        val ms = elapsedMs(base)
        TJLabsAuthPerfHolder.current()?.record("body_read", ms)
            ?: TJAuthLogger.d("[PERF] body_read=${ms}ms bytes=$byteCount")
    }

    private fun elapsedMs(start: Long): Long {
        if (start == 0L) return 0L
        return (System.nanoTime() - start) / 1_000_000
    }
}

/**
 * Holds the currently active perf session so the EventListener (which runs on the OkHttp
 * dispatcher thread, not the calling thread) can attribute its phase timings back to the
 * auth() call that started the request.
 *
 * Auth calls in this SDK are inherently serial (one auth call at a time per process),
 * so a single shared @Volatile reference is sufficient.
 */
internal object TJLabsAuthPerfHolder {
    @Volatile
    private var session: TJLabsAuthPerf.Session? = null

    fun set(s: TJLabsAuthPerf.Session?) {
        session = s
    }

    fun current(): TJLabsAuthPerf.Session? = session

    fun clear() {
        session = null
    }
}
