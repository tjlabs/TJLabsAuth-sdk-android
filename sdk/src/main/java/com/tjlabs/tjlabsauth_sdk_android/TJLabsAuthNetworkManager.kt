package com.tjlabs.tjlabsauth_sdk_android

import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsAuthNetworkManager {
    fun postAuthToken(url : String, input : AuthInput, authServerVersion: String, completion: (Int, AuthOutput) -> Unit) {
        TJAuthLogger.d("[Network] postAuthToken start version=$authServerVersion url=$url")
        val retrofit = TJLabsAuthNetworkConstants.genRetrofit()
        val postPathPixel = retrofit.create(PostInput::class.java)
        postPathPixel.postAuth(input, authServerVersion).enqueue(object :
            Callback<okhttp3.ResponseBody> {
            override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) {
                TJAuthLogger.e("[Network] postAuthToken failed", t)
                completion(500, AuthOutput())
            }
            override fun onResponse(call: Call<okhttp3.ResponseBody>, response: Response<okhttp3.ResponseBody>) {
                val statusCode = response.code()
                TJAuthLogger.d("[Auth] request request : ${call.request()}")
                TJAuthLogger.d("[Network] postAuthToken response code=$statusCode")

                val rawBody = response.body()?.string().orEmpty()
                TJAuthLogger.d("[Network] postAuthToken raw response=${maskSensitiveResponse(rawBody)}")

                if (statusCode in 200 until 300) {
                    val resultData = parseAuthOutput(rawBody)
                    completion(statusCode, resultData)
                } else {
                    completion(statusCode,  AuthOutput())
                }
            }
        })
    }

    private fun parseAuthOutput(rawBody: String): AuthOutput {
        if (rawBody.isBlank()) {
            return AuthOutput()
        }
        return try {
            val json = JSONObject(rawBody)
            val tenantJson = json.optJSONObject("tenant")

            AuthOutput(
                access = json.optString("access", ""),
                expires_in = json.optInt("expires_in", 0),
                tenant = Tenant(
                    id = tenantJson?.optInt("id", -1) ?: -1,
                    name = tenantJson?.optString("name", "") ?: "",
                    user_name = tenantJson?.optString("user_name", "") ?: ""
                )
            )
        } catch (t: Throwable) {
            TJAuthLogger.e("[Network] failed to parse auth response", t)
            AuthOutput()
        }
    }

    private fun maskSensitiveResponse(rawBody: String): String {
        if (rawBody.isBlank()) {
            return "<empty>"
        }
        return rawBody.replace(Regex("\"access\"\\s*:\\s*\"([^\"]*)\"")) { match ->
            val maskedToken = mask(match.groupValues[1])
            "\"access\":\"$maskedToken\""
        }
    }

    private fun mask(value: String): String {
        if (value.isBlank()) return "<empty>"
        return if (value.length <= 8) "***" else "${value.take(4)}***${value.takeLast(4)}"
    }
}
