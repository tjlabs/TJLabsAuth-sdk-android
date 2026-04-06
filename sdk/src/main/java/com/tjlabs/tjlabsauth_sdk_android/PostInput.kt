package com.tjlabs.tjlabsauth_sdk_android

import retrofit2.Call
import retrofit2.http.*

internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )

    @POST("/{token_server_version}/tenants/access-keys/token")
    fun postAuth(@Body param: AuthInput, @Path("token_server_version") authServerVersion : String) : Call<AuthOutput>


}