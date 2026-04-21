package com.tjlabs.tjlabsauth_sdk_android

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsAuthNetworkConstants {
    private const val USER_JUPITER_TOKEN_SERVER_VERSION = "2026-04-07"
    private const val USER_PHOENIX_TOKEN_SERVER_VERSION = "2025-06-11"
    private const val USER_GUARDIANS_TOKEN_SERVER_VERSION = "2025-06-30"

    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    private const val SUFFIX = ".tjlabs.dev"
    private var REGION_NAME = "Korea"
    private var SERVER_TYPE: String = "jupiter"
    private var USER_URL = "$HTTP_PREFIX${REGION_PREFIX}user.$SERVER_TYPE$SUFFIX"

    fun genRetrofit(bearerToken: String? = null): Retrofit {
        val okHttpClientBuilder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)

        if (!bearerToken.isNullOrBlank()) {
            okHttpClientBuilder.addInterceptor(HeaderInterceptor(bearerToken))
        }

        val okHttpClient = okHttpClientBuilder.build()

        return Retrofit.Builder()
            .baseUrl(USER_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }


    fun setServerURL(provider: String, region: String, serverType: String) {
        val normalizedRegion = region.uppercase()
        val normalizedProvider = provider.lowercase()
        val normalizedServerType = serverType.ifBlank { "jupiter" }.lowercase()

        when (normalizedRegion) {
            AuthRegion.KOREA.value -> {
                REGION_PREFIX = when (normalizedProvider) {
                    ServerProvider.AWS.value -> "ap-northeast-2."
                    ServerProvider.GCP.value -> "asia-northeast3."
                    else -> {
                        TJAuthLogger.e("[Network] invalid provider=$provider for Korea, fallback=aws")
                        "ap-northeast-2."
                    }
                }
                REGION_NAME = "Korea"
            }

            AuthRegion.CANADA.value -> {
                REGION_PREFIX = "ca-central-1."
                REGION_NAME = "Canada"
            }

            AuthRegion.US_EAST.value -> {
                REGION_PREFIX = "us-east-1."
                REGION_NAME = "US"
            }

            else -> {
                TJAuthLogger.e("[Network] invalid region=$region, fallback=KOREA")
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }
        }

        SERVER_TYPE = normalizedServerType
        USER_URL = "$HTTP_PREFIX${REGION_PREFIX}user.$normalizedServerType$SUFFIX"
        TJAuthLogger.d(
            "[Network] server configured provider=$normalizedProvider region=$normalizedRegion " +
                "serverType=$normalizedServerType baseUrl=$USER_URL"
        )
    }

    fun getUserBaseURL(): String {
        return USER_URL
    }

    fun getUserTokenVersion(): String {
        return when (SERVER_TYPE.lowercase()) {
            "jupiter" -> USER_JUPITER_TOKEN_SERVER_VERSION
            "phoenix" -> USER_PHOENIX_TOKEN_SERVER_VERSION
            "guardians" -> USER_GUARDIANS_TOKEN_SERVER_VERSION
            else -> USER_JUPITER_TOKEN_SERVER_VERSION
        }
    }

    class HeaderInterceptor(private val token: String) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val newRequest = chain.request().newBuilder()
                .addHeader("authorization", "Bearer $token")
                .build()
            return chain.proceed(newRequest)
        }
    }
}
