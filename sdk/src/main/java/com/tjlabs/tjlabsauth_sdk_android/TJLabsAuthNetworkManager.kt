package com.tjlabs.tjlabsauth_sdk_android

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsAuthNetworkManager {
    fun postAuthToken(url : String, input : AuthInput, authServerVersion: String, completion: (Int, AuthOutput) -> Unit) {
        TJAuthLogger.d("[Network] postAuthToken start version=$authServerVersion url=$url")
        val retrofit = TJLabsAuthNetworkConstants.genRetrofit()
        val postPathPixel = retrofit.create(PostInput::class.java)
        postPathPixel.postAuth(input, authServerVersion).enqueue(object :
            Callback<AuthOutput> {
            override fun onFailure(call: Call<AuthOutput>, t: Throwable) {
                TJAuthLogger.e("[Network] postAuthToken failed", t)
                completion(500, AuthOutput())
            }
            override fun onResponse(call: Call<AuthOutput>, response: Response<AuthOutput>) {
                val statusCode = response.code()
                TJAuthLogger.d("[Network] postAuthToken response code=$statusCode")
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: AuthOutput()
                    completion(statusCode, resultData)
                } else {
                    completion(statusCode,  AuthOutput())
                }
            }
        })
    }

}
