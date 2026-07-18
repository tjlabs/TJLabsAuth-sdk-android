package com.tjlabs.tjlabsauth_sdk_android

import android.content.Context
import android.os.Build
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Executors

object TJLabsAuthManager {
    private const val KEY_ACCESS_TOKEN = "TJLabs.accessToken"
    private const val KEY_ACCESS_TOKEN_EXP = "TJLabs.accessTokenExp"
    private const val KEY_ACCESS_KEY = "TJLabs.accessKey"
    private const val KEY_SECRET_ACCESS_KEY = "TJLabs.secretAccessKey"
    private const val KEY_CLIENT_SECRET = "TJLabs.clientSecret"
    private const val AUTH_REISSUE_RETRY_LIMIT = 1

    private var accessToken: String = ""
    private var accessTokenExpDate: Instant = Instant.EPOCH

    private var storedAccessKey: String = ""
    private var storedSecretAccessKey: String = ""
    private var clientSecret: String = ""
    private var authenticated: Boolean = false
    private var tenantUserName: String? = null
    private var tenantName: String? = null
    private var customSdkInfos: List<Sdk> = emptyList()

    private lateinit var keychain: KeychainHelper
    private var appContext: Context? = null

    // Single-thread background executor for prewarm work (master key + TLS handshake).
    // Kept private; users only see the public prewarm() entry point.
    private val backgroundExecutor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "TJLabsAuth-prewarm").apply { isDaemon = true }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        keychain = KeychainHelper.getInstance(context)

        accessToken = keychain.load(KEY_ACCESS_TOKEN) ?: ""
        accessTokenExpDate = loadSavedAccessTokenExp() ?: extractExpirationDate(accessToken) ?: Instant.EPOCH

        storedAccessKey = keychain.load(KEY_ACCESS_KEY) ?: ""
        storedSecretAccessKey = keychain.load(KEY_SECRET_ACCESS_KEY) ?: ""
        clientSecret = keychain.load(KEY_CLIENT_SECRET) ?: clientSecret
        authenticated = false
        tenantUserName = null
        tenantName = null
        clearLegacyTenantCache()

        TJAuthLogger.d("[Init] manager initialized")
        TJAuthLogger.d(
            "[Init] cache loaded " +
                "accessToken=${accessToken.isNotBlank()} " +
                "exp=$accessTokenExpDate " +
                "accessKey=${storedAccessKey.isNotBlank()} " +
                "secretAccessKey=${storedSecretAccessKey.isNotBlank()} " +
                "clientSecret=${clientSecret.isNotBlank()} " +
                "authenticated=$authenticated " +
                "tenantUserName=false " +
                "tenantName=false"
        )
    }

    /**
     * Opt-in pre-warm: forces (off the main thread) the two cold-start costs that
     * dominate the first auth() call —
     *  1) Android KeyStore master-key generation for EncryptedSharedPreferences
     *  2) DNS resolution + TCP handshake + TLS handshake to the auth endpoint
     *
     * Safe to call multiple times; the second call returns almost immediately if
     * prefs are already created and a pooled TLS connection still exists.
     *
     * Recommended usage:
     *   TJLabsAuthManager.initialize(context)
     *   TJLabsAuthManager.setServerURL(provider, region)
     *   TJLabsAuthManager.prewarm(context)
     */
    fun prewarm(context: Context) {
        ensureInitialized(context)
        val keychainRef = if (::keychain.isInitialized) keychain else null
        val url = TJLabsAuthNetworkConstants.getUserBaseURL()
        backgroundExecutor.execute {
            val perf = TJLabsAuthPerf.newSession("prewarm")
            perf.markStart()

            // (1) Master-key warm-up.
            keychainRef?.ensureReady()
            perf.mark("prefs_init")

            // (2) TLS connection warm-up. A HEAD on the base URL is cheap; the response
            // status doesn't matter — we only care that the TLS session lives in the
            // shared connection pool when auth() is eventually called.
            try {
                val req = Request.Builder().url(url).head().build()
                TJLabsAuthNetworkConstants.sharedOkHttpClient().newCall(req).execute().use { /* discard */ }
            } catch (t: Throwable) {
                TJAuthLogger.e("[Prewarm] connection warm-up failed (non-fatal)", t)
            }
            perf.mark("tls_warmup")
            perf.end()
        }
    }

    private fun ensureInitialized(context: Context? = appContext) {
        val initContext = context?.applicationContext
        if (::keychain.isInitialized && initContext != null) {
            appContext = initContext
            return
        }

        if (initContext != null) {
            initialize(initContext)
        } else if (!::keychain.isInitialized) {
            TJAuthLogger.e("[Init] manager is not initialized; provide context first")
        }
    }

    fun setServerURL(
        provider: String,
        region: String = AuthRegion.KOREA.value,
        serverType: String = "jupiter",
        env: AuthServerEnv = AuthServerEnv.PROD,
    ) {
        TJAuthLogger.d("[Config] setServerURL provider=$provider region=$region serverType=$serverType env=$env")
        TJLabsAuthNetworkConstants.setServerURL(provider, region, serverType, env)
    }

    fun setClientSecret(context: Context, secret: String, persist: Boolean = false) {
        ensureInitialized(context)
        setClientSecret(secret, persist)
    }

    // 회사 SDK 정보는 앱에서 수동으로 등록 (예: TJLabsNavi, TJLabsJupiter 등)
    fun setSdkInfos(sdks: List<Sdk>) {
        customSdkInfos = sdks
        TJAuthLogger.d("[Config] sdk info updated count=${sdks.size}")
    }

    fun setLogEnabled(set : Boolean) {
        TJAuthLogger.setEnabled(set)
    }

    fun isAuthenticated(): Boolean {
        ensureInitialized()
        TJAuthLogger.d("[Auth] isAuthenticated=$authenticated")
        return authenticated
    }

    fun isAuthenticated(context: Context): Boolean {
        ensureInitialized(context)
        return isAuthenticated()
    }

    fun getTenantName(): String? = tenantName

    fun getTenantUserName(): String? = tenantUserName

    private fun clearTenantInfo() {
        tenantName = null
        tenantUserName = null
        TJAuthLogger.d("[Token] cleared tenant info")
    }

    private fun setAuthenticated(value: Boolean) {
        authenticated = value
        TJAuthLogger.d("[Auth] authentication state updated=$value")
    }

    private fun setClientSecret(secret: String, persist: Boolean = false) {
        clientSecret = secret
        if (persist && ::keychain.isInitialized) {
            keychain.save(KEY_CLIENT_SECRET, secret)
        }
        TJAuthLogger.d("[Config] clientSecret configured persist=$persist valueSet=${secret.isNotBlank()}")
    }

    private fun setTokenInfo(authOutput: AuthOutput) {
        accessToken = authOutput.access
        accessTokenExpDate = resolveAccessTokenExpiration(authOutput)

        if (::keychain.isInitialized) {
            // commit() (not apply()) so the access token survives an immediate
            // process kill — otherwise getAccessToken() on next cold start would
            // miss the cache and force a redundant auth() round-trip.
            keychain.saveSyncBatch(
                mapOf(
                    KEY_ACCESS_TOKEN to accessToken,
                    KEY_ACCESS_TOKEN_EXP to accessTokenExpDate.epochSecond.toString()
                )
            )
        }

        tenantUserName = authOutput.tenant.user_name
        tenantName = authOutput.tenant.name

        TJAuthLogger.d("[Token] cached access token exp=$accessTokenExpDate")
        TJAuthLogger.d("[Token] tenantName received=${!tenantName.isNullOrBlank()}")
        TJAuthLogger.d("[Token] tenantUserName received=${!tenantUserName.isNullOrBlank()}")
    }

    fun getAccessToken(update: Boolean = true, completion: (TokenResult) -> Unit) {
        TJAuthLogger.d("[Token] getAccessToken(update=$update) called")
        if (!update) {
            if (isAccessTokenValid(thresholdSeconds = 0)) {
                TJAuthLogger.d("[Token] returning cached token (strict validation)")
                completion(TokenResult.Success(accessToken))
            } else {
                TJAuthLogger.e("[Token] cached token unavailable or expired")
                completion(
                    TokenResult.Failure(
                        TokenResult.FailureReason.AUTH_FAILED,
                        statusCode = null,
                        message = "Access token is missing or expired"
                    )
                )
            }
            return
        }

        if (isAccessTokenValid(thresholdSeconds = 60)) {
            TJAuthLogger.d("[Token] returning cached token (valid > 60s)")
            completion(TokenResult.Success(accessToken))
            return
        }

        // access token 만료/임박 시 auth 재호출
        TJAuthLogger.d("[Token] token expired/expiring soon, re-authenticating")
        reAuthenticateIfPossible(completion)
    }

    // 하위 호환용 API: refresh endpoint가 없는 현재 구조에서는 재인증으로 동작
    private fun refresh(completion: (Int, Boolean) -> Unit) {
        reAuthenticateIfPossible { result ->
            when (result) {
                is TokenResult.Success -> completion(200, true)
                is TokenResult.Failure -> completion(result.statusCode ?: 401, false)
            }
        }
    }

    // 하위 호환용 API: 서버 verify endpoint 없이 로컬 만료검사로 판단
    private fun verify(completion: (Boolean) -> Unit) {
        completion(isAccessTokenValid(thresholdSeconds = 0))
    }

    // 하위 호환용 API
    private fun getRefreshToken(): String {
        return ""
    }

    fun auth(context: Context, accessKey: String, secretAccessKey: String, completion: (Int, Boolean) -> Unit) {
        ensureInitialized(context)
        auth(accessKey, secretAccessKey, completion)
    }

    fun auth(accessKey: String, secretAccessKey: String, completion: (Int, Boolean) -> Unit) {
        val perf = TJLabsAuthPerf.newSession("auth")
        perf.markStart()

        ensureInitialized()
        perf.mark("ensure_init")

        TJAuthLogger.d(
            "[Auth] auth requested " +
                "accessKey=${mask(accessKey)} " +
                "secretAccessKey=${mask(secretAccessKey)}"
        )

        storedAccessKey = accessKey
        storedSecretAccessKey = secretAccessKey

        if (::keychain.isInitialized) {
            keychain.save(KEY_ACCESS_KEY, accessKey)
            keychain.save(KEY_SECRET_ACCESS_KEY, secretAccessKey)
        }
        perf.mark("credential_persist")

        if (clientSecret.isBlank() && ::keychain.isInitialized) {
            clientSecret = keychain.load(KEY_CLIENT_SECRET) ?: ""
        }

        if (clientSecret.isBlank()) {
            setAuthenticated(false)
            clearTenantInfo()
            TJAuthLogger.e("[Auth] clientSecret is missing, auth aborted")
            perf.end("ok=false code=400 reason=missing_client_secret")
            completion(400, false)
            return
        }

        val url = TJLabsAuthNetworkConstants.getUserBaseURL()
        val clientMeta = buildClientMeta()
        perf.mark("build_request")

        requestAuthToken(
            accessKey = accessKey,
            secretAccessKey = secretAccessKey,
            clientMeta = clientMeta,
            url = url,
            attempt = 0,
            perf = perf,
            completion = completion
        )
    }

    private fun isAccessTokenValid(thresholdSeconds: Long): Boolean {
        if (accessToken.isBlank()) {
            TJAuthLogger.d("[Token] access token is empty")
            return false
        }
        val nowWithThreshold = Instant.now().plusSeconds(thresholdSeconds)
        val valid = nowWithThreshold.isBefore(accessTokenExpDate)
        val remainSeconds = accessTokenExpDate.epochSecond - Instant.now().epochSecond
        TJAuthLogger.d("[Token] validity check valid=$valid threshold=${thresholdSeconds}s remain=${remainSeconds}s")
        return valid
    }

    private fun clearLegacyTenantCache() {
        if (!::keychain.isInitialized) {
            return
        }
        keychain.delete("TJLabs.tenantUserName")
        keychain.delete("TJLabs.tenantName")
    }

    private fun reAuthenticateIfPossible(completion: (TokenResult) -> Unit) {
        TJAuthLogger.d("[Auth] re-authentication flow started")
        if (::keychain.isInitialized) {
            if (storedAccessKey.isBlank()) {
                storedAccessKey = keychain.load(KEY_ACCESS_KEY) ?: ""
            }
            if (storedSecretAccessKey.isBlank()) {
                storedSecretAccessKey = keychain.load(KEY_SECRET_ACCESS_KEY) ?: ""
            }
            if (clientSecret.isBlank()) {
                clientSecret = keychain.load(KEY_CLIENT_SECRET) ?: ""
            }
        }

        if (storedAccessKey.isBlank() || storedSecretAccessKey.isBlank()) {
            TJAuthLogger.e("[Auth] re-auth failed: access credentials missing")
            completion(
                TokenResult.Failure(
                    TokenResult.FailureReason.CREDENTIALS_MISSING,
                    statusCode = null,
                    message = "access_key/secret_access_key not stored"
                )
            )
            return
        }

        if (clientSecret.isBlank()) {
            TJAuthLogger.e("[Auth] re-auth failed: client secret missing")
            completion(
                TokenResult.Failure(
                    TokenResult.FailureReason.CREDENTIALS_MISSING,
                    statusCode = null,
                    message = "client secret not configured"
                )
            )
            return
        }

        auth(storedAccessKey, storedSecretAccessKey) { status, success ->
            if (success && isAccessTokenValid(thresholdSeconds = 0)) {
                TJAuthLogger.d("[Auth] re-authentication success")
                completion(TokenResult.Success(accessToken))
            } else {
                TJAuthLogger.e("[Auth] re-authentication failed status=$status")
                completion(
                    TokenResult.Failure(
                        TokenResult.FailureReason.AUTH_FAILED,
                        statusCode = status,
                        message = "Failed to reauthenticate"
                    )
                )
            }
        }
    }

    private fun resolveAccessTokenExpiration(authOutput: AuthOutput): Instant {
        if (authOutput.expires_in > 0) {
            TJAuthLogger.d("[Token] expiration source=expires_in value=${authOutput.expires_in}s")
            return Instant.now().plusSeconds(authOutput.expires_in.toLong())
        }
        val jwtExp = extractExpirationDate(authOutput.access)
        if (jwtExp != null) {
            TJAuthLogger.d("[Token] expiration source=jwt exp=$jwtExp")
            return jwtExp
        }
        TJAuthLogger.e("[Token] expiration parse failed, fallback=Instant.EPOCH")
        return Instant.EPOCH
    }

    private fun loadSavedAccessTokenExp(): Instant? {
        if (!::keychain.isInitialized) return null
        val expEpoch = keychain.load(KEY_ACCESS_TOKEN_EXP)?.toLongOrNull() ?: return null
        val exp = Instant.ofEpochSecond(expEpoch)
        TJAuthLogger.d("[Token] loaded saved exp=$exp")
        return exp
    }

    private fun buildClientMeta(): AuthClientMeta {
        val context = appContext
        val appPackage = context?.packageName ?: "unknown"
        val appVersion = context?.let {
            runCatching {
                val packageInfo = it.packageManager.getPackageInfo(it.packageName, 0)
                packageInfo.versionName ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toString()
                    }
                }
            }.getOrDefault("unknown")
        } ?: "unknown"

        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val deviceModel = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "unknown" }

        val osVersion = "Android ${Build.VERSION.RELEASE ?: "unknown"} (API ${Build.VERSION.SDK_INT})"
        val sdkInfos = if (customSdkInfos.isNotEmpty()) {
            customSdkInfos
        } else {
            listOf(Sdk(name = "TJLabsAuth-sdk-android", version = "unknown"))
        }

        return AuthClientMeta(
            app_version = appVersion,
            app_package = appPackage,
            device_model = deviceModel,
            os_version = osVersion,
            sdks = sdkInfos
        )
    }

    private fun extractExpirationDate(token: String): Instant? {
        val parts = token.split(".")
        if (parts.size < 2) return null

        return try {
            val base64 = parts[1]
                .replace("-", "+")
                .replace("_", "/")
                .let {
                    val padding = (4 - it.length % 4) % 4
                    it.padEnd(it.length + padding, '=')
                }

            val decodedBytes = Base64.getDecoder().decode(base64)
            val json = JSONObject(String(decodedBytes))
            val exp = json.getLong("exp")
            Instant.ofEpochSecond(exp)
        } catch (e: Exception) {
            TJAuthLogger.e("[Token] failed to decode jwt exp", e)
            null
        }
    }

    private fun extractTenantId(token: String): String? {
        val parts = token.split(".")
        if (parts.size < 2) return null

        return try {
            val base64 = parts[1]
                .replace("-", "+")
                .replace("_", "/")
                .let {
                    val padding = (4 - it.length % 4) % 4
                    it.padEnd(it.length + padding, '=')
                }

            val decodedBytes = Base64.getDecoder().decode(base64)
            val json = JSONObject(String(decodedBytes))
            json.getString("tenant_id")
        } catch (_: Exception) {
            null
        }
    }

    private fun mask(value: String): String {
        if (value.isBlank()) return "<empty>"
        return if (value.length <= 4) "***" else "${value.take(2)}***${value.takeLast(2)}"
    }

    private fun requestAuthToken(
        accessKey: String,
        secretAccessKey: String,
        clientMeta: AuthClientMeta,
        url: String,
        attempt: Int,
        perf: TJLabsAuthPerf.Session,
        completion: (Int, Boolean) -> Unit
    ) {
        val attemptNo = attempt + 1
        TJAuthLogger.d("[Auth] request start url=$url attempt=$attemptNo")

        val authInput = AuthInput(
            client_secret = clientSecret,
            access_key = accessKey,
            secret_access_key = secretAccessKey,
            client_meta = clientMeta
        )

        TJLabsAuthNetworkManager.postAuthToken(
            url = url,
            input = authInput,
            authServerVersion = TJLabsAuthNetworkConstants.getUserTokenVersion(),
            perf = perf
        ) { code, output ->
            val success = (code in 200 until 300) && output.access.isNotBlank()
            if (success) {
                TJAuthLogger.d("[Auth] request output : $output")

                val persistStart = System.nanoTime()
                setTokenInfo(output)
                perf.record("token_persist", (System.nanoTime() - persistStart) / 1_000_000)
                setAuthenticated(true)
                TJAuthLogger.d("[Auth] request success code=$code attempt=$attemptNo")
                perf.end("ok=true code=$code attempt=$attemptNo")
                completion(code, true)
                return@postAuthToken
            }

            setAuthenticated(false)
            TJAuthLogger.e("[Auth] request failed code=$code hasAccess=${output.access.isNotBlank()} attempt=$attemptNo")
            val shouldRetry = attempt < AUTH_REISSUE_RETRY_LIMIT
            if (shouldRetry) {
                TJAuthLogger.d("[Auth] retrying token reissue nextAttempt=${attemptNo + 1}")
                requestAuthToken(
                    accessKey = accessKey,
                    secretAccessKey = secretAccessKey,
                    clientMeta = clientMeta,
                    url = url,
                    attempt = attempt + 1,
                    perf = perf,
                    completion = completion
                )
            } else {
                clearTenantInfo()
                perf.end("ok=false code=$code attempt=$attemptNo")
                completion(code, false)
            }
        }
    }
}
