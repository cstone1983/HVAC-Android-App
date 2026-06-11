package com.example.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object GithubClient {
    var tokenProvider: (() -> String?)? = null

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GithubApi by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val requestBuilder = request.newBuilder()
                .header("User-Agent", "HVAC-Android-App-Updater")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")

            val url = request.url.toString()
            if (url.contains("api.github.com")) {
                requestBuilder.header("Accept", "application/vnd.github.v3+json")
                tokenProvider?.invoke()?.let { token ->
                    val trimmed = token.trim()
                    if (trimmed.isNotBlank() && 
                        !trimmed.startsWith("YOUR_", ignoreCase = true) && 
                        !trimmed.startsWith("MY_", ignoreCase = true) && 
                        trimmed != "YOUR_GITHUB_PERSONAL_ACCESS_TOKEN"
                    ) {
                        val authHeaderValue = if (trimmed.startsWith("Bearer ", ignoreCase = true) || trimmed.startsWith("token ", ignoreCase = true)) {
                            trimmed
                        } else {
                            "Bearer $trimmed"
                        }
                        requestBuilder.header("Authorization", authHeaderValue)
                    }
                }
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)
    }
}
