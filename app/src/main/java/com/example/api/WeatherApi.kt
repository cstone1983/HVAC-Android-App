package com.example.api

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val daily: OpenMeteoDaily? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    val time: List<String>? = null,
    val temperature_2m_max: List<Double>? = null,
    val temperature_2m_min: List<Double>? = null,
    val weathercode: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class MetNoResponse(
    val properties: MetNoProperties? = null
)

@JsonClass(generateAdapter = true)
data class MetNoProperties(
    val timeseries: List<MetNoTimeseries>? = null
)

@JsonClass(generateAdapter = true)
data class MetNoTimeseries(
    val time: String? = null,
    val data: MetNoData? = null
)

@JsonClass(generateAdapter = true)
data class MetNoData(
    val instant: MetNoInstant? = null,
    val next_12_hours: MetNoHours? = null,
    val next_6_hours: MetNoHours? = null
)

@JsonClass(generateAdapter = true)
data class MetNoInstant(
    val details: MetNoDetails? = null
)

@JsonClass(generateAdapter = true)
data class MetNoDetails(
    val air_temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class MetNoHours(
    val summary: MetNoSummary? = null
)

@JsonClass(generateAdapter = true)
data class MetNoSummary(
    val symbol_code: String? = null
)

interface WeatherApi {
    @GET
    suspend fun getOpenMeteo(@Url url: String): Response<OpenMeteoResponse>

    @GET
    suspend fun getMetNo(
        @Url url: String,
        @Header("User-Agent") userAgent: String
    ): Response<MetNoResponse>
}
