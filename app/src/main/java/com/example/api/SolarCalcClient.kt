package com.example.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Talks to Solar Calc. Modelled on [WeatherClient], with two differences worth knowing.
 *
 * **No logging interceptor.** The weather client logs headers; this polls every minute on three
 * panels, all day, and the traffic is uninteresting when it works.
 *
 * **One client, any base URL.** [SolarCalcApi.today] takes a full URL rather than a path, so the
 * base URL can be changed in `layout_config.json` over the air without rebuilding Retrofit. The
 * base URL still has to be given to the builder, so a placeholder goes in; nothing ever resolves
 * against it.
 */
object SolarCalcClient {

    /** The default, which works both at home and away. Overridable from layout_config.json. */
    const val DEFAULT_BASE_URL = "https://solar.stoneyshome.com/"

    private const val TODAY_PATH = "api/app/v1/today"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: SolarCalcApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            // Never used: every call passes an absolute URL. Retrofit demands one anyway.
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SolarCalcApi::class.java)
    }

    /**
     * Joins the configured base to the endpoint path, tolerating a trailing slash or its absence.
     *
     * Trivial, and it has its own function because the alternative is a string concatenation at
     * the call site that produces a double slash the first time somebody edits the config without
     * one — which 404s, from a config file, with nothing in the app to explain why.
     */
    fun todayUrl(baseUrl: String?): String {
        val base = baseUrl?.trim().orEmpty().ifEmpty { DEFAULT_BASE_URL }
        return base.trimEnd('/') + "/" + TODAY_PATH
    }
}
