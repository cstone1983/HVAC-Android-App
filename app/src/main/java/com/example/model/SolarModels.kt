package com.example.model

data class SolarLiveState(
    val liveUsageWatts: Float = 0f,
    val liveProductionWatts: Float = 0f,
    val isFetched: Boolean = false,
    val isError: Boolean = false,
    val lastUpdated: String? = null,
    val productionLastUpdated: String? = null,
    val usageLastUpdated: String? = null,
    val diagSolarGeneration: Float? = null,
    val diagSolarProductionDaily: Float? = null,
    val diagSolarGenerationUnit: String? = null,
    val diagSolarProductionDailyUnit: String? = null,
    val diagPanelUsage: Float? = null,
    val diagDailyPanelUsage: Float? = null,
    val diagPanelUsageUnit: String? = null,
    val diagDailyPanelUsageUnit: String? = null,
    val solcastForecastToday: Float? = null,
    val solcastForecastTodayUnit: String? = null,
    val solcastForecastNow: Float? = null,
    val solcastForecastNowUnit: String? = null,
    val dailyGridUsage: Float? = null,
    val dailyGridUsageUnit: String? = null,
    val dailySolarGeneration: Float? = null,
    val dailySolarGenerationUnit: String? = null,
    val cmpBankBalance: Float? = null,
    val cmpBankBalanceUnit: String? = null
) {
    val netPowerWatts: Float get() = liveProductionWatts - liveUsageWatts
    val isExporting: Boolean get() = netPowerWatts >= 0f
}

data class SolarLinePoint(
    val timestampLabel: String,
    val epochMillis: Long,
    val usageWatts: Float,
    val productionWatts: Float
)

data class SolarBarPoint(
    val intervalLabel: String,
    val totalConsumedKwh: Float,
    val totalProducedKwh: Float
)
