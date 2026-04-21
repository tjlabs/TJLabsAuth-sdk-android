package com.tjlabs.tjlabsauth_sdk_android

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

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
    private var customSdkInfos: List<Sdk> = emptyList()

    private lateinit var keychain: KeychainHelper
    private var appContext: Context? = null

    private fun initialize(context: Context) {
        appContext = context.applicationContext
        keychain = KeychainHelper.getInstance(context)

        accessToken = keychain.load(KEY_ACCESS_TOKEN) ?: ""
        accessTokenExpDate = loadSavedAccessTokenExp() ?: extractExpirationDate(accessToken) ?: Instant.EPOCH

        storedAccessKey = keychain.load(KEY_ACCESS_KEY) ?: ""
        storedSecretAccessKey = keychain.load(KEY_SECRET_ACCESS_KEY) ?: ""
        clientSecret = keychain.load(KEY_CLIENT_SECRET) ?: clientSecret

        TJAuthLogger.d("[Init] manager initialized")
        TJAuthLogger.d(
            "[Init] cache loaded " +
                "accessToken=${accessToken.isNotBlank()} " +
                "exp=$accessTokenExpDate " +
                "accessKey=${storedAccessKey.isNotBlank()} " +
                "secretAccessKey=${storedSecretAccessKey.isNotBlank()} " +
                "clientSecret=${clientSecret.isNotBlank()}"
        )
    }

    fun setServerURL(provider: String, region: String = AuthRegion.KOREA.value, serverType: String = "jupiter") {
        TJAuthLogger.d("[Config] setServerURL provider=$provider region=$region serverType=$serverType")
        TJLabsAuthNetworkConstants.setServerURL(provider, region, serverType)
    }

    fun setClientSecret(context: Context, secret: String, persist: Boolean = false) {
        if (!::keychain.isInitialized) {
            initialize(context)
        }
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
            keychain.save(KEY_ACCESS_TOKEN, accessToken)
            keychain.save(KEY_ACCESS_TOKEN_EXP, accessTokenExpDate.epochSecond.toString())
        }
        TJAuthLogger.d("[Token] cached access token exp=$accessTokenExpDate")
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

    fun auth(accessKey: String, secretAccessKey: String, completion: (Int, Boolean) -> Unit) {
        TJAuthLogger.d(
            "[Auth] auth requested " +
                "accessKey=${mask(accessKey)} " +
                "secretAccessKey=${mask(secretAccessKey)}"
        )
        if (!::keychain.isInitialized) {
            appContext?.let { initialize(it) }
        }

        storedAccessKey = accessKey
        storedSecretAccessKey = secretAccessKey

        if (::keychain.isInitialized) {
            keychain.save(KEY_ACCESS_KEY, accessKey)
            keychain.save(KEY_SECRET_ACCESS_KEY, secretAccessKey)
        }

        if (clientSecret.isBlank() && ::keychain.isInitialized) {
            clientSecret = keychain.load(KEY_CLIENT_SECRET) ?: ""
        }

        if (clientSecret.isBlank()) {
            TJAuthLogger.e("[Auth] clientSecret is missing, auth aborted")
            completion(400, false)
            return
        }

        val url = TJLabsAuthNetworkConstants.getUserBaseURL()
        val clientMeta = buildClientMeta()
        requestAuthToken(
            accessKey = accessKey,
            secretAccessKey = secretAccessKey,
            clientMeta = clientMeta,
            url = url,
            attempt = 0,
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

        TJLabsAuthNetworkManager.postAuthToken(url, authInput, TJLabsAuthNetworkConstants.getUserTokenVersion()) { code, output ->
            val success = (code in 200 until 300) && output.access.isNotBlank()
            if (success) {
                setTokenInfo(output)
                TJAuthLogger.d("[Auth] request success code=$code attempt=$attemptNo")
                completion(code, true)
                return@postAuthToken
            }

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
                    completion = completion
                )
            } else {
                completion(code, false)
            }
        }
    }
}
