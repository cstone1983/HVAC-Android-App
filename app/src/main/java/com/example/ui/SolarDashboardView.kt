package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SolarLinePoint
import com.example.model.SolarBarPoint
import com.example.ui.theme.HvacThemeColors
import com.example.viewmodel.HvacViewModel
import kotlinx.coroutines.launch

@Composable
fun SolarDashboardView(
    viewModel: HvacViewModel,
    theme: HvacThemeColors,
    modifier: Modifier = Modifier
) {
    val liveState by viewModel.solarLiveState.collectAsStateWithLifecycle()
    val solar6HourHistory by viewModel.solar6HourHistory.collectAsStateWithLifecycle()
    val history24h by viewModel.solar24HourHistory.collectAsStateWithLifecycle()
    val solar7DayPowerHistory by viewModel.solar7DayPowerHistory.collectAsStateWithLifecycle()
    val dailyHistory by viewModel.solarDailyHistory.collectAsStateWithLifecycle()
    val weeklyHistory by viewModel.solarWeeklyHistory.collectAsStateWithLifecycle()
    val isFetching by viewModel.isSolarFetching.collectAsStateWithLifecycle()
    val error by viewModel.solarError.collectAsStateWithLifecycle()
    val diagnosticInfo by viewModel.solarDiagnosticInfo.collectAsStateWithLifecycle()

    var activeTrendTab by remember { mutableStateOf(1) } // 0: 6h, 1: 24h, 2: 7d

    val (todayGeneration, todayUsage) = remember(history24h, liveState, dailyHistory) {
        val estTz = java.util.TimeZone.getTimeZone("America/New_York")
        val cal = java.util.Calendar.getInstance(estTz)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val midnightMillis = cal.timeInMillis

        val pointsSinceMidnight = history24h.filter { it.epochMillis >= midnightMillis }
        if (pointsSinceMidnight.isEmpty()) {
            val todayBarPoint = dailyHistory.lastOrNull()
            Pair(todayBarPoint?.totalProducedKwh ?: 0f, todayBarPoint?.totalConsumedKwh ?: 0f)
        } else {
            val list = pointsSinceMidnight.toMutableList()
            if (liveState.isFetched) {
                val nowMs = System.currentTimeMillis()
                val lastPt = list.lastOrNull()
                if (lastPt != null && nowMs - lastPt.epochMillis < 300000L) { // 5 mins
                    list[list.size - 1] = SolarLinePoint(
                        timestampLabel = lastPt.timestampLabel,
                        epochMillis = nowMs,
                        usageWatts = liveState.liveUsageWatts,
                        productionWatts = liveState.liveProductionWatts
                    )
                } else {
                    val formattedNow = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
                        timeZone = estTz
                    }.format(java.util.Date(nowMs))
                    list.add(
                        SolarLinePoint(
                            timestampLabel = formattedNow,
                            epochMillis = nowMs,
                            usageWatts = liveState.liveUsageWatts,
                            productionWatts = liveState.liveProductionWatts
                        )
                    )
                }
            }
            val prodKwh = list.sumOf { it.productionWatts.toDouble() }.toFloat() / 6000f
            val consKwh = list.sumOf { it.usageWatts.toDouble() }.toFloat() / 6000f
            Pair(prodKwh, consKwh)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("solar_dashboard")
    ) {
        // Section header
        Text(
            text = "SOLAR & ENERGY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // A. The Main Status Banner (Live Net Meter)
        NetMeterCard(
            netPowerWatts = liveState.netPowerWatts,
            isExporting = liveState.isExporting,
            isFetched = liveState.isFetched,
            theme = theme
        )

        // B. Side-by-Side Metric Cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricCard(
                title = "CURRENT PRODUCTION",
                value = liveState.liveProductionWatts,
                icon = Icons.Default.WbSunny,
                iconColor = theme.ecoColor,
                isPower = true,
                lastUpdated = liveState.productionLastUpdated,
                modifier = Modifier.weight(1f),
                theme = theme
            )
            MetricCard(
                title = "GRID STATUS",
                value = liveState.liveUsageWatts,
                icon = Icons.Default.Bolt,
                iconColor = theme.coolColor,
                isPower = true,
                lastUpdated = liveState.usageLastUpdated,
                modifier = Modifier.weight(1f),
                theme = theme
            )
        }

        // C. Bottom Graph / Trends Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = theme.cardOpacity)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Trends card header + segment control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HISTORICAL TRENDS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Daily solar yields and consumption",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    if (isFetching) {
                        CircularProgressIndicator(
                            color = theme.ecoColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Segmented Button Row inside the card
                SegmentedButtonRow(
                    selectedIndex = activeTrendTab,
                    onSegmentSelected = { activeTrendTab = it },
                    theme = theme
                )

                // Error message banner
                error?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.boostColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Data Sync Warning: $err (Showing Offline Cache)",
                            fontSize = 11.sp,
                            color = theme.boostColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // The Chart Component
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    when (activeTrendTab) {
                        0 -> {
                            val live6hHistory = remember(solar6HourHistory, liveState) {
                                if (solar6HourHistory.isEmpty()) {
                                    emptyList()
                                } else {
                                    val list = solar6HourHistory.toMutableList()
                                    if (liveState.isFetched) {
                                        val nowMs = System.currentTimeMillis()
                                        val formattedNow = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
                                            timeZone = java.util.TimeZone.getTimeZone("America/New_York")
                                        }.format(java.util.Date(nowMs))
                                        val lastPt = list.lastOrNull()
                                        if (lastPt != null && nowMs - lastPt.epochMillis < 300000L) { // 5 mins
                                            list[list.size - 1] = SolarLinePoint(
                                                timestampLabel = formattedNow,
                                                epochMillis = nowMs,
                                                usageWatts = liveState.liveUsageWatts,
                                                productionWatts = liveState.liveProductionWatts
                                            )
                                        } else {
                                            list.add(
                                                SolarLinePoint(
                                                    timestampLabel = formattedNow,
                                                    epochMillis = nowMs,
                                                    usageWatts = liveState.liveUsageWatts,
                                                    productionWatts = liveState.liveProductionWatts
                                                )
                                            )
                                        }
                                    }
                                    list
                                }
                            }
                            if (live6hHistory.isNotEmpty()) {
                                LineChart6h(data = live6hHistory, theme = theme)
                            } else {
                                ChartEmptyState()
                            }
                        }
                        1 -> {
                            val liveHistory = remember(history24h, liveState) {
                                if (history24h.isEmpty()) {
                                    emptyList()
                                } else {
                                    val list = history24h.toMutableList()
                                    if (liveState.isFetched) {
                                        val nowMs = System.currentTimeMillis()
                                        val formattedNow = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
                                            timeZone = java.util.TimeZone.getTimeZone("America/New_York")
                                        }.format(java.util.Date(nowMs))
                                        val lastPt = list.lastOrNull()
                                        if (lastPt != null && nowMs - lastPt.epochMillis < 300000L) { // 5 mins
                                            list[list.size - 1] = SolarLinePoint(
                                                timestampLabel = formattedNow,
                                                epochMillis = nowMs,
                                                usageWatts = liveState.liveUsageWatts,
                                                productionWatts = liveState.liveProductionWatts
                                            )
                                        } else {
                                            list.add(
                                                SolarLinePoint(
                                                    timestampLabel = formattedNow,
                                                    epochMillis = nowMs,
                                                    usageWatts = liveState.liveUsageWatts,
                                                    productionWatts = liveState.liveProductionWatts
                                                )
                                            )
                                        }
                                    }
                                    list
                                }
                            }
                            if (liveHistory.isNotEmpty()) {
                                LineChart24h(data = liveHistory, theme = theme)
                            } else {
                                ChartEmptyState()
                            }
                        }
                        2 -> {
                            val live7DayHistory = remember(solar7DayPowerHistory, liveState) {
                                if (solar7DayPowerHistory.isEmpty()) {
                                    emptyList()
                                } else {
                                    val list = solar7DayPowerHistory.toMutableList()
                                    if (liveState.isFetched) {
                                        val nowMs = System.currentTimeMillis()
                                        val formattedNow = java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.US).apply {
                                            timeZone = java.util.TimeZone.getTimeZone("America/New_York")
                                        }.format(java.util.Date(nowMs))
                                        val lastPt = list.lastOrNull()
                                        if (lastPt != null && nowMs - lastPt.epochMillis < 3600000L) { // 1 hour
                                            list[list.size - 1] = SolarLinePoint(
                                                timestampLabel = formattedNow,
                                                epochMillis = nowMs,
                                                usageWatts = liveState.liveUsageWatts,
                                                productionWatts = liveState.liveProductionWatts
                                            )
                                        } else {
                                            list.add(
                                                SolarLinePoint(
                                                    timestampLabel = formattedNow,
                                                    epochMillis = nowMs,
                                                    usageWatts = liveState.liveUsageWatts,
                                                    productionWatts = liveState.liveProductionWatts
                                                )
                                            )
                                        }
                                    }
                                    list
                                }
                            }
                            if (live7DayHistory.isNotEmpty()) {
                                LineChart7d(data = live7DayHistory, theme = theme)
                            } else {
                                ChartEmptyState()
                            }
                        }
                    }
                }

                // Legend / Explanation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(theme.ecoColor, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "Solar Production",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(theme.coolColor, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "Energy Consumed",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Today's Generation card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = theme.ecoColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "TODAY'S GENERATION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f kWh", todayGeneration),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.testTag("today_generation_value")
                        )
                    }

                    // Today's Usage card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = theme.coolColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "TODAY'S USAGE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f kWh", todayUsage),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.testTag("today_usage_value")
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                Text(
                    text = "DAILY TOTALS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Grid Usage Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.015f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "GRID USAGE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 0.3.sp
                        )
                        val gridUsageValStr = if (liveState.dailyGridUsage != null) {
                            String.format(java.util.Locale.US, "%.2f %s", liveState.dailyGridUsage, liveState.dailyGridUsageUnit ?: "kWh")
                        } else {
                            "Unavailable"
                        }
                        Text(
                            text = gridUsageValStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.testTag("daily_grid_usage_value")
                        )
                    }

                    // Solar Generation Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.015f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SOLAR GEN",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 0.3.sp
                        )
                        val solarGenValStr = if (liveState.dailySolarGeneration != null) {
                            String.format(java.util.Locale.US, "%.2f %s", liveState.dailySolarGeneration, liveState.dailySolarGenerationUnit ?: "kWh")
                        } else {
                            "Unavailable"
                        }
                        Text(
                            text = solarGenValStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.testTag("daily_solar_generation_value")
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                Text(
                    text = "CMP BANK",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // CMP Bank Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.015f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CREDITS",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 0.3.sp
                        )
                        val cmpBankValStr = if (liveState.cmpBankBalance != null) {
                            String.format(java.util.Locale.US, "%.2f %s", liveState.cmpBankBalance, liveState.cmpBankBalanceUnit ?: "kWh")
                        } else {
                            "Unavailable"
                        }
                        Text(
                            text = cmpBankValStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.testTag("cmp_bank_balance_value")
                        )
                    }
                }
            }
        }

        // D. Collapsible Diagnostics Section
        var showDiagnostics by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = theme.cardOpacity)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .testTag("solar_diagnostic_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiagnostics = !showDiagnostics },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = theme.ecoColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "RAW DIAGNOSTIC DATA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Home Assistant sensor payload details",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                    IconButton(onClick = { showDiagnostics = !showDiagnostics }) {
                        Icon(
                            imageVector = if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle diagnostics",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                AnimatedVisibility(visible = showDiagnostics) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        
                        Text(
                            text = "Sync Status & Payloads:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.ecoColor
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = diagnosticInfo,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.LightGray,
                                lineHeight = 14.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.fetchSolarHistoryFromHA(force = true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.ecoColor.copy(alpha = 0.15f), contentColor = theme.ecoColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.6.dp))
                            Text("Force Refresh History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetMeterCard(
    netPowerWatts: Float,
    isExporting: Boolean,
    isFetched: Boolean,
    theme: HvacThemeColors
) {
    val accentColor = if (isExporting) theme.ecoColor else theme.coolColor
    val statusLabel = if (isExporting) "NET EXPORTING" else "NET IMPORTING"
    val formattedWatts = String.format(java.util.Locale.US, "%,.0f W", kotlin.math.abs(netPowerWatts))
    val textExplanation = if (isExporting) {
        "Your panels are producing excess clean power for the grid."
    } else {
        "You are supplementing solar yields with power from the grid."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = theme.cardOpacity)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .testTag("net_meter_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExporting) Icons.Default.SolarPower else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    if (!isFetched) {
                        Box(
                            modifier = Modifier
                                .background(theme.offColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "DEMO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.offColor
                            )
                        }
                    }
                }

                Text(
                    text = formattedWatts,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = textExplanation,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isPower: Boolean,
    lastUpdated: String? = null,
    theme: HvacThemeColors,
    modifier: Modifier = Modifier
) {
    val formattedValue = if (value >= 1000f) {
        String.format(java.util.Locale.US, "%.2f kW", value / 1000f)
    } else {
        String.format(java.util.Locale.US, "%.0f W", value)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = theme.cardOpacity)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .testTag("metric_card_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formattedValue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = formatFriendlyTime(lastUpdated).uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
fun SegmentedButtonRow(
    selectedIndex: Int,
    onSegmentSelected: (Int) -> Unit,
    theme: HvacThemeColors,
    modifier: Modifier = Modifier
) {
    val segments = listOf("6-Hour", "24-Hour", "7-Day")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(2.dp)
    ) {
        segments.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val itemBgColor = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent
            val itemTextColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(itemBgColor, RoundedCornerShape(16.dp))
                    .clickable { onSegmentSelected(index) }
                    .testTag("trend_segment_$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = itemTextColor,
                    fontSize = 11.sp,
                    fontWeight = fontWeight
                )
            }
        }
    }
}

@Composable
fun ChartEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Offline",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Home Assistant Connection Offline",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = "Unable to retrieve solar historical metrics",
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun LineChart6h(data: List<SolarLinePoint>, theme: HvacThemeColors) {
    val textMeasurer = rememberTextMeasurer()
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    }
                )
            }
            .pointerInput(data) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDrag = { change, _ ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = change.position.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDragEnd = {
                        selectedPointIndex = null
                    },
                    onDragCancel = {
                        selectedPointIndex = null
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("solar_6h_line_chart")
        ) {
            val width = size.width
            val height = size.height
            
            val leftPad = 8.dp.toPx()
            val rightPad = 56.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 24.dp.toPx()
            
            val effW = width - leftPad - rightPad
            val effH = height - topPad - bottomPad
    
            val maxVal = data.maxOfOrNull { maxOf(it.usageWatts, it.productionWatts) }?.coerceAtLeast(100f) ?: 1000f
    
            val gridCount = 3
            for (i in 0 until gridCount) {
                val fraction = i.toFloat() / (gridCount - 1)
                val y = topPad + fraction * effH
                
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + effW, y),
                    strokeWidth = 1f
                )
                
                val valAtGrid = maxVal * (1f - fraction)
                val labelText = if (valAtGrid >= 1000f) {
                    String.format(java.util.Locale.US, "%.1f kW", valAtGrid / 1000f)
                } else {
                    String.format(java.util.Locale.US, "%.0f W", valAtGrid)
                }
                
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        leftPad + effW + 6.dp.toPx(),
                        y - textLayoutResult.size.height / 2f
                    )
                )
            }
    
            if (data.size > 1) {
                val prodPath = Path()
                val usagePath = Path()
    
                data.forEachIndexed { idx, pt ->
                    val x = leftPad + (effW / (data.size - 1)) * idx
                    val yProd = topPad + (1f - (pt.productionWatts / maxVal)) * effH
                    val yUsage = topPad + (1f - (pt.usageWatts / maxVal)) * effH
    
                    if (idx == 0) {
                        prodPath.moveTo(x, yProd)
                        usagePath.moveTo(x, yUsage)
                    } else {
                        prodPath.lineTo(x, yProd)
                        usagePath.lineTo(x, yUsage)
                    }
                }
    
                drawPath(
                    path = prodPath,
                    color = theme.ecoColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
    
                drawPath(
                    path = usagePath,
                    color = theme.coolColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                
                val firstPt = data.firstOrNull()
                val lastPt = data.lastOrNull()
                if (firstPt != null && lastPt != null) {
                    val totalTime = lastPt.epochMillis - firstPt.epochMillis
                    val divisions = 6
                    val sdf12hr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("America/New_York")
                    }
                    
                    for (i in 0..divisions) {
                        val frac = i.toFloat() / divisions
                        val targetTime = firstPt.epochMillis + (totalTime * frac).toLong()
                        val x = leftPad + frac * effW
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1f
                        )
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x, topPad + effH),
                            end = Offset(x, topPad + effH + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                        
                        val labelText = sdf12hr.format(java.util.Date(targetTime))
                        val textLayoutResult = textMeasurer.measure(
                            text = labelText,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x - textLayoutResult.size.width / 2f,
                                topPad + effH + 6.dp.toPx()
                            )
                        )
                    }
                }
                
                selectedPointIndex?.let { selIdx ->
                    if (selIdx in data.indices) {
                        val pt = data[selIdx]
                        val x = leftPad + (effW / (data.size - 1)) * selIdx
                        val yProd = topPad + (1f - (pt.productionWatts / maxVal)) * effH
                        val yUsage = topPad + (1f - (pt.usageWatts / maxVal)) * effH
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        drawCircle(
                            color = theme.ecoColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        
                        drawCircle(
                            color = theme.coolColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                    }
                }
            }
        }
        
        selectedPointIndex?.let { selIdx ->
            if (selIdx in data.indices) {
                val pt = data[selIdx]
                val formattedProd = if (pt.productionWatts >= 1000f) String.format(java.util.Locale.US, "%.2f kW", pt.productionWatts / 1000f) else String.format(java.util.Locale.US, "%.0f W", pt.productionWatts)
                val formattedUsage = if (pt.usageWatts >= 1000f) String.format(java.util.Locale.US, "%.2f kW", pt.usageWatts / 1000f) else String.format(java.util.Locale.US, "%.0f W", pt.usageWatts)
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pt.timestampLabel,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prod: $formattedProd",
                                fontSize = 10.sp,
                                color = theme.ecoColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Usage: $formattedUsage",
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LineChart24h(data: List<SolarLinePoint>, theme: HvacThemeColors) {
    val textMeasurer = rememberTextMeasurer()
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    }
                )
            }
            .pointerInput(data) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDrag = { change, _ ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = change.position.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDragEnd = {
                        selectedPointIndex = null
                    },
                    onDragCancel = {
                        selectedPointIndex = null
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("solar_line_chart")
        ) {
            val width = size.width
            val height = size.height
            
            val leftPad = 8.dp.toPx()
            val rightPad = 56.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 24.dp.toPx()
            
            val effW = width - leftPad - rightPad
            val effH = height - topPad - bottomPad
    
            // Scaling values safely
            val maxVal = data.maxOfOrNull { maxOf(it.usageWatts, it.productionWatts) }?.coerceAtLeast(100f) ?: 1000f
    
            // Draw horizontal reference lines and Y labels (Watt amounts)
            val gridCount = 3
            for (i in 0 until gridCount) {
                val fraction = i.toFloat() / (gridCount - 1)
                val y = topPad + fraction * effH
                
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + effW, y),
                    strokeWidth = 1f
                )
                
                val valAtGrid = maxVal * (1f - fraction)
                val labelText = if (valAtGrid >= 1000f) {
                    String.format(java.util.Locale.US, "%.1f kW", valAtGrid / 1000f)
                } else {
                    String.format(java.util.Locale.US, "%.0f W", valAtGrid)
                }
                
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        leftPad + effW + 6.dp.toPx(),
                        y - textLayoutResult.size.height / 2f
                    )
                )
            }
    
            // 24h line curves (Usage & Production on the same scale, no fill, clean intersections)
            if (data.size > 1) {
                val prodPath = Path()
                val usagePath = Path()
    
                data.forEachIndexed { idx, pt ->
                    val x = leftPad + (effW / (data.size - 1)) * idx
                    val yProd = topPad + (1f - (pt.productionWatts / maxVal)) * effH
                    val yUsage = topPad + (1f - (pt.usageWatts / maxVal)) * effH
    
                    if (idx == 0) {
                        prodPath.moveTo(x, yProd)
                        usagePath.moveTo(x, yUsage)
                    } else {
                        prodPath.lineTo(x, yProd)
                        usagePath.lineTo(x, yUsage)
                    }
                }
    
                drawPath(
                    path = prodPath,
                    color = theme.ecoColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
    
                drawPath(
                    path = usagePath,
                    color = theme.coolColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Draw discrete timeline labels (subtle indicator & time text) - formatted in 12hr "h a" format
                val firstPt = data.firstOrNull()
                val lastPt = data.lastOrNull()
                if (firstPt != null && lastPt != null) {
                    val totalTime = lastPt.epochMillis - firstPt.epochMillis
                    val divisions = 8
                    val sdf12hr = java.text.SimpleDateFormat("h a", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("America/New_York")
                    }
                    
                    for (i in 0..divisions) {
                        val frac = i.toFloat() / divisions
                        val targetTime = firstPt.epochMillis + (totalTime * frac).toLong()
                        val x = leftPad + frac * effW
                        
                        // Vertical grid line for 3hr divisions
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1f
                        )
                        
                        // Bottom tick
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x, topPad + effH),
                            end = Offset(x, topPad + effH + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                        
                        val labelText = sdf12hr.format(java.util.Date(targetTime))
                        val textLayoutResult = textMeasurer.measure(
                            text = labelText,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x - textLayoutResult.size.width / 2f,
                                topPad + effH + 6.dp.toPx()
                            )
                        )
                    }
                }
                
                // Interactive Drag & Hover Guide + Indicator
                selectedPointIndex?.let { selIdx ->
                    if (selIdx in data.indices) {
                        val pt = data[selIdx]
                        val x = leftPad + (effW / (data.size - 1)) * selIdx
                        val yProd = topPad + (1f - (pt.productionWatts / maxVal)) * effH
                        val yUsage = topPad + (1f - (pt.usageWatts / maxVal)) * effH
                        
                        // Draw vertical tracker line
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // Highlight circles
                        drawCircle(
                            color = theme.ecoColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        
                        drawCircle(
                            color = theme.coolColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                    }
                }
            }
        }
        
        // Tooltip Overlay Composable
        selectedPointIndex?.let { selIdx ->
            if (selIdx in data.indices) {
                val pt = data[selIdx]
                val formattedProd = if (pt.productionWatts >= 1000f) String.format(java.util.Locale.US, "%.2f kW", pt.productionWatts / 1000f) else String.format(java.util.Locale.US, "%.0f W", pt.productionWatts)
                val formattedUsage = if (pt.usageWatts >= 1000f) String.format(java.util.Locale.US, "%.2f kW", pt.usageWatts / 1000f) else String.format(java.util.Locale.US, "%.0f W", pt.usageWatts)
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pt.timestampLabel,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prod: $formattedProd",
                                fontSize = 10.sp,
                                color = theme.ecoColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Usage: $formattedUsage",
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LineChart7d(data: List<SolarLinePoint>, theme: HvacThemeColors) {
    val textMeasurer = rememberTextMeasurer()
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    }
                )
            }
            .pointerInput(data) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDrag = { change, _ ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = change.position.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDragEnd = {
                        selectedPointIndex = null
                    },
                    onDragCancel = {
                        selectedPointIndex = null
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("solar_7d_line_chart")
        ) {
            val width = size.width
            val height = size.height
            
            val leftPad = 8.dp.toPx()
            val rightPad = 56.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 24.dp.toPx()
            
            val effW = width - leftPad - rightPad
            val effH = height - topPad - bottomPad
    
            val maxVal = data.maxOfOrNull { maxOf(it.usageWatts, it.productionWatts) }?.coerceAtLeast(100f) ?: 1000f
    
            val gridCount = 3
            for (i in 0 until gridCount) {
                val fraction = i.toFloat() / (gridCount - 1)
                val y = topPad + fraction * effH
                
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + effW, y),
                    strokeWidth = 1f
                )
                
                val valAtGrid = maxVal * (1f - fraction)
                val labelText = if (valAtGrid >= 1000f) {
                    String.format(java.util.Locale.US, "%.1f kW", valAtGrid / 1000f)
                } else {
                    String.format(java.util.Locale.US, "%.0f W", valAtGrid)
                }
                
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        leftPad + effW + 6.dp.toPx(),
                        y - textLayoutResult.size.height / 2f
                    )
                )
            }
    
            if (data.size > 1) {
                val prodPath = Path()
                val usagePath = Path()
    
                data.forEachIndexed { idx, pt ->
                    val x = leftPad + (effW / (data.size - 1)) * idx
                    val yProd = topPad + (1f - (pt.productionWatts / maxVal)) * effH
                    val yUsage = topPad + (1f - (pt.usageWatts / maxVal)) * effH
    
                    if (idx == 0) {
                        prodPath.moveTo(x, yProd)
                        usagePath.moveTo(x, yUsage)
                    } else {
                        prodPath.lineTo(x, yProd)
                        usagePath.lineTo(x, yUsage)
                    }
                }
    
                drawPath(
                    path = prodPath,
                    color = theme.ecoColor,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
    
                drawPath(
                    path = usagePath,
                    color = theme.coolColor,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
                
                val firstPt = data.firstOrNull()
                val lastPt = data.lastOrNull()
                if (firstPt != null && lastPt != null) {
                    val totalTime = lastPt.epochMillis - firstPt.epochMillis
                    val divisions = 7
                    val sdfDay = java.text.SimpleDateFormat("EEE", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("America/New_York")
                    }
                    
                    for (i in 0..divisions) {
                        val frac = i.toFloat() / divisions
                        val targetTime = firstPt.epochMillis + (totalTime * frac).toLong()
                        val x = leftPad + frac * effW
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1f
                        )
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x, topPad + effH),
                            end = Offset(x, topPad + effH + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                        
                        if (i < divisions) {
                            val labelText = sdfDay.format(java.util.Date(targetTime))
                            val textLayoutResult = textMeasurer.measure(
                                text = labelText,
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            val nextX = leftPad + ((i + 1).toFloat() / divisions) * effW
                            val midX = (x + nextX) / 2f
                            
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    midX - textLayoutResult.size.width / 2f,
                                    topPad + effH + 6.dp.toPx()
                                )
                            )
                        }
                    }
                }
                
                selectedPointIndex?.let { selIdx ->
                    if (selIdx in data.indices) {
                        val pt = data[selIdx]
                        val x = leftPad + (effW / (data.size - 1)) * selIdx
                        val yProd = topPad + (1f - (pt.productionWatts / maxVal)) * effH
                        val yUsage = topPad + (1f - (pt.usageWatts / maxVal)) * effH
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        drawCircle(
                            color = theme.ecoColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        
                        drawCircle(
                            color = theme.coolColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                    }
                }
            }
        }
        
        selectedPointIndex?.let { selIdx ->
            if (selIdx in data.indices) {
                val pt = data[selIdx]
                val formattedProd = if (pt.productionWatts >= 1000f) String.format(java.util.Locale.US, "%.2f kW", pt.productionWatts / 1000f) else String.format(java.util.Locale.US, "%.0f W", pt.productionWatts)
                val formattedUsage = if (pt.usageWatts >= 1000f) String.format(java.util.Locale.US, "%.2f kW", pt.usageWatts / 1000f) else String.format(java.util.Locale.US, "%.0f W", pt.usageWatts)
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pt.timestampLabel,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prod: $formattedProd",
                                fontSize = 10.sp,
                                color = theme.ecoColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Usage: $formattedUsage",
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalLineChart(data: List<SolarBarPoint>, isMonth: Boolean, theme: HvacThemeColors) {
    val textMeasurer = rememberTextMeasurer()
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    }
                )
            }
            .pointerInput(data) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = offset.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDrag = { change, _ ->
                        val width = size.width
                        val leftPad = 8.dp.toPx()
                        val rightPad = 56.dp.toPx()
                        val effW = width - leftPad - rightPad
                        if (effW > 0 && data.isNotEmpty()) {
                            val rawX = change.position.x - leftPad
                            val fraction = (rawX / effW).coerceIn(0f, 1f)
                            val idx = (fraction * (data.size - 1)).roundToInt()
                            selectedPointIndex = idx.coerceIn(data.indices)
                        }
                    },
                    onDragEnd = {
                        selectedPointIndex = null
                    },
                    onDragCancel = {
                        selectedPointIndex = null
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag(if (isMonth) "solar_monthly_chart" else "solar_weekly_chart")
        ) {
            val width = size.width
            val height = size.height
            
            val leftPad = 8.dp.toPx()
            val rightPad = 56.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 24.dp.toPx()
            
            val effW = width - leftPad - rightPad
            val effH = height - topPad - bottomPad
    
            val maxVal = data.maxOfOrNull { maxOf(it.totalConsumedKwh, it.totalProducedKwh) }?.coerceAtLeast(1f) ?: 10f
    
            // Draw horizontal reference lines and Y labels (kWh amounts)
            val gridCount = 3
            for (i in 0 until gridCount) {
                val fraction = i.toFloat() / (gridCount - 1)
                val y = topPad + fraction * effH
                
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + effW, y),
                    strokeWidth = 1f
                )
                
                val valAtGrid = maxVal * (1f - fraction)
                val labelText = String.format(java.util.Locale.US, "%.1f kWh", valAtGrid)
                
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        leftPad + effW + 6.dp.toPx(),
                        y - textLayoutResult.size.height / 2f
                    )
                )
            }
    
            if (data.size > 1) {
                val prodPath = Path()
                val usagePath = Path()
    
                data.forEachIndexed { idx, pt ->
                    val x = leftPad + (effW / (data.size - 1)) * idx
                    val yProd = topPad + (1f - (pt.totalProducedKwh / maxVal)) * effH
                    val yUsage = topPad + (1f - (pt.totalConsumedKwh / maxVal)) * effH
    
                    if (idx == 0) {
                        prodPath.moveTo(x, yProd)
                        usagePath.moveTo(x, yUsage)
                    } else {
                        prodPath.lineTo(x, yProd)
                        usagePath.lineTo(x, yUsage)
                    }
                }
    
                drawPath(
                    path = prodPath,
                    color = theme.ecoColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
    
                drawPath(
                    path = usagePath,
                    color = theme.coolColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                
                if (isMonth) {
                    val divisions = listOf(0, 7, 14, 21, data.size - 1).filter { it < data.size }
                    divisions.forEach { idx ->
                        val x = leftPad + (idx.toFloat() / (data.size - 1)) * effW
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1f
                        )
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x, topPad + effH),
                            end = Offset(x, topPad + effH + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                        
                        val textLayoutResult = textMeasurer.measure(
                            text = data[idx].intervalLabel,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x - textLayoutResult.size.width / 2f,
                                topPad + effH + 6.dp.toPx()
                            )
                        )
                    }
                } else {
                    data.forEachIndexed { idx, pt ->
                        val x = leftPad + (idx.toFloat() / (data.size - 1)) * effW
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1f
                        )
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x, topPad + effH),
                            end = Offset(x, topPad + effH + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                        
                        val textLayoutResult = textMeasurer.measure(
                            text = pt.intervalLabel,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x - textLayoutResult.size.width / 2f,
                                topPad + effH + 6.dp.toPx()
                            )
                        )
                    }
                }
                
                selectedPointIndex?.let { selIdx ->
                    if (selIdx in data.indices) {
                        val pt = data[selIdx]
                        val x = leftPad + (effW / (data.size - 1)) * selIdx
                        val yProd = topPad + (1f - (pt.totalProducedKwh / maxVal)) * effH
                        val yUsage = topPad + (1f - (pt.totalConsumedKwh / maxVal)) * effH
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + effH),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        drawCircle(
                            color = theme.ecoColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yProd)
                        )
                        
                        drawCircle(
                            color = theme.coolColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x, yUsage)
                        )
                    }
                }
            }
        }
        
        selectedPointIndex?.let { selIdx ->
            if (selIdx in data.indices) {
                val pt = data[selIdx]
                val formattedProd = String.format(java.util.Locale.US, "%.1f kWh", pt.totalProducedKwh)
                val formattedUsage = String.format(java.util.Locale.US, "%.1f kWh", pt.totalConsumedKwh)
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pt.intervalLabel,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prod: $formattedProd",
                                fontSize = 10.sp,
                                color = theme.ecoColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Usage: $formattedUsage",
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
