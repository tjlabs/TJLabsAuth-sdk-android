package com.tjlabs.tjlabsauth_sdk_android

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

object TJLabsAuthManager {
    private const val KEY_ACCESS_TOKEN = "TJLabs.accessToken"
    private const val KEY_ACCESS_TOKEN_EXP = "TJLabs.accessTokenExp"
    private const val KEY_ACCESS_KEY = "TJLabs.accessKey"
    private const val KEY_SECRET_ACCESS_KEY = "TJLabs.secretAccessKey"
    private const val KEY_CLIENT_SECRET = "TJLabs.clientSecret"

    private var accessToken: String = ""
    private var accessTokenExpDate: Instant = Instant.EPOCH

    private var storedAccessKey: String = ""
    private var storedSecretAccessKey: String = ""
    private var clientSecret: String = ""

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

        Log.d("CheckToken", "initialize")
        Log.d("CheckToken", "accessToken exp : $accessTokenExpDate")
    }

    private fun initialize(context: Context, clientSecret: String) {
        initialize(context)
        setClientSecret(clientSecret, persist = true)
    }

    internal fun setServerURL(region: String = AuthRegion.KOREA, serverType: String = "jupiter") {
        TJLabsAuthNetworkConstants.setServerURL(region, serverType)
    }

    fun setClientSecret(context: Context, secret: String, persist: Boolean = true) {
        if (!::keychain.isInitialized) {
            initialize(context)
        }
        setClientSecret(secret, persist)
    }

    private fun setClientSecret(secret: String, persist: Boolean = true) {
        clientSecret = secret
        if (persist && ::keychain.isInitialized) {
            keychain.save(KEY_CLIENT_SECRET, secret)
        }
    }

    private fun setTokenInfo(authOutput: AuthOutput) {
        accessToken = authOutput.access
        accessTokenExpDate = resolveAccessTokenExpiration(authOutput)

        if (::keychain.isInitialized) {
            keychain.save(KEY_ACCESS_TOKEN, accessToken)
            keychain.save(KEY_ACCESS_TOKEN_EXP, accessTokenExpDate.epochSecond.toString())
        }
    }

    fun getAccessToken(update: Boolean = true, completion: (TokenResult) -> Unit) {
        if (!update) {
            if (isAccessTokenValid(thresholdSeconds = 0)) {
                completion(TokenResult.Success(accessToken))
            } else {
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
            completion(TokenResult.Success(accessToken))
            return
        }

        // access token 만료/임박 시 auth 재호출
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
            completion(400, false)
            return
        }

        val url = TJLabsAuthNetworkConstants.getUserBaseURL()
        val authInput = AuthInput(
            client_secret = clientSecret,
            access_key = accessKey,
            secret_access_key = secretAccessKey
        )

        TJLabsAuthNetworkManager.postAuthToken(url, authInput, TJLabsAuthNetworkConstants.getUserTokenVersion()) { code, output ->
            val success = (code in 200 until 300) && output.access.isNotBlank()
            if (success) {
                setTokenInfo(output)
            }
            completion(code, success)
        }
    }

    private fun isAccessTokenValid(thresholdSeconds: Long): Boolean {
        if (accessToken.isBlank()) return false
        val nowWithThreshold = Instant.now().plusSeconds(thresholdSeconds)
        return nowWithThreshold.isBefore(accessTokenExpDate)
    }

    private fun reAuthenticateIfPossible(completion: (TokenResult) -> Unit) {
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
                completion(TokenResult.Success(accessToken))
            } else {
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
            return Instant.now().plusSeconds(authOutput.expires_in.toLong())
        }
        return extractExpirationDate(authOutput.access) ?: Instant.EPOCH
    }

    private fun loadSavedAccessTokenExp(): Instant? {
        if (!::keychain.isInitialized) return null
        val expEpoch = keychain.load(KEY_ACCESS_TOKEN_EXP)?.toLongOrNull() ?: return null
        return Instant.ofEpochSecond(expEpoch)
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
        } catch (_: Exception) {
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
}
