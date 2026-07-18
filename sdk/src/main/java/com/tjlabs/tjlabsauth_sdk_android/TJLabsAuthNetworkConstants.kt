package com.tjlabs.tjlabsauth_sdk_android

import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsAuthNetworkConstants {
    private const val USER_JUPITER_TOKEN_SERVER_VERSION = "2026-06-16"
    private const val USER_PHOENIX_TOKEN_SERVER_VERSION = "2025-06-11"
    private const val USER_GUARDIANS_TOKEN_SERVER_VERSION = "2025-06-30"

    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    // 환경별 도메인 suffix.
    //  PROD : 실 운영 도메인 (.tjlabscorp.com)
    //  DEV  : 내부 테스트 도메인 (.tjlabs.dev)
    private const val SUFFIX_PROD = ".tjlabscorp.com"
    private const val SUFFIX_DEV  = ".tjlabs.dev"
    private var currentSuffix = SUFFIX_PROD    // 기본 PROD
    private var SERVER_TYPE: String = "jupiter"
    private var USER_URL = "$HTTP_PREFIX${REGION_PREFIX}user.$SERVER_TYPE$currentSuffix"

    // Single shared OkHttpClient across all auth() / refresh() calls.
    // Connection pool, DNS cache, TLS session cache, and dispatcher are reused.
    // Recreating the client per-call was the dominant cold-path cost: every auth()
    // re-did DNS resolution + TCP handshake + TLS handshake (~500–800ms on GCP
    // asia-northeast3 from a Korean mobile network).
    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            // Keep idle TLS connections alive long enough to amortize across user actions.
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .eventListenerFactory(object : EventListener.Factory {
                override fun create(call: Call): EventListener = TJLabsAuthEventListener()
            })
            .build()
    }

    // Retrofit instance cached per (baseUrl + bearerToken) tuple.
    // setServerURL() invalidates the cache so the next call picks up the new baseUrl.
    @Volatile
    private var cachedRetrofitKey: String = ""
    @Volatile
    private var cachedRetrofit: Retrofit? = null

    fun genRetrofit(bearerToken: String? = null): Retrofit {
        val key = USER_URL + "|" + (bearerToken ?: "")
        cachedRetrofit?.let { if (cachedRetrofitKey == key) return it }
        return synchronized(this) {
            cachedRetrofit?.let { if (cachedRetrofitKey == key) return it }
            val client = if (bearerToken.isNullOrBlank()) {
                sharedClient
            } else {
                sharedClient.newBuilder()
                    .addInterceptor(HeaderInterceptor(bearerToken))
                    .build()
            }
            val retrofit = Retrofit.Builder()
                .baseUrl(USER_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
            cachedRetrofit = retrofit
            cachedRetrofitKey = key
            retrofit
        }
    }

    /** Returns the shared OkHttpClient for low-level use (e.g. prewarm HEAD requests). */
    fun sharedOkHttpClient(): OkHttpClient = sharedClient

    fun setServerURL(
        provider: String,
        region: String,
        serverType: String,
        env: AuthServerEnv = AuthServerEnv.PROD,
    ) {
        val normalizedRegion = region.uppercase()
        val normalizedProvider = provider.lowercase()
        val normalizedServerType = serverType.ifBlank { "jupiter" }.lowercase()
        currentSuffix = when (env) {
            AuthServerEnv.PROD -> SUFFIX_PROD
            AuthServerEnv.DEV_TESTING_ONLY -> SUFFIX_DEV
        }

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
            }

            AuthRegion.CANADA.value -> {
                REGION_PREFIX = "ca-central-1."
            }

            AuthRegion.US_EAST.value -> {
                REGION_PREFIX = "us-east-1."
            }
            AuthRegion.SAUDI.value -> {
                REGION_PREFIX = "me-central2."
            }

            else -> {
                TJAuthLogger.e("[Network] invalid region=$region, fallback=KOREA")
                REGION_PREFIX = "ap-northeast-2."
            }
        }

        SERVER_TYPE = normalizedServerType
        val newUrl = "$HTTP_PREFIX${REGION_PREFIX}user.$normalizedServerType$currentSuffix"
        if (newUrl != USER_URL) {
            USER_URL = newUrl
            // Force Retrofit rebuild on next call so the new baseUrl is picked up.
            // The shared OkHttpClient (connection pool, DNS cache) is preserved.
            cachedRetrofit = null
            cachedRetrofitKey = ""
        }
        TJAuthLogger.d(
            "[Network] server configured provider=$normalizedProvider region=$normalizedRegion " +
                "serverType=$normalizedServerType env=$env baseUrl=$USER_URL"
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
