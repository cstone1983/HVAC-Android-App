package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.model.DynamicCardConfig
import com.example.model.DynamicSectionConfig
import com.example.model.DynamicStatConfig
import com.example.ui.theme.HvacThemeColors

// Resolve icons dynamically from string names in JSON
fun getDynamicIconByName(name: String?): ImageVector {
    if (name == null) return Icons.Default.Help
    return when (name.lowercase().trim()) {
        "solar_power", "solarpower" -> Icons.Default.SolarPower
        "pool" -> Icons.Default.Pool
        "wb_sunny", "sunny", "sun" -> Icons.Default.WbSunny
        "bolt", "power", "energy" -> Icons.Default.Bolt
        "science", "chemistry", "ph" -> Icons.Default.Science
        "settings", "pump", "gear" -> Icons.Default.Settings
        "trending_up", "trendingup" -> Icons.Default.TrendingUp
        "show_chart", "showchart", "chart", "graph" -> Icons.Default.ShowChart
        "lightbulb" -> Icons.Default.Lightbulb
        "cloud_download", "download" -> Icons.Default.CloudDownload
        "layers" -> Icons.Default.Layers
        "bar_chart", "barchart" -> Icons.Default.BarChart
        "schedule", "timer", "time" -> Icons.Default.Schedule
        "shield", "security" -> Icons.Default.Shield
        "info", "help" -> Icons.Default.Info
        else -> Icons.Default.Help
    }
}

// Resolve colors dynamically from presets or hex format
fun getColorByName(name: String?, theme: HvacThemeColors): Color {
    if (name == null) return theme.coolColor
    if (name.startsWith("#")) {
        return try {
            Color(android.graphics.Color.parseColor(name))
        } catch (e: Exception) {
            theme.coolColor
        }
    }
    return when (name.lowercase().trim()) {
        "eco" -> theme.ecoColor
        "cool", "blue" -> theme.coolColor
        "heat", "heating", "orange" -> theme.heatColor
        "boost", "red" -> theme.boostColor
        "off", "gray" -> theme.offColor
        "accent", "green" -> Color(0xFF10B981)
        "purple" -> Color(0xFF8B5CF6)
        "sky" -> Color(0xFF0EA5E9)
        "amber" -> Color(0xFFF59E0B)
        else -> theme.coolColor
    }
}

@Composable
fun DynamicSectionRenderer(sectionConfig: DynamicSectionConfig, theme: HvacThemeColors) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header (Dynamic Title)
        sectionConfig.title?.let { title ->
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Render Cards dynamically based on configuration
        sectionConfig.cards.forEach { card ->
            when (card.type.lowercase().trim()) {
                "placeholder" -> {
                    PlaceholderCard(card = card, theme = theme)
                }
                "stats_row" -> {
                    StatsRowCard(card = card, theme = theme)
                }
                "chart" -> {
                    ChartCard(card = card, theme = theme)
                }
                else -> {
                    // Fallback simple card with generic layout
                    SimpleGenericCard(card = card, theme = theme)
                }
            }
        }
    }
}

@Composable
fun PlaceholderCard(card: DynamicCardConfig, theme: HvacThemeColors) {
    val accentColor = getColorByName(card.tintColor, theme)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                color = Color.White.copy(alpha = theme.cardOpacity),
                shape = RoundedCornerShape(12.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = getDynamicIconByName(card.icon),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = card.title ?: "INTEGRATION PENDING",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 0.5.sp
            )
            Text(
                text = card.subtitle ?: "Sensors & Telemetry Offline",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            card.statusText?.let { status ->
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = status.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StatsRowCard(card: DynamicCardConfig, theme: HvacThemeColors) {
    val stats = card.stats ?: emptyList()
    if (stats.isEmpty()) return
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        card.title?.let { title ->
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            stats.forEach { stat ->
                val statAccent = getColorByName(stat.tintColor ?: card.tintColor, theme)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp)
                        .background(
                            color = Color.White.copy(alpha = theme.cardOpacity),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        stat.icon?.let { iconName ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(statAccent.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getDynamicIconByName(iconName),
                                    contentDescription = null,
                                    tint = statAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stat.label.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.4f),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = stat.value,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChartCard(card: DynamicCardConfig, theme: HvacThemeColors) {
    val accentColor = getColorByName(card.tintColor, theme)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(
                color = Color.White.copy(alpha = theme.cardOpacity),
                shape = RoundedCornerShape(12.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = card.title ?: "HISTORICAL ACTIVITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = card.subtitle ?: "Historical monitoring trends",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
                Icon(
                    imageVector = getDynamicIconByName(card.icon),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Draw a highly visual, realistic pulsing telemetry chart
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val width = size.width
                val height = size.height
                
                // Draw horizontal dotted grid lines
                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = (height / gridLines) * i
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                
                // Draw a beautiful ambient gradient bezier path
                val path = Path().apply {
                    moveTo(0f, height * 0.7f)
                    cubicTo(width * 0.25f, height * 0.2f, width * 0.4f, height * 0.8f, width * 0.6f, height * 0.4f)
                    cubicTo(width * 0.75f, height * 0.1f, width * 0.9f, height * 0.6f, width, height * 0.5f)
                }
                
                // Fill brush under path
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )
                
                // Draw path line
                drawPath(
                    path = path,
                    color = accentColor.copy(alpha = 0.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun SimpleGenericCard(card: DynamicCardConfig, theme: HvacThemeColors) {
    val accentColor = getColorByName(card.tintColor, theme)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = theme.cardOpacity),
                shape = RoundedCornerShape(10.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = card.title ?: "General Configuration",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = card.subtitle ?: "Setup details pending",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

// Retain compatibility delegates for existing code structures
@Composable
fun SolarDataPlaceholder(theme: HvacThemeColors) {
    // Elegant fallbacks
    val config = DynamicSectionConfig(
        id = "solar",
        title = "Solar Energy Dashboard",
        cards = listOf(
            DynamicCardConfig(
                type = "placeholder",
                title = "INTEGRATION PENDING",
                subtitle = "Solar Array & Inverter Telemetry",
                icon = "solar_power",
                tintColor = "eco",
                statusText = "Awaiting local inverter connection"
            ),
            DynamicCardConfig(
                type = "stats_row",
                stats = listOf(
                    DynamicStatConfig("CURRENT PRODUCTION", "Pending Setup", "wb_sunny", "eco"),
                    DynamicStatConfig("GRID STATUS", "Monitoring Offline", "bolt", "cool")
                )
            ),
            DynamicCardConfig(
                type = "chart",
                title = "HISTORICAL TRENDS (COMING SOON)",
                subtitle = "Daily solar yields and consumption patterns",
                icon = "trending_up"
            )
        )
    )
    DynamicSectionRenderer(sectionConfig = config, theme = theme)
}

@Composable
fun PoolDataPlaceholder(theme: HvacThemeColors) {
    val config = DynamicSectionConfig(
        id = "pool",
        title = "Pool Automation Dashboard",
        cards = listOf(
            DynamicCardConfig(
                type = "placeholder",
                title = "INTEGRATION PENDING",
                subtitle = "Pool Automation & Chemistry",
                icon = "pool",
                tintColor = "cool",
                statusText = "Awaiting controller link"
            ),
            DynamicCardConfig(
                type = "stats_row",
                stats = listOf(
                    DynamicStatConfig("PH & CHLORINE", "pH: -- / Cl: -- ppm", "science", "accent"),
                    DynamicStatConfig("PUMP & HEATER STATUS", "Inactive / Off", "settings", "eco")
                )
            ),
            DynamicCardConfig(
                type = "chart",
                title = "TEMPERATURE HISTORICAL TRENDS",
                subtitle = "Pool temperature patterns and analytics",
                icon = "show_chart"
            )
        )
    )
    DynamicSectionRenderer(sectionConfig = config, theme = theme)
}
