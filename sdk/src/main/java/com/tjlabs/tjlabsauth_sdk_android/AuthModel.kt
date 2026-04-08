package com.tjlabs.tjlabsauth_sdk_android

data class AuthRegion(
    val KOREA : String,
    val US_EAST : String,
    val CANADA : String
) {
    companion object {
        val KOREA : String = "KOREA"
        val US_EAST : String = "US_EAST"
        val CANADA : String = "CANADA"
    }
}


data class AuthInput(
    val client_secret: String,
    val access_key: String,
    val secret_access_key : String,
    val client_meta : AuthClientMeta
)

data class AuthClientMeta(
    val app_version : String,
    val app_package : String,
    val device_model : String,
    val os_version : String,
    val sdks : List<Sdk>
)

data class Sdk(
    val name : String,
    val version : String
)

data class AuthOutput(
    val access: String = "",
    val expires_in: Int = 0
)

sealed class TokenResult {
    data class Success(val token: String) : TokenResult()
    data class Failure(
        val reason: FailureReason,
        val statusCode: Int? = null,
        val message: String? = null
    ) : TokenResult()

    enum class FailureReason {
        REFRESH_FAILED,
        AUTH_FAILED,
        CREDENTIALS_MISSING
    }
}