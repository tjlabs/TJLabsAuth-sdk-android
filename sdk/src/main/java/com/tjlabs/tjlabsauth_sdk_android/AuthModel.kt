package com.tjlabs.tjlabsauth_sdk_android

enum class AuthRegion(val value: String) {
    KOREA("KOREA"),
    US_EAST("US_EAST"),
    CANADA("CANADA"),
    SAUDI("SAUDI")
}

enum class ServerProvider(val value: String) {
    AWS("aws"),
    GCP("gcp")
}

data class AuthInput(
    val client_secret: String,
    val access_key: String,
    val secret_access_key: String,
    val client_meta: AuthClientMeta
)

data class AuthClientMeta(
    val app_version: String,
    val app_package: String,
    val device_model: String,
    val os_version: String,
    val sdks: List<Sdk>
)

data class Sdk(
    val name: String,
    val version: String
)

data class Tenant(
    val id: Int = -1,
    val name: String = "",
    val user_name: String = ""
)

data class AuthOutput(
    val access: String = "",
    val expires_in: Int = 0,
    val tenant_user_name: String? = null,
    val tenant_name: String? = null,
    val tenant: Tenant? = null
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
