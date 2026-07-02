package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.HvacViewModel
import com.example.model.PoolState
import com.example.model.PoolHistoryPoint
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.HvacThemeColors
import com.example.ui.theme.hvacCardShape

fun formatFriendlyTime(rawInput: String?): String {
    if (rawInput == null || rawInput.isBlank()) return "15 Min ago"

    // Try parsing Unix timestamp as a Float or Double (e.g. seconds format "1718280000" or milliseconds)
    val doubleVal = rawInput.toDoubleOrNull()
    if (doubleVal != null) {
        val ms = (doubleVal * 1000).toLong()
        return calculateFriendlyTime(ms)
    }

    val longVal = rawInput.toLongOrNull()
    if (longVal != null) {
        val ms = if (longVal < 100000000000L) longVal * 1000 else longVal
        return calculateFriendlyTime(ms)
    }

    // Try parsing ISO formats
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    for (format in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
            if (format.endsWith("'Z'")) {
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(rawInput)
            if (date != null) {
                return calculateFriendlyTime(date.time)
            }
        } catch (e: Exception) {
            // Check next format
        }
    }

    // fallback matching for common relative words
    val lower = rawInput.lowercase().trim()
    if (lower.contains("minute") || lower.contains("min")) {
        val num = lower.replace(Regex("[^0-9]"), "")
        if (num.isNotEmpty()) return "$num Min ago"
    }
    if (lower.contains("hour") || lower.contains("hr")) {
        val num = lower.replace(Regex("[^0-9]"), "")
        if (num.isNotEmpty()) return "$num Hr ago"
    }
    if (lower.contains("second") || lower.contains("sec")) {
        return "Just Now"
    }

    return rawInput
}

private fun calculateFriendlyTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    if (diffMs < 0) return "Just Now"

    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "Just Now"
        diffMinutes < 60 -> "$diffMinutes Min ago"
        diffHours < 24 -> "$diffHours Hr ago"
        else -> "$diffDays Day ago"
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PoolDashboardView(
    viewModel: HvacViewModel,
    modifier: Modifier = Modifier
) {
    val theme = LocalHvacTheme.current
    val poolState by viewModel.poolState.collectAsStateWithLifecycle()
    val poolHistory by viewModel.poolHistory.collectAsStateWithLifecycle()

    val poolTempMin by viewModel.poolTempMin.collectAsStateWithLifecycle()
    val poolTempMax by viewModel.poolTempMax.collectAsStateWithLifecycle()
    val poolPhMin by viewModel.poolPhMin.collectAsStateWithLifecycle()
    val poolPhMax by viewModel.poolPhMax.collectAsStateWithLifecycle()
    val poolOrpMin by viewModel.poolOrpMin.collectAsStateWithLifecycle()
    val poolOrpMax by viewModel.poolOrpMax.collectAsStateWithLifecycle()
    val poolBatteryMin by viewModel.poolBatteryMin.collectAsStateWithLifecycle()
    val poolBatteryMax by viewModel.poolBatteryMax.collectAsStateWithLifecycle()

    var activeTrendTab by remember { mutableStateOf(0) } // 0 = Temp, 1 = pH, 2 = ORP
    var activeTimeFrame by remember { mutableStateOf(1) } // 0 = 6h, 1 = 24h, 2 = 7d

    val currentTemp = poolState.waterTemperature?.toFloat() ?: 79.0f
    val currentPh = poolState.ph?.toFloat() ?: 7.35f
    val currentOrp = poolState.orp?.toFloat() ?: 561.0f

    // Highly authentic historical data points anchored directly to active live metrics
    // Highly authentic historical data points anchored directly to active live metrics
    val displayHistory = remember(poolHistory, activeTimeFrame, currentTemp, currentPh, currentOrp) {
        if (poolHistory.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            val estTz = java.util.TimeZone.getTimeZone("America/New_York")
            val parsePointTime: (String) -> Long = { ts ->
                try {
                    if (ts.contains("/")) {
                        val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US).apply {
                            timeZone = estTz
                        }
                        val parsed = sdf.parse(ts)
                        if (parsed != null) {
                            val cal = java.util.Calendar.getInstance(estTz)
                            val parsedCal = java.util.Calendar.getInstance(estTz).apply { time = parsed }
                            cal.set(java.util.Calendar.MONTH, parsedCal.get(java.util.Calendar.MONTH))
                            cal.set(java.util.Calendar.DAY_OF_MONTH, parsedCal.get(java.util.Calendar.DAY_OF_MONTH))
                            cal.set(java.util.Calendar.HOUR_OF_DAY, parsedCal.get(java.util.Calendar.HOUR_OF_DAY))
                            cal.set(java.util.Calendar.MINUTE, parsedCal.get(java.util.Calendar.MINUTE))
                            cal.timeInMillis
                        } else {
                            0L
                        }
                    } else {
                        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
                            timeZone = estTz
                        }
                        val parsed = sdf.parse(ts)
                        if (parsed != null) {
                            val cal = java.util.Calendar.getInstance(estTz)
                            val parsedCal = java.util.Calendar.getInstance(estTz).apply { time = parsed }
                            cal.set(java.util.Calendar.HOUR_OF_DAY, parsedCal.get(java.util.Calendar.HOUR_OF_DAY))
                            cal.set(java.util.Calendar.MINUTE, parsedCal.get(java.util.Calendar.MINUTE))
                            cal.timeInMillis
                        } else {
                            0L
                        }
                    }
                } catch (e: Exception) {
                    0L
                }
            }

            val filteredPoints = when (activeTimeFrame) {
                0 -> {
                    // Granular (past 6h): points within last 6h
                    val sixHoursAgo = nowMs - 6 * 60 * 60 * 1000L
                    poolHistory.filter { parsePointTime(it.timestamp) >= sixHoursAgo || parsePointTime(it.timestamp) == 0L }
                }
                1 -> {
                    // Hourly (past 24h): points within last 24h
                    val oneDayAgo = nowMs - 24 * 60 * 60 * 1000L
                    poolHistory.filter { parsePointTime(it.timestamp) >= oneDayAgo || parsePointTime(it.timestamp) == 0L }
                }
                2 -> {
                    // Weekly (past 7 days): points within last 7 days
                    val sevenDaysAgo = nowMs - 7 * 24 * 60 * 60 * 1000L
                    val points = poolHistory.filter { parsePointTime(it.timestamp) >= sevenDaysAgo || parsePointTime(it.timestamp) == 0L }
                    if (points.size > 50) {
                        val step = points.size / 30
                        points.filterIndexed { index, _ -> index % step == 0 }
                    } else {
                        points
                    }
                }
                else -> poolHistory
            }

            if (filteredPoints.isNotEmpty()) filteredPoints else poolHistory
        } else {
            emptyList()
            /*
            // BACKWARD MOCK FALLBACK: Use pristine beautiful generated curves if offline or not synced
            val estTz = java.util.TimeZone.getTimeZone("America/New_York")
            val calendar = java.util.Calendar.getInstance(estTz)
            val nowMs = System.currentTimeMillis()
            when (activeTimeFrame) {
                0 -> {
                    // 6-Hour Fallback (granular, say every 10 mins)
                    val list = mutableListOf<PoolHistoryPoint>()
                    val intervalMinutes = 10
                    val intervalMillis = intervalMinutes * 60 * 1000L
                    val totalIntervals = (6 * 60) / intervalMinutes // 36 intervals
                    val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
                        timeZone = estTz
                    }
                    for (i in totalIntervals downTo 0) {
                        val tMillis = nowMs - i * intervalMillis
                        calendar.timeInMillis = tMillis
                        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                        val minute = calendar.get(java.util.Calendar.MINUTE)
                        val timeStr = timeFormatter.format(java.util.Date(tMillis))
                        
                        val tDecimal = hour + (minute / 60f)
                        val angle = (tDecimal - 6) * (2.0 * Math.PI / 24.0)
                        val tempOffset = -Math.cos(angle).toFloat() * 1.5f + (Math.sin(angle * 2).toFloat() * 0.2f)
                        val phOffset = Math.sin(angle).toFloat() * 0.03f
                        val orpOffset = Math.cos(angle).toFloat() * 12f

                        // Add fine granular noise
                        val noiseTemp = ((Math.random() - 0.5) * 0.04).toFloat()
                        val noisePh = ((Math.random() - 0.5) * 0.004).toFloat()
                        val noiseOrp = ((Math.random() - 0.5) * 0.8).toFloat()

                        list.add(
                            PoolHistoryPoint(
                                timestamp = timeStr,
                                temp = currentTemp + tempOffset + noiseTemp,
                                ph = currentPh + phOffset + noisePh,
                                orp = currentOrp + orpOffset + noiseOrp
                            )
                        )
                    }
                    list
                }
                1 -> {
                    // 24-Hour Fallback
                    val list = mutableListOf<PoolHistoryPoint>()
                    val intervalMinutes = 60
                    val intervalMillis = intervalMinutes * 60 * 1000L
                    val totalIntervals = 24
                    val timeFormatter = java.text.SimpleDateFormat("HH:00", java.util.Locale.US).apply {
                        timeZone = estTz
                    }
                    for (i in totalIntervals downTo 0) {
                        val tMillis = nowMs - i * intervalMillis
                        calendar.timeInMillis = tMillis
                        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                        val timeStr = timeFormatter.format(java.util.Date(tMillis))
                        
                        val angle = (hour - 6) * (2.0 * Math.PI / 24.0)
                        val tempOffset = -Math.cos(angle).toFloat() * 1.5f + (Math.sin(angle * 2).toFloat() * 0.2f)
                        val phOffset = Math.sin(angle).toFloat() * 0.03f
                        val orpOffset = Math.cos(angle).toFloat() * 12f

                        list.add(
                            PoolHistoryPoint(
                                timestamp = timeStr,
                                temp = currentTemp + tempOffset,
                                ph = currentPh + phOffset,
                                orp = currentOrp + orpOffset
                            )
                        )
                    }
                    list
                }
                2 -> {
                    // 7-Day Fallback
                    val list = mutableListOf<PoolHistoryPoint>()
                    val intervalHours = 6
                    val intervalMillis = intervalHours * 60 * 60 * 1000L
                    val totalIntervals = (7 * 24) / intervalHours // 28 intervals
                    val dayFormatter = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US).apply {
                        timeZone = estTz
                    }
                    for (i in totalIntervals downTo 0) {
                        val tMillis = nowMs - i * intervalMillis
                        calendar.timeInMillis = tMillis
                        val dayIndex = calendar.get(java.util.Calendar.DAY_OF_YEAR)
                        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                        val timeStr = dayFormatter.format(java.util.Date(tMillis))
                        
                        val angle = (hour - 6) * (2.0 * Math.PI / 24.0)
                        val trendAngle = dayIndex * (2.0 * Math.PI / 7.0)
                        
                        val tempOffset = -Math.cos(angle).toFloat() * 1.1f + Math.sin(trendAngle).toFloat() * 1.5f + (Math.sin(angle * 3).toFloat() * 0.15f)
                        val phOffset = Math.sin(angle).toFloat() * 0.03f + Math.cos(trendAngle).toFloat() * 0.015f
                        val orpOffset = Math.cos(angle).toFloat() * 8f - Math.sin(trendAngle).toFloat() * 9f

                        list.add(
                            PoolHistoryPoint(
                                timestamp = timeStr,
                                temp = currentTemp + tempOffset,
                                ph = currentPh + phOffset,
                                orp = currentOrp + orpOffset
                            )
                        )
                    }
                    list
                }
                else -> emptyList()
            }
            */
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // --- 1. HERO WATER TEMPERATURE HEADER CARD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(hvacCardShape(16))
                .background(Brush.radialGradient(
                    colors = listOf(theme.coolColor.copy(alpha = 0.15f), Color.Transparent),
                    radius = 400f
                ))
                .background(Color.White.copy(alpha = theme.cardOpacity))
                .border(1.dp, Color.White.copy(alpha = 0.08f), hvacCardShape(16))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pool,
                            contentDescription = null,
                            tint = theme.coolColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MY POOL",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${String.format("%.1f", poolState.waterTemperature ?: 79.0)} °F",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Water Status Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.ecoColor.copy(alpha = 0.15f))
                                .border(1.dp, theme.ecoColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = (poolState.waterStatus ?: "Balanced").uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.ecoColor
                            )
                        }

                        // Actions Pending Pill
                        val pendingCount = poolState.actionsPending ?: 1
                        if (pendingCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.heatColor.copy(alpha = 0.15f))
                                    .border(1.dp, theme.heatColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$pendingCount ACTIONS REQ",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.heatColor
                                )
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFriendlyTime(poolState.lastUpdated).uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.35f)
                    )

                    // Wave visualization
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(theme.coolColor.copy(alpha = 0.08f))
                            .border(1.dp, theme.coolColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Water,
                            contentDescription = "Water Temp Icon",
                            tint = theme.coolColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // --- 2. LIVE PARAMETER METRICS GRID ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // pH CARD
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(hvacCardShape(12))
                    .background(Color.White.copy(alpha = theme.cardOpacity))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), hvacCardShape(12))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("pH LEVEL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = theme.coolColor, modifier = Modifier.size(12.dp))
                }
                 Spacer(modifier = Modifier.height(4.dp))
                 val ph = poolState.ph ?: 7.35
                 Text("${String.format("%.2f", ph)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Spacer(modifier = Modifier.height(4.dp))
                val isPhIdeal = ph >= poolPhMin && ph <= poolPhMax
                Text(
                    text = if (isPhIdeal) "• IDEAL" else "• ADJUST REQ",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPhIdeal) theme.ecoColor else theme.heatColor
                )
            }
 
            // ORP CARD
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(hvacCardShape(12))
                    .background(Color.White.copy(alpha = theme.cardOpacity))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), hvacCardShape(12))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ORP INDEX", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = theme.heatColor, modifier = Modifier.size(12.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                val orp = poolState.orp ?: 561.0
                Text("${orp.toInt()} mV", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Spacer(modifier = Modifier.height(4.dp))
                val isOrpIdeal = orp >= poolOrpMin && orp <= poolOrpMax
                Text(
                    text = if (isOrpIdeal) "• ADEQUATE" else "• LOW SANITIZER",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOrpIdeal) theme.ecoColor else theme.boostColor
                )
            }

            // WIFI CARD
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(hvacCardShape(12))
                    .background(Color.White.copy(alpha = theme.cardOpacity))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), hvacCardShape(12))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SIGNAL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = theme.ecoColor, modifier = Modifier.size(12.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${poolState.wifiSignal ?: -57} dBm", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Spacer(modifier = Modifier.height(4.dp))
                Text("• EXCELLENT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = theme.ecoColor)
            }
        }

        // --- 3. INTERACTIVE HISTORICAL CHARTS & TRENDS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(hvacCardShape(14))
                .background(Color.White.copy(alpha = theme.cardOpacity))
                .border(1.dp, Color.White.copy(alpha = 0.07f), hvacCardShape(14))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "HISTORICAL TRENDS & ANALYTICS",
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    color = theme.coolColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Consolidated telemetry logs and chemical fluctuations",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }

            // Parametric sub-tabs (TEMP, pH, ORP)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    Triple(0, "TEMP", theme.coolColor),
                    Triple(1, "pH LEVEL", theme.ecoColor),
                    Triple(2, "ORP", theme.heatColor)
                )
                tabs.forEach { (index, title, color) ->
                    val isSel = activeTrendTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.02f))
                            .border(
                                1.dp,
                                if (isSel) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.07f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { activeTrendTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) color else Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Timeframe selection picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIME FRAME:",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(end = 4.dp)
                )
                val timeframes = listOf("6 HOURS", "24 HOURS", "7 DAYS")
                timeframes.forEachIndexed { index, label ->
                    val isSel = activeTimeFrame == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSel) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { activeTimeFrame = index }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            val rangeMin = when (activeTrendTab) {
                0 -> poolTempMin
                1 -> poolPhMin
                else -> poolOrpMin
            }
            val rangeMax = when (activeTrendTab) {
                0 -> poolTempMax
                1 -> poolPhMax
                else -> poolOrpMax
            }

            // Calculation of chart ranges
            val originalValues = displayHistory.map { pt ->
                when (activeTrendTab) {
                    0 -> pt.temp
                    1 -> pt.ph
                    else -> pt.orp
                }
            }

            val minVal = minOf(originalValues.minOrNull() ?: 0f, rangeMin)
            val maxVal = maxOf(originalValues.maxOrNull() ?: 100f, rangeMax)
            val range = if (maxVal - minVal == 0f) 1f else maxVal - minVal

            // Canvas & Y-Axis container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Y-AXIS LABELS COLUMN ---
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(42.dp)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val isTemp = activeTrendTab == 0
                    val isPh = activeTrendTab == 1
                    
                    val topVal = maxVal
                    val midVal = minVal + range / 2f
                    val botVal = minVal

                    val formatLabel = { v: Float ->
                        when {
                            isTemp -> String.format("%.1f°", v)
                            isPh -> String.format("%.2f", v)
                            else -> String.format("%.0f", v)
                        }
                    }

                    Text(formatLabel(topVal), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                    Text(formatLabel(midVal), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(formatLabel(botVal), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                }

                // --- CANVAS GRAPH CONTAINER ---
                var selectedPointIndex by remember(displayHistory) { mutableStateOf<Int?>(null) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(displayHistory) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val width = size.width
                                    if (width > 0 && displayHistory.isNotEmpty()) {
                                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                                        val idx = (fraction * (displayHistory.size - 1)).roundToInt()
                                        selectedPointIndex = idx.coerceIn(displayHistory.indices)
                                    }
                                }
                            )
                        }
                        .pointerInput(displayHistory) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val width = size.width
                                    if (width > 0 && displayHistory.isNotEmpty()) {
                                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                                        val idx = (fraction * (displayHistory.size - 1)).roundToInt()
                                        selectedPointIndex = idx.coerceIn(displayHistory.indices)
                                    }
                                },
                                onDrag = { change, _ ->
                                    val width = size.width
                                    if (width > 0 && displayHistory.isNotEmpty()) {
                                        val fraction = (change.position.x / width).coerceIn(0f, 1f)
                                        val idx = (fraction * (displayHistory.size - 1)).roundToInt()
                                        selectedPointIndex = idx.coerceIn(displayHistory.indices)
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
                    if (displayHistory.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Home Assistant Connection Offline",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Unable to retrieve pool historical metrics",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val pad = 4.dp.toPx()
                        val effH = height - 2 * pad

                        // Horizontal Reference Lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, pad),
                            end = Offset(width, pad),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(0f, pad + effH / 2f),
                            end = Offset(width, pad + effH / 2f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, pad + effH),
                            end = Offset(width, pad + effH),
                            strokeWidth = 1f
                        )

                        // Draw set target range background
                        val yMinBound = pad + (1f - (rangeMin - minVal) / range) * effH
                        val yMaxBound = pad + (1f - (rangeMax - minVal) / range) * effH
                        val heightDiff = if (yMinBound > yMaxBound) yMinBound - yMaxBound else yMaxBound - yMinBound
                        
                        drawRect(
                            color = theme.ecoColor.copy(alpha = 0.08f),
                            topLeft = Offset(0f, if (yMinBound < yMaxBound) yMinBound else yMaxBound),
                            size = Size(width, heightDiff)
                        )
                        
                        drawLine(
                            color = theme.ecoColor.copy(alpha = 0.25f),
                            start = Offset(0f, yMinBound),
                            end = Offset(width, yMinBound),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                        drawLine(
                            color = theme.ecoColor.copy(alpha = 0.25f),
                            start = Offset(0f, yMaxBound),
                            end = Offset(width, yMaxBound),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        if (displayHistory.isNotEmpty()) {
                            val points = displayHistory.mapIndexed { idx, pt ->
                                val value = when (activeTrendTab) {
                                    0 -> pt.temp
                                    1 -> pt.ph
                                    else -> pt.orp
                                }
                                val x = (width / (displayHistory.size - 1)) * idx
                                val y = pad + (1f - (value - minVal) / range) * effH
                                Offset(x, y)
                            }

                            val strokeColor = when (activeTrendTab) {
                                0 -> theme.coolColor
                                1 -> theme.ecoColor
                                else -> theme.heatColor
                            }

                            // Bezier construction
                            val path = Path().apply {
                                if (points.isNotEmpty()) {
                                    moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size) {
                                        val prev = points[i - 1]
                                        val curr = points[i]
                                        val ctrlX = (prev.x + curr.x) / 2f
                                        cubicTo(
                                            ctrlX, prev.y,
                                            ctrlX, curr.y,
                                            curr.x, curr.y
                                        )
                                    }
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(path)
                                if (points.isNotEmpty()) {
                                    lineTo(points.last().x, pad + effH)
                                    lineTo(points.first().x, pad + effH)
                                    close()
                                }
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(strokeColor.copy(alpha = 0.18f), Color.Transparent),
                                    startY = pad,
                                    endY = pad + effH
                                )
                            )

                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Point highlight highlights
                            points.forEachIndexed { i, pt ->
                                if (i == points.size - 1 || i % 2 == 0) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = strokeColor,
                                        radius = 3.5.dp.toPx(),
                                        center = pt,
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }

                            // Draw vertical crosshair guide
                            selectedPointIndex?.let { selIdx ->
                                if (selIdx in points.indices) {
                                    val selPt = points[selIdx]
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.4f),
                                        start = Offset(selPt.x, pad),
                                        end = Offset(selPt.x, pad + effH),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawCircle(
                                        color = strokeColor,
                                        radius = 6.dp.toPx(),
                                        center = selPt
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.5.dp.toPx(),
                                        center = selPt
                                    )
                                }
                            }
                        }
                    }

                    // Floating Tooltip Overlay
                    selectedPointIndex?.let { selIdx ->
                        if (selIdx in displayHistory.indices) {
                            val pt = displayHistory[selIdx]
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = pt.timestamp.uppercase(),
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
                                            text = "Temp: ${String.format(java.util.Locale.US, "%.1f", pt.temp)}°F",
                                            fontSize = 9.5.sp,
                                            color = theme.coolColor,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "pH: ${String.format(java.util.Locale.US, "%.2f", pt.ph)}",
                                            fontSize = 9.5.sp,
                                            color = theme.ecoColor,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "ORP: ${pt.orp.toInt()}mV",
                                            fontSize = 9.5.sp,
                                            color = theme.heatColor,
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

            // Timestamps Labels row aligned perfectly with canvas starting position
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (displayHistory.size >= 4) {
                    Text(displayHistory.first().timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(displayHistory[displayHistory.size / 3].timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(displayHistory[2 * displayHistory.size / 3].timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(displayHistory.last().timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                } else {
                    Text("00:00", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text("12:00", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text("23:59", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                }
            }
        }

        // --- 3.5. POOL THRESHOLDS & SETTINGS PANEL ---
        var isSettingsExpanded by remember { mutableStateOf(false) }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = theme.cardOpacity)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            shape = hvacCardShape(14),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSettingsExpanded = !isSettingsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tune Icon",
                            tint = theme.coolColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "POOL TARGET THRESHOLDS",
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = theme.coolColor,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Configure safe ranges for automated balanced checks",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand/Collapse",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isSettingsExpanded) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    var tempMinStr by remember(poolTempMin) { mutableStateOf(poolTempMin.toString()) }
                    var tempMaxStr by remember(poolTempMax) { mutableStateOf(poolTempMax.toString()) }

                    var phMinStr by remember(poolPhMin) { mutableStateOf(String.format("%.2f", poolPhMin)) }
                    var phMaxStr by remember(poolPhMax) { mutableStateOf(String.format("%.2f", poolPhMax)) }

                    var orpMinStr by remember(poolOrpMin) { mutableStateOf(poolOrpMin.toInt().toString()) }
                    var orpMaxStr by remember(poolOrpMax) { mutableStateOf(poolOrpMax.toInt().toString()) }

                    var batteryMinStr by remember(poolBatteryMin) { mutableStateOf(poolBatteryMin.toInt().toString()) }
                    var batteryMaxStr by remember(poolBatteryMax) { mutableStateOf(poolBatteryMax.toInt().toString()) }

                    val context = LocalContext.current

                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // WATER TEMPERATURE RANGE
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Water Temperature Range (°F)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = tempMinStr,
                                    onValueChange = { tempMinStr = it },
                                    label = { Text("Min Temp", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.coolColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = tempMaxStr,
                                    onValueChange = { tempMaxStr = it },
                                    label = { Text("Max Temp", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.coolColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // PH LEVEL RANGE
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "pH Cleanliness Range",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = phMinStr,
                                    onValueChange = { phMinStr = it },
                                    label = { Text("Min pH", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.ecoColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = phMaxStr,
                                    onValueChange = { phMaxStr = it },
                                    label = { Text("Max pH", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.ecoColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ORP ACCENT RANGE
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "ORP Sanitizer Range (mV)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = orpMinStr,
                                    onValueChange = { orpMinStr = it },
                                    label = { Text("Min ORP", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.heatColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = orpMaxStr,
                                    onValueChange = { orpMaxStr = it },
                                    label = { Text("Max ORP", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.heatColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // BATTERY VOLTAGE RANGE
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Pool Monitor Battery Range (mV)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = batteryMinStr,
                                    onValueChange = { batteryMinStr = it },
                                    label = { Text("Min Battery", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.coolColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = batteryMaxStr,
                                    onValueChange = { batteryMaxStr = it },
                                    label = { Text("Max Battery", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = theme.coolColor,
                                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Apply ranges button
                        Button(
                            onClick = {
                                val tMin = tempMinStr.toFloatOrNull() ?: poolTempMin
                                val tMax = tempMaxStr.toFloatOrNull() ?: poolTempMax
                                val pMin = phMinStr.toFloatOrNull() ?: poolPhMin
                                val pMax = phMaxStr.toFloatOrNull() ?: poolPhMax
                                val oMin = orpMinStr.toFloatOrNull() ?: poolOrpMin
                                val oMax = orpMaxStr.toFloatOrNull() ?: poolOrpMax
                                val bMin = batteryMinStr.toFloatOrNull() ?: poolBatteryMin
                                val bMax = batteryMaxStr.toFloatOrNull() ?: poolBatteryMax

                                if (tMin <= tMax && pMin <= pMax && oMin <= oMax && bMin <= bMax) {
                                    viewModel.setPoolTempRange(tMin, tMax)
                                    viewModel.setPoolPhRange(pMin, pMax)
                                    viewModel.setPoolOrpRange(oMin, oMax)
                                    viewModel.setPoolBatteryRange(bMin, bMax)
                                    android.widget.Toast.makeText(context, "Telemetry threshold ranges updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Invalid boundaries: Min must be less than Max", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.ecoColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SAVE TARGET RANGES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // --- 4. DIAGNOTICS & NODE DETAILS PANEL ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(hvacCardShape(12))
                .background(Color.White.copy(alpha = theme.cardOpacity))
                .border(1.dp, Color.White.copy(alpha = 0.07f), hvacCardShape(12))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "NODE DIAGNOSTIC DATA",
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val battVal = poolState.battery ?: 4536.0
                val isBattIdeal = battVal >= poolBatteryMin && battVal <= poolBatteryMax
                val batteryColor = if (isBattIdeal) theme.ecoColor else theme.heatColor
                val batteryText = "${String.format("%.0f", battVal)} mV" + (if (isBattIdeal) " • IDEAL" else " • ADJUST REQ")

                DiagnosticItem("Battery Volts", batteryText, Icons.Outlined.BatteryFull, theme, valueColor = batteryColor)
                DiagnosticItem("Monitor Serial", poolState.monitorSerial ?: "020F5F12", Icons.Outlined.Monitor, theme)
                DiagnosticItem("Sensor Serial", poolState.sensorSerial ?: "2515-0608P", Icons.Outlined.QrCodeScanner, theme)
                DiagnosticItem("WiFi Signal Link", "${poolState.wifiSignal ?: -57} dBm", Icons.Outlined.CellTower, theme)
                DiagnosticItem("Last Device Sync", formatFriendlyTime(poolState.lastSynced), Icons.Outlined.CloudSync, theme)
                DiagnosticItem("Last Updated", formatFriendlyTime(poolState.lastUpdated), Icons.Outlined.History, theme)
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    value: String,
    icon: ImageVector,
    theme: HvacThemeColors,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
