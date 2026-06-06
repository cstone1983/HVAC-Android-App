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

    fun createService(baseUrl: String, token: String): HomeAssistantApi {
        val formattedUrl = formatBaseUrl(baseUrl)

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS // Don't overwhelm logs but capture responses
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HomeAssistantApi::class.java)
    }

    val service: HomeAssistantApi by lazy {
        val url = try {
            BuildConfig.HA_URL
        } catch (e: Exception) {
            "https://localhost/"
        }
        val token = try {
            BuildConfig.HA_TOKEN
        } catch (e: Exception) {
            ""
        }
        createService(url, token)
    }
}
