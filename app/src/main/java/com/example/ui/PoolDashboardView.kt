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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.HvacViewModel
import com.example.model.PoolState
import com.example.model.PoolHistoryPoint
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.HvacThemeColors
import com.example.ui.theme.hvacCardShape

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PoolDashboardView(
    viewModel: HvacViewModel,
    modifier: Modifier = Modifier
) {
    val theme = LocalHvacTheme.current
    val poolState by viewModel.poolState.collectAsStateWithLifecycle()
    val poolHistory by viewModel.poolHistory.collectAsStateWithLifecycle()

    var activeTrendTab by remember { mutableStateOf(0) } // 0 = Temp, 1 = pH, 2 = ORP

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
                verticalAlignment = Alignment.CenterVertically
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
                val isPhIdeal = ph in 7.2..7.6
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
                val isOrpIdeal = orp in 550.0..750.0
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

            // Tab selectors for different graphs
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

            // Canvas Trend Line Drawing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // Draw grid reference horizontal lines
                    val lines = 3
                    for (i in 0..lines) {
                        val y = (height / lines) * i
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    if (poolHistory.isNotEmpty()) {
                        // Gather points corresponding to selection
                        val originalValues = poolHistory.map { pt ->
                            when (activeTrendTab) {
                                0 -> pt.temp
                                1 -> pt.ph
                                else -> pt.orp
                            }
                        }

                        val minVal = originalValues.minOrNull() ?: 0f
                        val maxVal = originalValues.maxOrNull() ?: 100f
                        val range = if (maxVal - minVal == 0f) 1f else maxVal - minVal

                        val points = poolHistory.mapIndexed { idx, pt ->
                            val value = when (activeTrendTab) {
                                0 -> pt.temp
                                1 -> pt.ph
                                else -> pt.orp
                            }
                            val x = (width / (poolHistory.size - 1)) * idx
                            // Flip y axis so higher value is at top
                            val y = height - ((value - minVal) / range) * (height * 0.8f) - (height * 0.1f)
                            Offset(x, y)
                        }

                        val strokeColor = when (activeTrendTab) {
                            0 -> theme.coolColor
                            1 -> theme.ecoColor
                            else -> theme.heatColor
                        }

                        // Drawing Bezier Line
                        val path = Path().apply {
                            if (points.isNotEmpty()) {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val previous = points[i - 1]
                                    val current = points[i]
                                    val ctrlX = (previous.x + current.x) / 2f
                                    cubicTo(
                                        ctrlX, previous.y,
                                        ctrlX, current.y,
                                        current.x, current.y
                                    )
                                }
                            }
                        }

                        // Gradient fill under curve
                        val fillPath = Path().apply {
                            addPath(path)
                            if (points.isNotEmpty()) {
                                lineTo(points.last().x, height)
                                lineTo(points.first().x, height)
                                close()
                            }
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(strokeColor.copy(alpha = 0.18f), Color.Transparent),
                                startY = 0f,
                                endY = height
                            )
                        )

                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Highlight dots for points
                        points.forEachIndexed { i, pt ->
                            if (i == points.size - 1 || i % 3 == 0) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = strokeColor,
                                    radius = 4.dp.toPx(),
                                    center = pt,
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Timestamps Labels row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (poolHistory.size >= 4) {
                    Text(poolHistory.first().timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(poolHistory[poolHistory.size / 3].timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(poolHistory[2 * poolHistory.size / 3].timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text(poolHistory.last().timestamp, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                } else {
                    Text("00:00", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text("12:00", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    Text("23:59", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
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
                DiagnosticItem("Battery Volts", "${String.format("%.0f", poolState.battery ?: 4536.0)} mV", Icons.Outlined.BatteryFull, theme)
                DiagnosticItem("Monitor Serial", poolState.monitorSerial ?: "020F5F12", Icons.Outlined.Monitor, theme)
                DiagnosticItem("Sensor Serial", poolState.sensorSerial ?: "2515-0608P", Icons.Outlined.QrCodeScanner, theme)
                DiagnosticItem("WiFi Signal Link", "${poolState.wifiSignal ?: -57} dBm", Icons.Outlined.CellTower, theme)
                DiagnosticItem("Last Device Sync", poolState.lastSynced ?: "3 minutes ago", Icons.Outlined.CloudSync, theme)
                DiagnosticItem("Last Updated", poolState.lastUpdated ?: "15 minutes ago", Icons.Outlined.History, theme)
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    value: String,
    icon: ImageVector,
    theme: HvacThemeColors
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
            color = Color.White
        )
    }
}
