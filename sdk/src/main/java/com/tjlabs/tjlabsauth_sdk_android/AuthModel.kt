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

/**
 * Auth SDK 가 붙는 서버 환경.
 *
 * - [PROD] : 실 운영 서버 (`.tjlabscorp.com`). 외부 앱 · 실 배포는 반드시 이 값.
 *   `TJLabsAuthManager.setServerURL(...)` 를 env 인자 없이 호출하면 자동으로 이 값이 사용된다.
 *
 * - [DEV_TESTING_ONLY] : 개발 서버 (`.tjlabs.dev`). **TJLabs 내부 개발 · QA 전용.**
 *   외부 프로덕션 앱에서 사용 금지 — 개발 서버는 SLA 없이 언제든 스키마 변경 · 다운타임이 발생한다.
 *   사용 시 반드시 명시적으로 이 값을 넘겨야 한다.
 */
enum class AuthServerEnv {
    PROD,
    DEV_TESTING_ONLY
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
    val tenant: Tenant = Tenant()
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
