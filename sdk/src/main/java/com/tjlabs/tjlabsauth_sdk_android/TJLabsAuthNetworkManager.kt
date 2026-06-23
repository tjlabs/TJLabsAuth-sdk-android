package com.tjlabs.tjlabsauth_sdk_android

import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsAuthNetworkManager {
    fun postAuthToken(
        url: String,
        input: AuthInput,
        authServerVersion: String,
        perf: TJLabsAuthPerf.Session = TJLabsAuthPerf.newSession("noop"),
        completion: (Int, AuthOutput) -> Unit
    ) {
        TJAuthLogger.d("[Network] postAuthToken start version=$authServerVersion url=$url")
        val retrofit = TJLabsAuthNetworkConstants.genRetrofit()
        val postPathPixel = retrofit.create(PostInput::class.java)

        // Bind the perf session to the next OkHttp call so the EventListener can
        // record dns/tcp+tls/server/rsp_read into the same breakdown.
        TJLabsAuthPerfHolder.set(perf)

        val enqueueStart = System.nanoTime()
        postPathPixel.postAuth(input, authServerVersion).enqueue(object :
            Callback<okhttp3.ResponseBody> {
            override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) {
                TJLabsAuthPerfHolder.clear()
                perf.record("network_total", (System.nanoTime() - enqueueStart) / 1_000_000)
                TJAuthLogger.e("[Network] postAuthToken failed", t)
                completion(500, AuthOutput())
            }

            override fun onResponse(
                call: Call<okhttp3.ResponseBody>,
                response: Response<okhttp3.ResponseBody>
            ) {
                TJLabsAuthPerfHolder.clear()
                perf.record("network_total", (System.nanoTime() - enqueueStart) / 1_000_000)

                val statusCode = response.code()
                TJAuthLogger.d("[Network] postAuthToken response code=$statusCode")

                val parseStart = System.nanoTime()
                val rawBody = response.body()?.string().orEmpty()
                if (TJAuthLogger.isEnabled()) {
                    TJAuthLogger.d(
                        "[Network] postAuthToken raw response=${maskSensitiveResponse(rawBody)}"
                    )
                }

                if (statusCode in 200 until 300) {
                    val resultData = parseAuthOutput(rawBody)
                    perf.record("json_parse", (System.nanoTime() - parseStart) / 1_000_000)
                    completion(statusCode, resultData)
                } else {
                    perf.record("json_parse", (System.nanoTime() - parseStart) / 1_000_000)
                    completion(statusCode, AuthOutput())
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
