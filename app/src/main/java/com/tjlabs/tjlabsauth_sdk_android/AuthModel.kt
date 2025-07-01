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
    val name: String,
    val password: String
)

data class AuthOutput(
    val refresh: String = "",
    val access: String = ""
)

data class RefreshTokenInput(
    val refresh: String
)

data class RefreshTokenOutput(
    val access: String = ""
)

data class VerifyTokenInput(
    val token: String
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