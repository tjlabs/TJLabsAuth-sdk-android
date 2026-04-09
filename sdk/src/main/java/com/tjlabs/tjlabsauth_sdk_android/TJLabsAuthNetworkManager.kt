package com.tjlabs.tjlabsauth_sdk_android

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsAuthNetworkManager {
    fun postAuthToken(url : String, input : AuthInput, authServerVersion: String, completion: (Int, AuthOutput) -> Unit) {
        val retrofit = TJLabsAuthNetworkConstants.genRetrofit(url)
        val postPathPixel = retrofit.create(PostInput::class.java)
        postPathPixel.postAuth(input, authServerVersion).enqueue(object :
            Callback<AuthOutput> {
            override fun onFailure(call: Call<AuthOutput>, t: Throwable) {
                completion(500, AuthOutput())
            }
            override fun onResponse(call: Call<AuthOutput>, response: Response<AuthOutput>) {
                val statusCode = response.code()
                logDebug("auth status code : $statusCode")
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: AuthOutput()
                    completion(statusCode, resultData)
                } else {
                    completion(statusCode,  AuthOutput())
                }
            }
        })
    }
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("CheckToken", message)
        }
    }

}
