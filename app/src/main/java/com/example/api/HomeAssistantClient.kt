package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object HomeAssistantClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun formatBaseUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "https://localhost/"
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * HTTP logging that cannot leak the long-lived token.
     *
     * This was `Level.BODY`, unconditionally — not gated on `BuildConfig.DEBUG` — while
     * [createService] adds `Authorization: Bearer <token>` to every request. The shipped builds
     * are debuggable, so `adb logcat` carried the Home Assistant token in clear text, which
     * quietly undid the masking added to the settings screen.
     *
     * It was also drowning the log: roughly 11,800 okhttp lines filled the entire main buffer in
     * under six minutes on both tablets, so nothing else survived long enough to be read when
     * something actually went wrong.
     */
    private fun buildLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (com.example.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

    /**
     * The token every request is signed with. Read by the shared interceptor at call time, so a
     * new token does not require a new client.
     */
    @Volatile
    private var activeToken: String = ""

    /**
     * One OkHttpClient for the whole app, built once.
     *
     * Every call to [createService] used to build its own, and each OkHttpClient brings a
     * ConnectionPool and a Dispatcher with its own thread pool. [initialize] is called on login,
     * from the foreground service, and — the expensive one — from the failover path in
     * `fetchStates`, twice per failed poll. During an outage that polls every 8 seconds, so the
     * app was minting a fresh connection pool and thread pool several times a minute and leaving
     * the old ones to expire on their own. Sharing one client also means sockets are actually
     * reused between the state poll, the history fetches and the service.
     */
    private val sharedClient: OkHttpClient by lazy {
        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Content-Type", "application/json")
            val token = activeToken
            if (token.isNotEmpty()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(buildLoggingInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            // The 30-day history queries are far slower to arrive than a state read. This used to
            // be 30s for everything, so a slow history response was cancelled part-way and then
            // retried, doubling the work at exactly the wrong moment.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createService(baseUrl: String, token: String): HomeAssistantApi {
        val formattedUrl = formatBaseUrl(baseUrl)
        activeToken = token

        // Retrofit instances are cheap; the client they share is not. Only the base URL differs
        // between primary and backup, so this is all that needs rebuilding on failover.
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(sharedClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HomeAssistantApi::class.java)
    }

    /**
     * Unauthenticated client for the login flow. Shares the connection pool and thread pool of
     * [sharedClient] rather than standing up a second one, but drops the auth header — logging in
     * is precisely when there is no token to send.
     */
    private val authFlowClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .also { it.interceptors().clear() }
            .addInterceptor(buildLoggingInterceptor())
            .build()
    }

    fun createAuthService(baseUrl: String): HomeAssistantApi {
        val formattedUrl = formatBaseUrl(baseUrl)

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(authFlowClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HomeAssistantApi::class.java)
    }

    @Volatile
    private var activeService: HomeAssistantApi? = null

    fun initialize(url: String, token: String) {
        activeService = createService(url, token)
    }

    val service: HomeAssistantApi
        get() {
            var s = activeService
            if (s == null) {
                synchronized(this) {
                    s = activeService
                    if (s == null) {
                        val buildUrl = try {
                            BuildConfig.HA_URL
                        } catch (e: Exception) {
                            ""
                        }
                        val buildToken = try {
                            BuildConfig.HA_TOKEN
                        } catch (e: Exception) {
                            ""
                        }
                        val finalUrl = if (buildUrl.isNotEmpty()) buildUrl else "https://localhost/"
                        s = createService(finalUrl, buildToken)
                        activeService = s
                    }
                }
            }
            return s!!
        }
}
