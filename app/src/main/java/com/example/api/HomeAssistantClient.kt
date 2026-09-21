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

    fun createService(baseUrl: String, token: String): HomeAssistantApi {
        val formattedUrl = formatBaseUrl(baseUrl)

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = buildLoggingInterceptor()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HomeAssistantApi::class.java)
    }

    fun createAuthService(baseUrl: String): HomeAssistantApi {
        val formattedUrl = formatBaseUrl(baseUrl)

        val loggingInterceptor = buildLoggingInterceptor()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
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
