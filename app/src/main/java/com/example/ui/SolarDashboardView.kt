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
    // The old "Historical trends" card, its 6 h / 24 h / 7 d power charts and the raw
    // diagnostics stood below Solar Calc until 2026-10-21. They repeated Solar Calc's figures
    // from a different source, so the two disagreed on the same screen (74.42 kWh against
    // 73.4), and the history they needed was downloaded every time the tab opened. The one
    // thing only they showed, the CMP bank balance, is kept as a line at the bottom.
    val liveState by viewModel.solarLiveState.collectAsStateWithLifecycle()

    // Solar Calc: the separate forecasting system behind solar.stoneyshome.com.
    //
    // Polled only while this tab is on screen. It is a website, not a controller, and nobody
    // needs its chart refreshed on a panel showing the pool.
    val solarToday by viewModel.solarToday.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val solarCalcCfg = layoutConfig.solarCalc
    val solarPalette = rememberSolarPalette(solarCalcCfg?.colors)

    LaunchedEffect(solarCalcCfg?.enabled, solarCalcCfg?.baseUrl, solarCalcCfg?.pollSeconds) {
        if (solarCalcCfg?.enabled == false) return@LaunchedEffect
        val everyMs = ((solarCalcCfg?.pollSeconds ?: 60).coerceAtLeast(15)) * 1000L
        while (true) {
            viewModel.fetchSolarToday()
            kotlinx.coroutines.delay(everyMs)
        }
    }

    // The app used to work out today’s generation and usage itself, here, and it was wrong:
    // it summed every power reading since midnight and divided by 6000, which counts each
    // reading as exactly ten minutes. The readings are not evenly spaced, so the total ran
    // about 2% high — always upwards, every day. That is the 98.0 kWh the phone showed
    // against the website’s 95.7 kWh for the same September day.
    //
    // Both figures now come from the endpoint, which does not integrate power at all: they
    // are the meters’ own daily energy counters, which match Home Assistant’s hourly
    // statistics to 0.01%. Nothing here recomputes them, so the two can never disagree again.
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("solar_dashboard")
    ) {
        // The importing/exporting banner stood here. The Right now tile below says the same
        // thing in words — "Sending 6.21 kW to the grid" — and says it with a figure the
        // website agrees with, so the banner had become a second opinion on a settled
        // question.
        //
        // It is worth knowing what was given up: the banner was live off the Home Assistant
        // WebSocket and moved within a second or two, where the tile is a five-minute sample
        // polled once a minute. If watching the needle swing in real time turns out to
        // matter, NetMeterCard is still here and takes four lines to put back.

        // B. Today, from Solar Calc.
        //
        // This replaced two cards that were quietly misleading. "CURRENT PRODUCTION" was fine,
        // but "GRID STATUS" showed whole-house consumption, not the grid — the panel CTs
        // cannot see export at all, so the export sensor reads a permanent zero and the one
        // labelled grid usage is really house usage. The Right now tile gets grid from the
        // endpoint, which knows the difference and signs it: positive pulling, negative
        // sending.
        SolarCalcTodaySection(
            state = solarToday,
            palette = solarPalette,
            enabled = solarCalcCfg?.enabled != false
        )

        liveState.cmpBankBalance?.let { balance ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("cmp_bank_balance_value"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CMP bank balance", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${String.format(java.util.Locale.US, "%,.0f", balance)} ${liveState.cmpBankBalanceUnit ?: "kWh"}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
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
        shape = com.example.ui.theme.themedShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), com.example.ui.theme.themedShape(12.dp))
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
                    .background(accentColor.copy(alpha = 0.15f), com.example.ui.theme.themedShape(12.dp)),
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
                                .background(theme.offColor.copy(alpha = 0.2f), com.example.ui.theme.themedShape(4.dp))
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
        shape = com.example.ui.theme.themedShape(12.dp),
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.08f), com.example.ui.theme.themedShape(12.dp))
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
                        .background(iconColor.copy(alpha = 0.12f), com.example.ui.theme.themedShape(6.dp)),
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
            .background(Color.White.copy(alpha = 0.04f), com.example.ui.theme.themedShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), com.example.ui.theme.themedShape(18.dp))
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
                    .background(itemBgColor, com.example.ui.theme.themedShape(16.dp))
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

            // 1. Vertical background shading for credits (export) vs import
            if (theme.showChartShading && data.size > 1) {
                for (idx in 0 until data.size - 1) {
                    val pt1 = data[idx]
                    val xStart = leftPad + (effW / (data.size - 1)) * idx
                    val xEnd = leftPad + (effW / (data.size - 1)) * (idx + 1)
                    
                    val isGenerating = pt1.productionWatts >= pt1.usageWatts
                    val shadeColor = if (isGenerating) theme.ecoColor.copy(alpha = theme.chartShadingAlpha) else theme.boostColor.copy(alpha = theme.chartShadingAlpha)
                    
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(xStart, topPad),
                        size = Size(xEnd - xStart, effH)
                    )
                }
            }
    
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
                        .background(Color.Black.copy(alpha = 0.85f), com.example.ui.theme.themedShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), com.example.ui.theme.themedShape(8.dp))
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
    
            // 1. Vertical background shading for credits (export) vs import
            if (theme.showChartShading && data.size > 1) {
                for (idx in 0 until data.size - 1) {
                    val pt1 = data[idx]
                    val xStart = leftPad + (effW / (data.size - 1)) * idx
                    val xEnd = leftPad + (effW / (data.size - 1)) * (idx + 1)
                    
                    val isGenerating = pt1.productionWatts >= pt1.usageWatts
                    val shadeColor = if (isGenerating) theme.ecoColor.copy(alpha = theme.chartShadingAlpha) else theme.boostColor.copy(alpha = theme.chartShadingAlpha)
                    
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(xStart, topPad),
                        size = Size(xEnd - xStart, effH)
                    )
                }
            }

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
                        .background(Color.Black.copy(alpha = 0.85f), com.example.ui.theme.themedShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), com.example.ui.theme.themedShape(8.dp))
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

            // 1. Vertical background shading for credits (export) vs import
            if (theme.showChartShading && data.size > 1) {
                for (idx in 0 until data.size - 1) {
                    val pt1 = data[idx]
                    val xStart = leftPad + (effW / (data.size - 1)) * idx
                    val xEnd = leftPad + (effW / (data.size - 1)) * (idx + 1)
                    
                    val isGenerating = pt1.productionWatts >= pt1.usageWatts
                    val shadeColor = if (isGenerating) theme.ecoColor.copy(alpha = theme.chartShadingAlpha) else theme.boostColor.copy(alpha = theme.chartShadingAlpha)
                    
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(xStart, topPad),
                        size = Size(xEnd - xStart, effH)
                    )
                }
            }
    
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
                        .background(Color.Black.copy(alpha = 0.85f), com.example.ui.theme.themedShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), com.example.ui.theme.themedShape(8.dp))
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

            // 1. Vertical background shading for credits (export) vs import
            if (theme.showChartShading && data.size > 1) {
                for (idx in 0 until data.size - 1) {
                    val pt1 = data[idx]
                    val xStart = leftPad + (effW / (data.size - 1)) * idx
                    val xEnd = leftPad + (effW / (data.size - 1)) * (idx + 1)
                    
                    val isGenerating = pt1.totalProducedKwh >= pt1.totalConsumedKwh
                    val shadeColor = if (isGenerating) theme.ecoColor.copy(alpha = theme.chartShadingAlpha) else theme.boostColor.copy(alpha = theme.chartShadingAlpha)
                    
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(xStart, topPad),
                        size = Size(xEnd - xStart, effH)
                    )
                }
            }
    
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
                        .background(Color.Black.copy(alpha = 0.85f), com.example.ui.theme.themedShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), com.example.ui.theme.themedShape(8.dp))
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
