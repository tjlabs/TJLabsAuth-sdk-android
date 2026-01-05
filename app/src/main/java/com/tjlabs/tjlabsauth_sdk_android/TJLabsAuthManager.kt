package com.tjlabs.tjlabsauth_sdk_android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

object TJLabsAuthManager {
    private var accessToken: String = ""
    private var refreshToken: String = ""

    private var accessTokenExpDate: Instant = Instant.EPOCH
    private var refreshTokenExpDate: Instant = Instant.EPOCH

    private var storedUsername: String = ""
    private var storedPassword: String = ""

    private var isRefreshing = false

    private  lateinit var keychain : KeychainHelper

    fun initialize(context: Context) {
        keychain = KeychainHelper.getInstance(context)
        //accessToken = keychain.load("TJLabs.accessToken") ?: ""
        //refreshToken = keychain.load("TJLabs.refreshToken") ?: ""
        //storedUsername = keychain.load("TJLabs.username") ?: ""
        //storedPassword = keychain.load("TJLabs.password") ?: ""

        Log.d("CheckToken", "initialize")
        Log.d("CheckToken", "accessToken : $accessToken")
        Log.d("CheckToken", "refreshToken : $refreshToken")
        Log.d("CheckToken", "storedUsername : $storedUsername")
        Log.d("CheckToken", "storedPassword : $storedPassword")
    }

    fun setServerURL(region: String = AuthRegion.KOREA, serverType: String = "jupiter")
    {
        TJLabsAuthNetworkConstants.setServerURL(region, serverType)
    }

    private fun setTokenInfo(authOutput: AuthOutput) {
        accessToken = authOutput.access
        refreshToken = authOutput.refresh

        keychain.apply {
            save("TJLabs.accessToken", accessToken)
            save("TJLabs.refreshToken", refreshToken)
        }

        extractExpirationDate(authOutput.access)?.let {
            accessTokenExpDate = it
        }

        extractExpirationDate(authOutput.refresh)?.let {
            refreshTokenExpDate = it
        }
    }

    fun getAccessToken(update: Boolean = true, completion: (TokenResult) -> Unit) {
        Log.d("CheckToken", "access token exp : ${extractExpirationDate(accessToken)} // tenant_id : ${extractTenantId(accessToken)}")

        if (!update) {
            completion(TokenResult.Success(accessToken))
            return
        }

        if (isTokenNearExpiry(refreshToken, threshold = 60)) {
            Log.d("CheckToken", "refreshToken expiry")
            reAuthenticateIfPossible(completion)
            return
        }

        if (isTokenNearExpiry(accessToken, threshold = 60)) {
            Log.d("CheckToken", "accessToken expiry")

            refresh { status, success ->
                if (success) {
                    Log.d("CheckToken", "refresh success")
                    completion(TokenResult.Success(accessToken))
                } else {
                    Log.d("CheckToken", "refresh fail")
                    completion(
                        TokenResult.Failure(
                            TokenResult.FailureReason.REFRESH_FAILED,
                            statusCode = status,
                            message = "Failed to refresh token"
                        )
                    )
                }
            }
        } else {
            Log.d("CheckToken", "accessToken get")
            completion(TokenResult.Success(accessToken))
        }
    }

    fun getRefreshToken() : String {
        return refreshToken
    }

    fun auth(name : String, password : String, completion : (Int, Boolean) -> Unit) {
        storedUsername = name
        storedPassword = password

        keychain.apply {
            save("TJLabs.username", name)
            save("TJLabs.password", password)
        }

        val url = TJLabsAuthNetworkConstants.getUserBaseURL()
        val authInput = AuthInput(name, password)
        var isSuccess = false
        TJLabsAuthNetworkManager.postAuthToken(url, authInput, TJLabsAuthNetworkConstants.getUserTokenVersion()) {
            code, output ->
            if (code == 200) {
                setTokenInfo(output)
                isSuccess = true
            }

            completion(code, isSuccess)
        }
    }

    fun refresh(completion : (Int, Boolean) -> Unit) {
        synchronized(this) {
            if (isRefreshing) {
                Handler(Looper.getMainLooper()).post {
                    completion(409, false)
                }
                return
            }
            isRefreshing = true
            val url = TJLabsAuthNetworkConstants.getUserBaseURL()
            val authInput = RefreshTokenInput(refreshToken)

            var isSuccess = false
            TJLabsAuthNetworkManager.postRefreshToken(url, authInput, TJLabsAuthNetworkConstants.getUserTokenVersion()) {
                    code, output ->
                isRefreshing = false
                if (code == 200) {
                    accessToken = output.access
                    keychain.save("TJLabs.accessToken", accessToken)
                    val expDate = extractExpirationDate(accessToken)
                    if (expDate != null) {
                        accessTokenExpDate = expDate
                    }
                    isSuccess = true
                }

                completion(code, isSuccess)
            }
        }
    }

    fun verify(completion : (Boolean) -> Unit) {
        val url = TJLabsAuthNetworkConstants.getUserBaseURL()
        val authInput = VerifyTokenInput(accessToken)
        TJLabsAuthNetworkManager.postVerifyToken(url, authInput, TJLabsAuthNetworkConstants.getUserTokenVersion()) {
                code ->
            isRefreshing = false
            if (code == 200) {
                completion(true)
            }else {
                completion(false)
            }
        }
    }



    private fun isTokenNearExpiry(token: String, threshold: Long = 60): Boolean {
        val exp = extractExpirationDate(token) ?: return true
        return Instant.now().plusSeconds(threshold) >= exp
    }

    private fun reAuthenticateIfPossible(
        completion: (TokenResult) -> Unit
    ) {
        if (storedUsername.isEmpty() || storedPassword.isEmpty()) {
            completion(
                TokenResult.Failure(
                    TokenResult.FailureReason.CREDENTIALS_MISSING,
                    statusCode = null,
                    message = "Username/password not stored"
                )
            )
            return
        }

        auth(storedUsername, storedPassword) { status, success ->
            if (success) {
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

    fun extractExpirationDate(token: String): Instant? {
        val parts = token.split(".")
        if (parts.size < 2) return null

        return try {
            // Base64 URL 디코딩 (padding 보정)
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
            null
        }
    }

    fun extractTenantId(token: String): String? {
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
        } catch (e: Exception) {
            null
        }
    }
}