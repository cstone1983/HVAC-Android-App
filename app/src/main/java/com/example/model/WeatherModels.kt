package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherDay(
    val dayLabel: String,         // "TODAY", "TOMORROW", "MONDAY", etc.
    val dateString: String,       // "2026-06-14"
    val avgHighTemp: Double,
    val avgLowTemp: Double,
    val condition: String,        // "Sunny", "Rainy", "Cloudy", "Stormy"
    val iconName: String,         // "sunny", "cloudy", "rainy", "stormy"
    val openMeteoHigh: Double?,
    val openMeteoLow: Double?,
    val metNoHigh: Double?,
    val metNoLow: Double?
)

@JsonClass(generateAdapter = true)
data class WeatherForecastState(
    val days: List<WeatherDay> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastFetched: Long = 0L,
    val resolvedLatitude: Double = 37.7749,
    val resolvedLongitude: Double = -122.4194
)
