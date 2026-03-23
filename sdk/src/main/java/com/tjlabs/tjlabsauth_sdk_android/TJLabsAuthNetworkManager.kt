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
                Log.d("CheckToken", "auth status code : $statusCode // request : ${call.request()} // response : ${response.body()}")
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: AuthOutput()
                    completion(statusCode, resultData)
                } else {
                    completion(statusCode,  AuthOutput())
                }
            }
        })
    }

    fun postRefreshToken(url : String, input : RefreshTokenInput, authServerVersion: String, completion: (Int, RefreshTokenOutput) -> Unit) {
        val retrofit = TJLabsAuthNetworkConstants.genRetrofit(url)
        val postPathPixel = retrofit.create(PostInput::class.java)
        postPathPixel.postRefresh(input, authServerVersion).enqueue(object :
            Callback<RefreshTokenOutput> {
            override fun onFailure(call: Call<RefreshTokenOutput>, t: Throwable) {
                completion(500, RefreshTokenOutput())
            }
            override fun onResponse(call: Call<RefreshTokenOutput>, response: Response<RefreshTokenOutput>) {
                val statusCode = response.code()
                Log.d("CheckToken", "refresh status code : $statusCode // request : ${call.request()} // response : ${response.body()}")

                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: RefreshTokenOutput()
                    completion(statusCode, resultData)
                } else {
                    completion(statusCode,  RefreshTokenOutput())
                }
            }
        })
    }


    fun postVerifyToken(url : String, input : VerifyTokenInput, authServerVersion: String, completion: (Int) -> Unit) {
        val retrofit = TJLabsAuthNetworkConstants.genRetrofit(url)
        val postPathPixel = retrofit.create(PostInput::class.java)
        postPathPixel.postVerify(input, authServerVersion).enqueue(object :
            Callback<Any> {
            override fun onFailure(call: Call<Any>, t: Throwable) {
                completion(500)
            }
            override fun onResponse(call: Call<Any>, response: Response<Any>) {
                val statusCode = response.code()
                Log.d("CheckToken", "verify status code : $statusCode // request : ${call.request()} // response : ${response.body()}")
                completion(statusCode)
            }
        })
    }




}