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
    val secret_access_key : String
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