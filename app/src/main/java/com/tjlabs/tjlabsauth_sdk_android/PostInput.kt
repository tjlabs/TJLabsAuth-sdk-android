package com.tjlabs.tjlabsauth_sdk_android

import retrofit2.Call
import retrofit2.http.*

internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )
    @POST("/{token_server_version}/token")
    fun postAuth(@Body param: AuthInput, @Path("token_server_version") authServerVersion : String) : Call<AuthOutput>

    @POST("/{token_server_version}/token/refresh")
    fun postRefresh(@Body param: RefreshTokenInput, @Path("token_server_version") scaleVersion : String) : Call<RefreshTokenOutput>

    @POST("/{token_server_version}/token/verify")
    fun postVerify(@Body param: VerifyTokenInput, @Path("token_server_version") levelVersion : String) : Call<Any>

}