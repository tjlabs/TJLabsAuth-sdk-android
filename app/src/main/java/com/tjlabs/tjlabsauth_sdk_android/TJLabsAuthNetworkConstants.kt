package com.tjlabs.tjlabsauth_sdk_android

import AuthRegion
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsAuthNetworkConstants {
    private const val USER_JUPITER_TOKEN_SERVER_VERSION = "2025-03-25"
    private const val USER_PHOENIX_TOKEN_SERVER_VERSION = "2025-06-11"
    private const val USER_GUARDIANS_TOKEN_SERVER_VERSION = "2025-06-30"


    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    private const val SUFFIX = ".tjlabs.dev"
    private var REGION_NAME = "Korea"

    private var USER_URL = HTTP_PREFIX + REGION_PREFIX + "user"
    private var SERVER_TYPE: String = "jupiter"

    fun genRetrofit(token: String?): Retrofit {
        val okHttpClientBuilder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)

        // token이 있을 경우에만 Interceptor 추가
        token?.let {
            okHttpClientBuilder.addInterceptor(HeaderInterceptor(it))
        }

        return Retrofit.Builder()
            .baseUrl(USER_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClientBuilder.build())
            .build()
    }


    fun setServerURL(region: String, serverType: String) {
        when (region) {
            AuthRegion.KOREA -> {
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }

            AuthRegion.CANADA -> {
                REGION_PREFIX = "ca-central-1."
                REGION_NAME = "Canada"
            }

            AuthRegion.US_EAST -> {
                REGION_PREFIX = "us-east-1."
                REGION_NAME = "US"
            }

            else -> {
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }
        }

        SERVER_TYPE = serverType
        USER_URL = "$HTTP_PREFIX${REGION_PREFIX}user.$serverType$SUFFIX"
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
            val token = "Bearer $token"
            val newRequest = chain.request().newBuilder()
                .addHeader("authorization", token)
                .build()
            return chain.proceed(newRequest)
        }
    }
}

