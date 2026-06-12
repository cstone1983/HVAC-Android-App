package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel
import com.example.viewmodel.UpdateState
import kotlinx.coroutines.launch

const val ACTIVE_CORE_VERSION = "v2.1.4"

private data class WaterHeaterItem(
    val option: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        if (cleanHex.length == 6) {
            Color(android.graphics.Color.parseColor("#$cleanHex"))
        } else if (cleanHex.length == 8) {
            Color(android.graphics.Color.parseColor("#$cleanHex"))
        } else {
            fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun ThemeCoreIcon(presetId: String, size: androidx.compose.ui.unit.Dp = 36.dp, modifier: Modifier = Modifier) {
    val themeColors = LocalHvacTheme.current
    val stroke1 = 1.dp
    val stroke1_5 = 1.5.dp
    val stroke2 = 2.dp
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val cx = w / 2f
        val cy = h / 2f
        
        when (presetId) {
            "classic", "dynamic" -> {
                drawCircle(themeColors.heatColor.copy(alpha = 0.2f), radius = w * 0.45f)
                drawCircle(themeColors.heatColor, radius = w * 0.18f)
                for (i in 0 until 8) {
                    val angle = (i * Math.PI / 4)
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    drawLine(
                        color = themeColors.heatColor.copy(alpha = 0.7f),
                        start = androidx.compose.ui.geometry.Offset(cx + cos * w * 0.22f, cy + sin * h * 0.22f),
                        end = androidx.compose.ui.geometry.Offset(cx + cos * w * 0.42f, cy + sin * h * 0.42f),
                        strokeWidth = stroke2.toPx()
                    )
                }
            }
            "nordic" -> {
                drawCircle(themeColors.coolColor.copy(alpha = 0.15f), radius = w * 0.4f)
                for (i in 0 until 6) {
                    val angle = (i * Math.PI / 3)
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    drawLine(
                        color = themeColors.coolColor,
                        start = androidx.compose.ui.geometry.Offset(cx, cy),
                        end = androidx.compose.ui.geometry.Offset(cx + cos * w * 0.45f, cy + sin * h * 0.45f),
                        strokeWidth = stroke2.toPx()
                    )
                    val shCos = Math.cos(angle + Math.PI / 12).toFloat()
                    val shSin = Math.sin(angle + Math.PI / 12).toFloat()
                    drawLine(
                        color = themeColors.coolColor.copy(alpha = 0.7f),
                        start = androidx.compose.ui.geometry.Offset(cx + cos * w * 0.25f, cy + sin * h * 0.25f),
                        end = androidx.compose.ui.geometry.Offset(cx + shCos * w * 0.38f, cy + shSin * h * 0.38f),
                        strokeWidth = stroke1_5.toPx()
                    )
                }
            }
            "emerald" -> {
                drawCircle(themeColors.heatColor.copy(alpha = 0.15f), radius = w * 0.4f)
                drawCircle(themeColors.coolColor.copy(alpha = 0.15f), radius = w * 0.3f)
                drawCircle(themeColors.heatColor, radius = w * 0.12f)
                rotate(45f, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
                    drawOval(
                        color = themeColors.heatColor,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.45f, cy - h * 0.15f),
                        size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.3f),
                        style = Stroke(width = stroke1_5.toPx())
                    )
                }
                rotate(135f, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
                    drawOval(
                        color = themeColors.coolColor,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.45f, cy - h * 0.15f),
                        size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.3f),
                        style = Stroke(width = stroke1_5.toPx())
                    )
                }
            }
            "cyber" -> {
                drawCircle(themeColors.heatColor.copy(alpha = 0.2f), radius = w * 0.45f)
                val path = Path().apply {
                    moveTo(cx, cy - h * 0.45f)
                    lineTo(cx + w * 0.45f, cy)
                    lineTo(cx, cy + h * 0.45f)
                    lineTo(cx - w * 0.45f, cy)
                    close()
                }
                drawPath(path, color = themeColors.heatColor, style = Stroke(width = stroke2.toPx()))
                val pathInner = Path().apply {
                    moveTo(cx, cy - h * 0.25f)
                    lineTo(cx + w * 0.25f, cy)
                    lineTo(cx, cy + h * 0.25f)
                    lineTo(cx - w * 0.25f, cy)
                    close()
                }
                drawPath(pathInner, color = themeColors.coolColor, style = Stroke(width = stroke1_5.toPx()))
                drawCircle(Color.White, radius = w * 0.08f)
            }
            "volcanic" -> {
                drawCircle(themeColors.heatColor.copy(alpha = 0.2f), radius = w * 0.45f)
                val path = Path().apply {
                    moveTo(cx - w * 0.35f, cy + h * 0.35f)
                    cubicTo(cx - w * 0.45f, cy - h * 0.1f, cx - w * 0.1f, cy - h * 0.4f, cx, cy - h * 0.45f)
                    cubicTo(cx + w * 0.1f, cy - h * 0.4f, cx + w * 0.45f, cy - h * 0.1f, cx + w * 0.35f, cy + h * 0.35f)
                    lineTo(cx, cy + h * 0.15f)
                    close()
                }
                drawPath(path, color = themeColors.heatColor)
                drawCircle(themeColors.coolColor, radius = w * 0.12f)
            }
            "monochrome" -> {
                drawCircle(themeColors.heatColor.copy(alpha = 0.1f), radius = w * 0.45f)
                drawCircle(Color.White, radius = w * 0.35f, style = Stroke(width = stroke1_5.toPx()))
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy - h * 0.42f), androidx.compose.ui.geometry.Offset(cx, cy - h * 0.28f), strokeWidth = stroke2.toPx())
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy + h * 0.28f), androidx.compose.ui.geometry.Offset(cx, cy + h * 0.42f), strokeWidth = stroke2.toPx())
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx - w * 0.42f, cy), androidx.compose.ui.geometry.Offset(cx - w * 0.28f, cy), strokeWidth = stroke2.toPx())
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx + w * 0.28f, cy), androidx.compose.ui.geometry.Offset(cx + w * 0.42f, cy), strokeWidth = stroke2.toPx())
                drawCircle(themeColors.heatColor, radius = w * 0.08f)
            }
        }
    }
}

fun getIconByName(name: String?): ImageVector {
    return when (name?.lowercase()?.trim()) {
        "layers" -> Icons.Default.Layers
        "lightbulb" -> Icons.Default.Lightbulb
        "cloud_download", "download", "update" -> Icons.Default.CloudDownload
        "settings", "gear" -> Icons.Default.Settings
        "home" -> Icons.Default.Home
        "flashlight", "flash", "power" -> Icons.Default.FlashlightOn
        "thermostat", "temp", "temperature" -> Icons.Default.Thermostat
        "weekend", "couch" -> Icons.Default.Weekend
        "bed", "bedroom" -> Icons.Default.Bed
        "view_quilt", "dashboard" -> Icons.Default.ViewQuilt
        "light", "brightness" -> Icons.Default.Brightness5
        "bolt", "electricity", "switch" -> Icons.Default.PowerSettingsNew
        "garage", "door" -> Icons.Default.Garage
        "menu" -> Icons.Default.Menu
        "warning" -> Icons.Default.Warning
        "refresh" -> Icons.Default.Refresh
        "exit" -> Icons.Default.ExitToApp
        else -> Icons.Default.Layers
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HvacDashboard(
    viewModel: HvacViewModel,
    modifier: Modifier = Modifier
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(actionFeedback) {
        actionFeedback?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showSettingsDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.forceSyncOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamic state background color mapping matching home assistant transitions
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val themeConfig = layoutConfig.theme ?: HvacThemeConfig()
    val cardCornerStyle by viewModel.cardCornerStyle.collectAsStateWithLifecycle()
    val cardOpacity by viewModel.cardOpacity.collectAsStateWithLifecycle()
    val backgroundDesign by viewModel.backgroundDesign.collectAsStateWithLifecycle()
    val activeThemePreset by viewModel.selectedThemePreset.collectAsStateWithLifecycle()

    val rawGlowColor = parseHexColor(themeConfig.glowColorHex, Color(0xFF2196F3))
    val glowColorFactor = themeConfig.glowAlpha ?: 0.12f

    val scheduleState = when (val state = uiState) {
        is HvacUiState.Success -> state.globalSettings.houseSchedule
        else -> "Day"
    }

    val glowColor = when (scheduleState) {
        "Day" -> rawGlowColor.copy(alpha = glowColorFactor)
        "Night" -> parseHexColor(themeConfig.accentColorHex, Color(0xFFF59E0B)).copy(alpha = 0.08f)
        else -> Color(0xFF10B981).copy(alpha = 0.1f)
    }

    val bgStartColor = when (scheduleState) {
        "Day" -> parseHexColor(themeConfig.bgStartColorHex, Color(0xFF0F172A))
        "Night" -> parseHexColor(themeConfig.bgStartColorHex, Color(0xFF020617))
        else -> parseHexColor(themeConfig.bgStartColorHex, Color(0xFF050505))
    }
    val bgEndColor = when (scheduleState) {
        "Day" -> parseHexColor(themeConfig.bgEndColorHex, Color(0xFF1E293B))
        "Night" -> parseHexColor(themeConfig.bgEndColorHex, Color(0xFF0F172A))
        else -> parseHexColor(themeConfig.bgEndColorHex, Color(0xFF111827))
    }
    val bgGradient = Brush.verticalGradient(listOf(bgStartColor, bgEndColor))

    val hvacThemeColors = HvacThemeColors(
        heatColor = parseHexColor(themeConfig.accentColorHex, Color(0xFFF59E0B)),
        coolColor = parseHexColor(themeConfig.coolColorHex, Color(0xFF2196F3)),
        offColor = parseHexColor(themeConfig.offColorHex, Color(0xFF64748B)),
        bgStart = bgStartColor,
        bgEnd = bgEndColor,
        glowColor = glowColor,
        glowAlpha = glowColorFactor,
        cardCornerStyle = cardCornerStyle,
        cardOpacity = cardOpacity
    )

    CompositionLocalProvider(LocalHvacTheme provides hvacThemeColors) {
        if (showSettingsDialog) {
            HvacSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (!isLoggedIn) {
            HvacLoginScreen(
                viewModel = viewModel,
                glowColor = glowColor,
                bgGradient = bgGradient
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgGradient)
                    .drawBehind {
                        // 1. Core Background Styles
                        when (backgroundDesign) {
                            "grid" -> {
                                val gridSpacing = 44.dp.toPx()
                                val lineColor = hvacThemeColors.coolColor.copy(alpha = 0.05f)
                                var x = 0f
                                while (x < size.width) {
                                    drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                    x += gridSpacing
                                }
                                var y = 0f
                                while (y < size.height) {
                                    drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                    y += gridSpacing
                                }
                            }
                            "nebula" -> {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(hvacThemeColors.heatColor.copy(alpha = 0.08f), Color.Transparent),
                                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.25f),
                                        radius = size.width * 0.7f
                                    ),
                                    radius = size.width * 0.7f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.25f)
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(hvacThemeColors.coolColor.copy(alpha = 0.1f), Color.Transparent),
                                        center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.75f),
                                        radius = size.width * 0.7f
                                    ),
                                    radius = size.width * 0.7f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.75f)
                                )
                            }
                            "aurora" -> {
                                val waveColor1 = hvacThemeColors.coolColor.copy(alpha = 0.08f)
                                val waveColor2 = hvacThemeColors.ecoColor.copy(alpha = 0.08f)
                                
                                val path1 = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, size.height * 0.45f)
                                    cubicTo(
                                        size.width * 0.25f, size.height * 0.25f,
                                        size.width * 0.75f, size.height * 0.65f,
                                        size.width, size.height * 0.35f
                                    )
                                    lineTo(size.width, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                drawPath(
                                    path = path1,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(waveColor1, Color.Transparent),
                                        startY = size.height * 0.35f,
                                        endY = size.height
                                    )
                                )
                                
                                val path2 = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, size.height * 0.25f)
                                    cubicTo(
                                        size.width * 0.35f, size.height * 0.55f,
                                        size.width * 0.65f, size.height * 0.15f,
                                        size.width, size.height * 0.55f
                                    )
                                    lineTo(size.width, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                drawPath(
                                    path = path2,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(waveColor2, Color.Transparent),
                                        startY = size.height * 0.15f,
                                        endY = size.height
                                    )
                                )
                            }
                            "minimal" -> {
                                // Strictly dual-tone minimal gradient, do not draw secondary items
                            }
                            else -> {
                                // radial_glow (Default)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(glowColor, Color.Transparent),
                                        center = center,
                                        radius = size.width * 0.85f
                                    ),
                                    radius = size.width * 0.85f,
                                    center = center
                                )
                            }
                        }

                        // 2. High-Resolution Immersive Vector Art based on Active Theme Preset
                        when (activeThemePreset) {
                            "classic", "dynamic" -> {
                                // Haven Metallic - solar crown flares & precision concentric rings
                                drawCircle(
                                    color = hvacThemeColors.heatColor.copy(alpha = 0.04f),
                                    radius = size.width * 0.45f,
                                    center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawCircle(
                                    color = hvacThemeColors.heatColor.copy(alpha = 0.02f),
                                    radius = size.width * 0.70f,
                                    center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            "nordic" -> {
                                // Nordic Frost - deep glacier crystal vectors and coordinates
                                val crystalColor = hvacThemeColors.coolColor.copy(alpha = 0.04f)
                                drawCircle(
                                    color = crystalColor,
                                    radius = size.width * 0.3f,
                                    center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawLine(crystalColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.5f), strokeWidth = 1.dp.toPx())
                                drawLine(crystalColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.35f), strokeWidth = 1.dp.toPx())
                                drawLine(crystalColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.8f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.65f), strokeWidth = 1.dp.toPx())
                            }
                            "emerald" -> {
                                // Emerald Nebula - orbital cosmic lanes and sweeping planetary systems
                                val orbitColor = hvacThemeColors.ecoColor.copy(alpha = 0.04f)
                                drawCircle(
                                    color = orbitColor,
                                    radius = size.width * 0.45f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawCircle(
                                    color = orbitColor.copy(alpha = 0.02f),
                                    radius = size.width * 0.65f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                drawOval(
                                    color = orbitColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.1f, size.height * 0.2f),
                                    size = androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * 0.6f),
                                    style = Stroke(width = 1.3.dp.toPx())
                                )
                            }
                            "cyber" -> {
                                // Cyber Sunset - retro cyber perspective grids & wireframe horizon sun
                                val gridColor = hvacThemeColors.heatColor.copy(alpha = 0.08f)
                                val horizonY = size.height * 0.65f
                                
                                drawCircle(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(hvacThemeColors.heatColor.copy(alpha = 0.22f), Color.Transparent)
                                    ),
                                    radius = size.width * 0.28f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, horizonY - size.width * 0.12f)
                                )
                                
                                var lineY = horizonY - size.width * 0.4f
                                while (lineY < horizonY) {
                                    drawLine(
                                        color = hvacThemeColors.bgStart.copy(alpha = 0.9f),
                                        start = androidx.compose.ui.geometry.Offset(0f, lineY),
                                        end = androidx.compose.ui.geometry.Offset(size.width, lineY),
                                        strokeWidth = 6.dp.toPx()
                                    )
                                    lineY += 16.dp.toPx()
                                }

                                val gridLineCount = 12
                                for (i in 0..gridLineCount) {
                                    val startX = (size.width / gridLineCount) * i
                                    val vanishX = size.width / 2f
                                    drawLine(
                                        color = gridColor.copy(alpha = 0.12f),
                                        start = androidx.compose.ui.geometry.Offset(vanishX, horizonY),
                                        end = androidx.compose.ui.geometry.Offset(startX, size.height),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                                
                                var hY = horizonY
                                var spacing = 10.dp.toPx()
                                while (hY < size.height) {
                                    drawLine(
                                        color = gridColor.copy(alpha = 0.08f),
                                        start = androidx.compose.ui.geometry.Offset(0f, hY),
                                        end = androidx.compose.ui.geometry.Offset(size.width, hY),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    hY += spacing
                                    spacing = (spacing * 1.35f).coerceAtMost(80.dp.toPx())
                                }
                            }
                            "volcanic" -> {
                                // Volcanic Obsidian - rising warm thermal sparks & dynamic magma channels
                                val magmaColor = hvacThemeColors.heatColor.copy(alpha = 0.12f)
                                val sparks = listOf(
                                    androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.4f),
                                    androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.75f),
                                    androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.25f),
                                    androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.85f),
                                    androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.35f),
                                    androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.7f)
                                )
                                sparks.forEachIndexed { idx, pos ->
                                    val sizeFactor = (1.5f + (idx % 3) * 1.2f)
                                    drawCircle(
                                        color = magmaColor,
                                        radius = sizeFactor.dp.toPx(),
                                        center = pos
                                    )
                                    drawCircle(
                                        color = magmaColor.copy(alpha = 0.025f),
                                        radius = (sizeFactor * 4).dp.toPx(),
                                        center = pos
                                    )
                                }
                                val path = Path().apply {
                                    moveTo(0f, size.height * 0.8f)
                                    cubicTo(size.width * 0.3f, size.height * 0.75f, size.width * 0.7f, size.height * 0.9f, size.width, size.height * 0.85f)
                                }
                                drawPath(path, color = magmaColor.copy(alpha = 0.03f), style = Stroke(width = 12.dp.toPx()))
                            }
                            "monochrome" -> {
                                // Monochrome Slate - high-precision engineered aerospace layout markups
                                val tickColor = Color.White.copy(alpha = 0.05f)
                                val sizeL = 16.dp.toPx()
                                val inset = 12.dp.toPx()
                                
                                drawLine(tickColor, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Offset(inset + sizeL, inset), strokeWidth = 1.dp.toPx())
                                drawLine(tickColor, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Offset(inset, inset + sizeL), strokeWidth = 1.dp.toPx())
                                drawLine(tickColor, androidx.compose.ui.geometry.Offset(size.width - inset, inset), androidx.compose.ui.geometry.Offset(size.width - inset - sizeL, inset), strokeWidth = 1.dp.toPx())
                                drawLine(tickColor, androidx.compose.ui.geometry.Offset(size.width - inset, inset), androidx.compose.ui.geometry.Offset(size.width - inset, inset + sizeL), strokeWidth = 1.dp.toPx())
                                
                                drawCircle(
                                    color = tickColor,
                                    radius = size.width * 0.15f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawCircle(
                                    color = tickColor.copy(alpha = 0.02f),
                                    radius = size.width * 0.3f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                    }
            ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!isLandscape) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeCoreIcon(presetId = activeThemePreset, size = 28.dp)
                                Column {
                                    Text(
                                        layoutConfig.appTitle ?: "Home Control",
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 3.sp,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        layoutConfig.appSubtitle ?: "HVAC SYSTEM CONTROLLER",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                // Settings Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { showSettingsDialog = true }
                                        .testTag("settings_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Logout Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { viewModel.logout() }
                                        .testTag("logout_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = "Log out",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Connection / sync state node indicator
                                val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
                                val connectionColor = if (isOffline) {
                                    Color(0xFFEF4444)
                                } else {
                                    when (uiState) {
                                        is HvacUiState.Success -> Color(0xFF10B981)
                                        is HvacUiState.Loading -> Color(0xFFF59E0B)
                                        is HvacUiState.Error -> Color(0xFFEF4444)
                                    }
                                }
                                val connectionText = if (isOffline) {
                                    "DISCONNECTED"
                                } else {
                                    when (uiState) {
                                        is HvacUiState.Success -> "CONNECTED"
                                        is HvacUiState.Loading -> "REFRESHING"
                                        is HvacUiState.Error -> "DISCONNECTED"
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable {
                                            scope.launch { viewModel.fetchStates() }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(connectionColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = connectionText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Manual Sync Request",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (val state = uiState) {
                    is HvacUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Loading climate variables...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    is HvacUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Connection breakdown",
                                        tint = Color.Red,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Home Assistant Unreachable",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        state.message,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            viewModel.startSync()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("RETRY CONNECTION", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    is HvacUiState.Success -> {
                        HvacDashboardContent(
                            state = state,
                            viewModel = viewModel,
                            onShowSettings = { showSettingsDialog = true }
                        )
                    }
                }
            }
        }
    }
}
}
}

@Composable
fun DynamicTabContent(
    tab: TabConfig,
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    listState: LazyListState
) {
    val context = LocalContext.current
    val theme = LocalHvacTheme.current
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    var activeZoneDetail by remember { mutableStateOf<ClimateZone?>(null) }
    var activeLightPopupId by remember { mutableStateOf<String?>(null) }
    var activeSwitchPopupId by remember { mutableStateOf<String?>(null) }
    
    // Popup lookup & display
    val currentLightPopupState = activeLightPopupId?.let { id -> state.lights.find { it.entityId == id } }
    if (activeLightPopupId != null && currentLightPopupState != null) {
        LightingControlPopup(
            entityId = currentLightPopupState.entityId,
            name = currentLightPopupState.name,
            isOn = currentLightPopupState.isOn,
            brightness = currentLightPopupState.brightness,
            isLight = true,
            onDismiss = { activeLightPopupId = null },
            viewModel = viewModel
        )
    }

    val currentSwitchPopupState = activeSwitchPopupId?.let { id -> state.switches.find { it.entityId == id } }
    if (activeSwitchPopupId != null && currentSwitchPopupState != null) {
        LightingControlPopup(
            entityId = currentSwitchPopupState.entityId,
            name = currentSwitchPopupState.name,
            isOn = currentSwitchPopupState.isOn,
            brightness = null,
            isLight = false,
            onDismiss = { activeSwitchPopupId = null },
            viewModel = viewModel
        )
    }

    if (activeZoneDetail != null) {
        val currentZoneStatus = state.zones.find { it.key == activeZoneDetail?.key } ?: activeZoneDetail!!
        ZoneDetailPopup(
            zone = currentZoneStatus,
            globalHvacMode = state.globalSettings.globalHvacMode,
            lastNonOffHvacMode = state.globalSettings.lastNonOffHvacMode,
            activeScheduleState = state.globalSettings.houseSchedule,
            onDismiss = { activeZoneDetail = null },
            viewModel = viewModel
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("tab_content_${tab.id}"),
        contentPadding = PaddingValues(bottom = if (isLandscape && tab.id == "zones") 10.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape && tab.id == "zones") 8.dp else 16.dp)
    ) {
        tab.sections.forEach { section ->
            when (section.lowercase().trim()) {
                "sensors" -> {
                    // Redundant copy removed since it's already displayed in the top header.
                }
                "zones" -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            GlobalSettingsQuickControl(state = state, viewModel = viewModel)
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${state.zones.size} ACTIVE ZONE CONTROLLERS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    val chunkedZones = state.zones.chunked(2)
                    items(chunkedZones) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { zone ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ConsolidatedZoneCard(
                                        zone = zone,
                                        onClick = { activeZoneDetail = zone },
                                        viewModel = viewModel
                                    )
                                }
                            }
                            if (pair.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                "condensed_power" -> {
                    val poolItems = mutableListOf<Any>()
                    val interiorItems = mutableListOf<Any>()
                    val exteriorItems = mutableListOf<Any>()

                    (state.lights + state.switches + state.covers).forEach { item ->
                        val entityId = when (item) {
                            is LightControl -> item.entityId
                            is SwitchControl -> item.entityId
                            is CoverControl -> item.entityId
                            else -> ""
                        }
                        if (entityId.contains("pool")) poolItems.add(item)
                        else if (entityId.contains("exterior") || entityId.contains("porch") || entityId.contains("garage") || entityId.contains("workshop")) exteriorItems.add(item)
                        else interiorItems.add(item)
                    }

                    item { CondensedPowerGroup("INTERIOR", interiorItems, theme, { activeLightPopupId = it }, { activeSwitchPopupId = it }, viewModel) }
                    item { CondensedPowerGroup("EXTERIOR & GARAGE", exteriorItems, theme, { activeLightPopupId = it }, { activeSwitchPopupId = it }, viewModel) }
                    item { CondensedPowerGroup("POOL", poolItems, theme, { activeLightPopupId = it }, { activeSwitchPopupId = it }, viewModel) }
                }
                "lights" -> {
                    val interiorLights = state.lights.filter { !it.entityId.contains("exterior") && !it.entityId.contains("porch") }
                    val exteriorItems = state.lights.filter { it.entityId.contains("exterior") || it.entityId.contains("porch") }
                    
                    if (interiorLights.isNotEmpty()) {
                        item {
                            Text(
                                "INTERIOR LIGHTING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .height(if (interiorLights.size <= 2) 75.dp else 150.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(interiorLights) { light ->
                                    Card(
                                        modifier = Modifier
                                            .testTag("light_card_${light.entityId}")
                                            .clickable { activeLightPopupId = light.entityId },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (light.isOn) hvacActiveCardBgColor(theme.heatColor) else hvacCardBgColor()
                                        ),
                                        border = BorderStroke(1.dp, if (light.isOn) hvacActiveBorderAlphaColor(theme.heatColor) else hvacBorderAlphaColor()),
                                        shape = hvacCardShape(12)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lightbulb,
                                                contentDescription = null,
                                                tint = if (light.isOn) theme.heatColor else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(light.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(
                                                    text = if (light.isOn) "ACTIVE" else "POWER OFF",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (light.isOn) theme.heatColor else Color.White.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (exteriorItems.isNotEmpty()) {
                        item {
                            Text(
                                "EXTERIOR PERIMETER & POWER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .height(if (exteriorItems.size <= 2) 105.dp else 210.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(exteriorItems) { light ->
                                    Card(
                                        modifier = Modifier
                                            .testTag("exterior_card_${light.entityId}")
                                            .clickable { activeLightPopupId = light.entityId },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (light.isOn) hvacActiveCardBgColor(theme.coolColor) else hvacCardBgColor()
                                        ),
                                        border = BorderStroke(1.dp, if (light.isOn) hvacActiveBorderAlphaColor(theme.coolColor) else hvacBorderAlphaColor()),
                                        shape = hvacCardShape(12)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FlashlightOn,
                                                contentDescription = null,
                                                tint = if (light.isOn) theme.coolColor else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(light.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(
                                                    text = if (light.isOn) "ACTIVE" else "POWER OFF",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (light.isOn) theme.coolColor else Color.White.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "switches" -> {
                    if (state.switches.isNotEmpty()) {
                        item {
                            Text(
                                "AUXILIARY POWER CONTROL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                val chunks = state.switches.chunked(2)
                                chunks.forEach { rowSwitches ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowSwitches.forEach { switch ->
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("switch_card_${switch.entityId}")
                                                    .clickable { activeSwitchPopupId = switch.entityId },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (switch.isOn) Color(0xFF10B981).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)
                                                ),
                                                border = BorderStroke(1.dp, if (switch.isOn) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PowerSettingsNew,
                                                        contentDescription = null,
                                                        tint = if (switch.isOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(switch.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        Text(
                                                            text = if (switch.isOn) "ACTIVE" else "POWER OFF",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (switch.isOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (rowSwitches.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "covers" -> {
                    if (state.covers.isNotEmpty()) {
                        item {
                            Text(
                                "GARAGE SECURITY COVERS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        items(state.covers) { cover ->
                            val isOpen = cover.state.lowercase() == "open" || cover.state.lowercase() == "opening"
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isOpen) hvacActiveBorderAlphaColor(Color(0xFFEF4444)) else hvacBorderAlphaColor()
                                    ),
                                    shape = hvacCardShape(12)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Garage,
                                                contentDescription = null,
                                                tint = if (isOpen) Color(0xFFEF4444) else Color(0xFF10B981),
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(cover.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                                Text(
                                                    text = cover.state.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isOpen) Color(0xFFEF4444) else Color(0xFF10B981),
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.controlCover(cover.entityId, "open", cover.name) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Button(
                                                onClick = { viewModel.controlCover(cover.entityId, "close", cover.name) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "updates" -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            UpdatesTab(viewModel = viewModel)
                        }
                    }
                }
                else -> {
                    val dynamicSection = layoutConfig.dynamicSections?.find { it.id.lowercase().trim() == section.lowercase().trim() }
                    if (dynamicSection != null) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                DynamicSectionRenderer(sectionConfig = dynamicSection, theme = theme)
                            }
                        }
                    } else {
                        if (section.lowercase().trim() == "solar") {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    SolarDataPlaceholder(theme = theme)
                                }
                            }
                        } else if (section.lowercase().trim() == "pool") {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    PoolDashboardView(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HvacDashboardContent(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    onShowSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val activeThemePreset by viewModel.selectedThemePreset.collectAsStateWithLifecycle()
    val rawTabs = layoutConfig.tabs ?: emptyList()
    
    val activeTabs = if (rawTabs.isNotEmpty()) rawTabs else listOf(
        TabConfig("zones", "ZONES & UNITS", "layers", listOf("sensors", "zones")),
        TabConfig("aux", "AUXILIARY POWER", "lightbulb", listOf("lights", "switches", "covers")),
        TabConfig("updates", "UPDATES", "cloud_download", listOf("updates"))
    )

    // Keep track of scroll states for each tab to reset back to top on inactivity
    val listStates = remember { List(10) { LazyListState() } }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Revert to Main Tab and Top of Page after 30 seconds of inactivity
    LaunchedEffect(lastInteractionTime) {
        kotlinx.coroutines.delay(30000L)
        selectedTab = 0
        if (listStates.isNotEmpty()) {
            try {
                listStates[0].scrollToItem(0)
            } catch (e: Exception) {
                // Safeguard against scroll interruptions
            }
        }
    }

    var isMenuExpanded by remember { mutableStateOf(!isLandscape) }
    val sidePanelWidth by animateDpAsState(targetValue = if (isMenuExpanded) 260.dp else 72.dp, label = "side_panel_width")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Left Side Panel: Deck Controls with collapsible side bar
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sidePanelWidth)
                    .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
                    .padding(if (isMenuExpanded) 14.dp else 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Modern styled Title / Brand & Toggle
                    if (isMenuExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeCoreIcon(presetId = activeThemePreset, size = 30.dp)
                                Column {
                                    Text(
                                        layoutConfig.appTitle ?: "Home Control",
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        layoutConfig.appSubtitle ?: "HVAC CONTROLLER",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = { isMenuExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowLeft,
                                    contentDescription = "Collapse menu",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Expand menu",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Navigation List
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        activeTabs.forEachIndexed { index, tabConfig ->
                            val isSelected = selectedTab == index
                            val activeColor = when (state.globalSettings.globalHvacMode) {
                                "cool" -> Color(0xFF2196F3)
                                "off" -> Color(0xFF64748B)
                                else -> Color(0xFFF59E0B)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("nav_tab_$index")
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) activeColor.copy(alpha = 0.4f) else Color.Transparent
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isMenuExpanded) Arrangement.Start else Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = getIconByName(tabConfig.icon),
                                    contentDescription = tabConfig.title,
                                    tint = if (isSelected) activeColor else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (isMenuExpanded) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = tabConfig.title,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Panel Deck: Connection and User details
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Connection State
                    val isOfflineSide by viewModel.isOffline.collectAsStateWithLifecycle()
                    val uiStateSide by viewModel.uiState.collectAsStateWithLifecycle()
                    val connectionColor = if (isOfflineSide) {
                        Color(0xFFEF4444)
                    } else {
                        when (uiStateSide) {
                            is HvacUiState.Success -> Color(0xFF10B981)
                            is HvacUiState.Loading -> Color(0xFFF59E0B)
                            is HvacUiState.Error -> Color(0xFFEF4444)
                        }
                    }
                    val connectionText = if (isOfflineSide) {
                        "DISCONNECTED"
                    } else {
                        when (uiStateSide) {
                            is HvacUiState.Success -> "CONNECTED"
                            is HvacUiState.Loading -> "REFRESHING"
                            is HvacUiState.Error -> "DISCONNECTED"
                        }
                    }

                    if (isMenuExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch { viewModel.fetchStates() }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(connectionColor, RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = connectionText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Manual Sync Request",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        // Logout / User Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPERATOR",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.4f),
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { onShowSettings() }
                                        .testTag("settings_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { viewModel.logout() }
                                        .testTag("logout_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = "Log out",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Collapsed Bottom Controls
                        IconButton(
                            onClick = { scope.launch { viewModel.fetchStates() } },
                            modifier = Modifier
                                .size(36.dp)
                                .background(connectionColor.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                                .border(BorderStroke(1.dp, connectionColor.copy(alpha = 0.4f)), RoundedCornerShape(18.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "$connectionText (Click to Sync)",
                                tint = connectionColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onShowSettings() }
                                .testTag("settings_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { viewModel.logout() }
                                .testTag("logout_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Log out",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right Area: Main Lists Column + Side Room Sensors Panel (Tablet Mode)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Main tab lists
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    UpdateAlertBanner(
                        viewModel = viewModel,
                        activeTabs = activeTabs,
                        onNavigateToUpdates = { selectedTab = it }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut()
                            },
                            label = "tab_swapper_landscape"
                        ) { currentTabIdx ->
                            val currentTab = activeTabs.getOrNull(currentTabIdx)
                            if (currentTab != null) {
                                DynamicTabContent(
                                    tab = currentTab,
                                    state = state,
                                    viewModel = viewModel,
                                    listState = listStates[currentTabIdx.coerceIn(0, listStates.lastIndex)]
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Consolidated, minimal right-hand room sensors side panel
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TEMPS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        state.roomSensors.forEach { room ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = when (room.id) {
                                            "living_room" -> Icons.Default.Weekend
                                            "bedroom" -> Icons.Default.Bed
                                            "basement" -> Icons.Default.AcUnit
                                            else -> Icons.Default.Thermostat
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = room.name.uppercase(),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = room.temp?.let { "${it.toInt()}°F" } ?: "--°F",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Original Portrait Layout preserved exactly
        Column(modifier = Modifier.fillMaxSize()) {
            // Horizontal sensors header strip
            RoomSensorsStrip(rooms = state.roomSensors)

            Spacer(modifier = Modifier.height(8.dp))

            // Custom compact tab row navigation mimicking the hot water mode option buttons layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeTabs.forEachIndexed { index, tabConfig ->
                    val isSelected = selectedTab == index
                    val activeColor = when (state.globalSettings.globalHvacMode) {
                        "cool" -> Color(0xFF2196F3)
                        "off" -> Color(0xFF64748B)
                        else -> Color(0xFFF59E0B)
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_tab_$index")
                            .clickable { selectedTab = index },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) activeColor else Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getIconByName(tabConfig.icon),
                                contentDescription = null,
                                tint = if (isSelected) activeColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tabConfig.title,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            UpdateAlertBanner(
                viewModel = viewModel,
                activeTabs = activeTabs,
                onNavigateToUpdates = { selectedTab = it }
            )

            // Body content swap based on selected tab containing robust details
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut()
                },
                label = "tab_swapper"
            ) { currentTabIdx ->
                val currentTab = activeTabs.getOrNull(currentTabIdx)
                if (currentTab != null) {
                    DynamicTabContent(
                        tab = currentTab,
                        state = state,
                        viewModel = viewModel,
                        listState = listStates[currentTabIdx.coerceIn(0, listStates.lastIndex)]
                    )
                }
            }
        }
    }
}
}

@Composable
fun UpdateAlertBanner(
    viewModel: HvacViewModel,
    activeTabs: List<TabConfig>,
    onNavigateToUpdates: (Int) -> Unit
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val theme = LocalHvacTheme.current
    
    val availableUpdate = updateState as? UpdateState.UpdateAvailable
    if (availableUpdate != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = theme.coolColor.copy(alpha = 0.12f)
            ),
            border = BorderStroke(1.dp, theme.coolColor.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 4.dp, end = 4.dp)
                .clickable {
                    val idx = activeTabs.indexOfFirst { it.id == "updates" }
                    if (idx != -1) {
                        onNavigateToUpdates(idx)
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Update available banner",
                    tint = theme.coolColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NEW CORE UPGRADE DETECTED",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.coolColor,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Home Control ${availableUpdate.version} is ready for installation. Tap to upgrade now.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun RoomSensorsStrip(rooms: List<RoomSensor>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("room_sensors_strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rooms.forEach { room ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 135.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                    border = BorderStroke(1.dp, hvacBorderAlphaColor()),
                    shape = hvacCardShape(12)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (room.id) {
                                "living_room" -> Icons.Default.Weekend
                                "bedroom" -> Icons.Default.Bed
                                "basement" -> Icons.Default.AcUnit
                                else -> Icons.Default.Thermostat
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = room.name,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = room.temp?.let { "${it.toInt()}°F" } ?: "--°F",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================== TAB 1: CLIMATE ZONES ========================

@Composable
fun GlobalSettingsQuickControl(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Sizing parameters - scaled down by approx 25% if in tablet mode
    val cardPadding = if (isTablet) 12.dp else 16.dp
    val rowSpacerHeight = if (isTablet) 8.dp else 12.dp

    // Day/Night/Away & Heat/Cool/Off control sizes
    val btnSize = if (isTablet) 30.dp else 40.dp
    val btnIconSize = if (isTablet) 15.dp else 20.dp
    val titleFontSize = if (isTablet) 7.5.sp else 9.sp
    val valueFontSize = if (isTablet) 10.sp else 13.sp

    // Hot Water control sizes
    val waterBtnPadding = if (isTablet) 5.dp else 8.dp
    val waterIconSize = if (isTablet) 15.dp else 20.dp
    val waterSpacerHeight = if (isTablet) 4.dp else 6.dp
    val waterLabelFontSize = if (isTablet) 8.sp else 10.sp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_quick_control_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(14)
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            // First row: HOUSE SCHEDULE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "HOUSE SCHEDULE",
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = if (isTablet) 0.5.sp else 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.globalSettings.houseSchedule.uppercase(),
                        fontSize = valueFontSize,
                        fontWeight = FontWeight.Bold,
                        color = when (state.globalSettings.houseSchedule.lowercase()) {
                            "day" -> Color(0xFFF59E0B)
                            "night" -> Color(0xFF2196F3)
                            else -> Color(0xFF10B981)
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple("Day", Icons.Default.WbSunny, Color(0xFFF59E0B)),
                        Triple("Night", Icons.Default.NightsStay, Color(0xFF2196F3)),
                        Triple("Away", Icons.Default.ExitToApp, Color(0xFF10B981))
                    ).forEach { (label, icon, color) ->
                        val isSelected = state.globalSettings.houseSchedule.lowercase() == label.lowercase()
                        Box(
                            modifier = Modifier
                                .size(btnSize)
                                .clip(RoundedCornerShape(if (isTablet) 8.dp else 12.dp))
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                                )
                                .clickable { viewModel.selectHouseSchedule(label) }
                                .testTag("main_schedule_btn_${label.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Set schedule to $label",
                                modifier = Modifier.size(btnIconSize),
                                tint = if (isSelected) color else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(rowSpacerHeight))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(rowSpacerHeight))

            // Second row: GLOBAL HVAC MODE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GLOBAL SEASON",
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = if (isTablet) 0.5.sp else 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.globalSettings.globalHvacMode.uppercase(),
                        fontSize = valueFontSize,
                        fontWeight = FontWeight.Bold,
                        color = when (state.globalSettings.globalHvacMode.lowercase()) {
                            "heat" -> Color(0xFFF59E0B)
                            "cool" -> Color(0xFF2196F3)
                            else -> Color(0xFF64748B) // off
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple("heat", Icons.Default.Whatshot, Color(0xFFF59E0B)),
                        Triple("cool", Icons.Default.AcUnit, Color(0xFF2196F3)),
                        Triple("off", Icons.Default.PowerSettingsNew, Color(0xFFEF4444))
                    ).forEach { (label, icon, color) ->
                        val isSelected = state.globalSettings.globalHvacMode.lowercase() == label.lowercase()
                        Box(
                            modifier = Modifier
                                .size(btnSize)
                                .clip(RoundedCornerShape(if (isTablet) 8.dp else 12.dp))
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                                )
                                .clickable { viewModel.selectGlobalHvacMode(label) }
                                .testTag("main_hvac_mode_btn_${label.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Set global mode to $label",
                                modifier = Modifier.size(btnIconSize),
                                tint = if (isSelected) color else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(rowSpacerHeight))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(rowSpacerHeight))

            // Third row: HOT WATER CONTROL
            Column {
                Text(
                    "HOT WATER MODE",
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = if (isTablet) 0.5.sp else 1.sp
                )
                Spacer(modifier = Modifier.height(waterSpacerHeight))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 6.dp else 8.dp)
                ) {
                    listOf(
                        WaterHeaterItem("eco", "ECO", Icons.Default.WaterDrop, Color(0xFF10B981)),
                        WaterHeaterItem("heat_pump", "HEAT PUMP", Icons.Default.Settings, Color(0xFFF59E0B)),
                        WaterHeaterItem("high_demand", "BOOST", Icons.Default.Bolt, Color(0xFFEF4444))
                    ).forEach { item ->
                        val option = item.option
                        val label = item.label
                        val icon = item.icon
                        val color = item.color
                        val isSelected = state.globalSettings.waterHeaterMode.lowercase() == option.lowercase()
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("water_heater_btn_${option.lowercase()}")
                                .clickable { viewModel.selectWaterHeaterMode(option) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) color else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(waterBtnPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) color else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(waterIconSize)
                                )
                                Spacer(modifier = Modifier.height(waterSpacerHeight))
                                Text(
                                    text = label,
                                    fontSize = waterLabelFontSize,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClimateZonesTab(
    state: HvacUiState.Success,
    viewModel: HvacViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeZoneDetail by remember { mutableStateOf<ClimateZone?>(null) }

    // If an active zone is selected, show detail configuration popup
    if (activeZoneDetail != null) {
        val currentZoneStatus = state.zones.find { it.key == activeZoneDetail?.key } ?: activeZoneDetail!!
        ZoneDetailPopup(
            zone = currentZoneStatus,
            globalHvacMode = state.globalSettings.globalHvacMode,
            lastNonOffHvacMode = state.globalSettings.lastNonOffHvacMode,
            activeScheduleState = state.globalSettings.houseSchedule,
            onDismiss = { activeZoneDetail = null },
            viewModel = viewModel
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("climate_zones_tab"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GlobalSettingsQuickControl(state = state, viewModel = viewModel)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${state.zones.size} ACTIVE ZONE CONTROLLERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }

        val chunkedZones = state.zones.chunked(2)
        items(chunkedZones) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { zone ->
                    Box(modifier = Modifier.weight(1f)) {
                        ConsolidatedZoneCard(
                            zone = zone,
                            onClick = { activeZoneDetail = zone },
                            viewModel = viewModel
                        )
                    }
                }
                if (pair.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ConsolidatedZoneCard(
    zone: ClimateZone,
    onClick: () -> Unit,
    viewModel: HvacViewModel
) {
    val theme = LocalHvacTheme.current
    val activeColor = when (zone.currentHvacMode.lowercase()) {
        "heat" -> theme.heatColor
        "cool" -> theme.coolColor
        "off" -> theme.offColor
        else -> theme.heatColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("zone_card_${zone.key}"),
        colors = CardDefaults.cardColors(
            containerColor = if (zone.overrideOn) {
                hvacActiveCardBgColor(theme.heatColor)
            } else {
                hvacCardBgColor()
            }
        ),
        border = BorderStroke(
            1.dp,
            if (zone.overrideOn) {
                hvacActiveBorderAlphaColor(theme.heatColor)
            } else if (!zone.autoOn) {
                hvacActiveBorderAlphaColor(theme.boostColor)
            } else {
                hvacBorderAlphaColor()
            }
        ),
        shape = hvacCardShape(12)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Row 1: Icon + Name & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (zone.key) {
                            "main_level" -> Icons.Default.Weekend
                            "bedroom_1", "bedroom_2" -> Icons.Default.Bed
                            "basement" -> Icons.Default.AcUnit
                            else -> Icons.Default.Person
                        },
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = zone.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Fan Speed Badge
                    Box(
                        modifier = Modifier
                            .background(
                                theme.coolColor.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = zone.fanMode.uppercase(),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            color = theme.coolColor,
                            letterSpacing = 0.2.sp
                        )
                    }

                    // Auto/Hold Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (zone.autoOn) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (zone.autoOn) "AUTO" else "HOLD",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            color = if (zone.autoOn) Color(0xFF10B981) else Color(0xFFEF4444),
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Status Mode + Current & Target Temps side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = zone.currentHvacMode.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = activeColor,
                    letterSpacing = 0.5.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current Temp
                    Text(
                        text = zone.currentTemp?.let { "${it.toInt()}°" } ?: "--°",
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    
                    Text(
                        text = " / ",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    // Target Temp
                    Text(
                        text = "${zone.targetTemp?.toInt() ?: "--"}°",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor
                    )
                }
            }
        }
    }
}

@Composable
fun LightingControlPopup(
    entityId: String,
    name: String,
    isOn: Boolean,
    brightness: Int?,
    isLight: Boolean,
    onDismiss: () -> Unit,
    viewModel: HvacViewModel
) {
    val theme = LocalHvacTheme.current
    val activeColor = if (isOn) theme.heatColor else Color(0xFF64748B)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("lighting_control_popup_$entityId"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(activeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLight) Icons.Default.Lightbulb else Icons.Default.FlashlightOn,
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isLight) "DIMMABLE LIGHT" else "POWER SWITCH",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Popup",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isOn) Icons.Default.Power else Icons.Default.PowerOff,
                            contentDescription = null,
                            tint = if (isOn) activeColor else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("POWER STATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                            Text(if (isOn) "ACTIVE" else "POWER OFF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = isOn,
                        onCheckedChange = { checked ->
                            if (isLight) {
                                viewModel.toggleLight(entityId, isOn, name)
                            } else {
                                viewModel.toggleSwitch(entityId, isOn, name)
                            }
                        },
                        modifier = Modifier.testTag("lighting_popup_power_switch")
                    )
                }

                if (isLight) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        var sliderValue by remember(brightness) {
                            mutableStateOf(brightness?.toFloat() ?: 255f)
                        }
                        val pct = ((sliderValue / 255f) * 100).toInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = activeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("BRIGHTNESS LEVEL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                    Text("$pct%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Text(
                                text = "${sliderValue.toInt()} / 255",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        androidx.compose.material3.Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                viewModel.setLightBrightness(entityId, sliderValue.toInt(), name)
                            },
                            valueRange = 0f..255f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = activeColor,
                                activeTrackColor = activeColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lighting_popup_brightness_slider")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoneDetailPopup(
    zone: ClimateZone,
    globalHvacMode: String,
    lastNonOffHvacMode: String,
    activeScheduleState: String,
    onDismiss: () -> Unit,
    viewModel: HvacViewModel
) {
    val activeColor = when (zone.currentHvacMode.lowercase()) {
        "heat" -> Color(0xFFF59E0B)
        "cool" -> Color(0xFF2196F3)
        "off" -> Color(0xFF64748B)
        else -> Color(0xFFF59E0B)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("zone_detail_popup_${zone.key}"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B) // slate dark background
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Main Header Row: Icon, zone name + close button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(activeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (zone.key) {
                                    "main_level" -> Icons.Default.Weekend
                                    "bedroom_1", "bedroom_2" -> Icons.Default.Bed
                                    "basement" -> Icons.Default.AcUnit
                                    else -> Icons.Default.Person
                                },
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = zone.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (zone.autoOn) "AUTO SCHEDULE" else "HOLD MANUAL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (zone.autoOn) Color(0xFF10B981) else Color(0xFFEF4444),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = zone.currentHvacMode.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor
                                )
                            }
                        }
                    }

                    // Top Back/Dismiss Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 12.dp))

                // Temps row & power button layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = zone.currentTemp?.let { "${it.toInt()}" } ?: "--",
                                fontWeight = FontWeight.Light,
                                fontSize = 28.sp,
                                color = Color.White
                            )
                            Text(
                                text = "°F",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 3.dp, start = 1.dp)
                            )
                        }
                        Text(
                            text = "Current Temp",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    // Power Control Toggle
                    IconButton(
                        onClick = {
                            viewModel.toggleZonePower(zone.climateEntityId, zone.currentHvacMode, globalHvacMode, zone.name)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (zone.currentHvacMode.lowercase() != "off") {
                                    activeColor.copy(alpha = 0.15f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .testTag("zone_power_button_${zone.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Power",
                            tint = if (zone.currentHvacMode.lowercase() != "off") activeColor else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${zone.targetTemp?.toInt() ?: "--"}°",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = activeColor
                        )
                        Text(
                            text = "Set Target",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Schedule Setpoints Section
                val activeModeForPresets = if (globalHvacMode.lowercase() == "off") lastNonOffHvacMode else globalHvacMode
                val blockPresets = if (activeModeForPresets.lowercase() == "cool") zone.presetsCool else zone.presetsHeat
                val presetLabelType = if (activeModeForPresets.lowercase() == "cool") "Cool" else "Heat"

                Text(
                    text = "SCHEDULE SETPOINTS ($presetLabelType Mode)",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                listOf(
                    Triple("DAY", blockPresets.dayValue, activeScheduleState == "Day"),
                    Triple("NIGHT", blockPresets.nightValue, activeScheduleState == "Night"),
                    Triple("AWAY", blockPresets.awayValue, activeScheduleState == "Away")
                ).forEach { (label, value, isActive) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) activeColor.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.02f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) activeColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isActive) activeColor else Color.White.copy(alpha = 0.8f),
                                        letterSpacing = 0.5.sp
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(activeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "ACTIVE",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = activeColor,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isActive) "Following active schedule preset" else "Scheduled fallback value",
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = if (isActive) 0.6f else 0.4f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            value?.let { currentVal ->
                                                val newVal = currentVal - 1.0
                                                val entityId = when (label) {
                                                    "DAY" -> blockPresets.day
                                                    "NIGHT" -> blockPresets.night
                                                    else -> blockPresets.away
                                                }
                                                viewModel.setPresetTemperature(entityId, newVal, "${zone.name} $label $presetLabelType")
                                                if (isActive) {
                                                    viewModel.setTargetTemperature(zone.climateEntityId, newVal, zone.name)
                                                }
                                            }
                                         }
                                         .testTag("preset_decrease_${label.lowercase()}_${zone.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "decrease preset",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = value?.let { "${it.toInt()}°" } ?: "--°",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) activeColor else Color.White
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            value?.let { currentVal ->
                                                val newVal = currentVal + 1.0
                                                val entityId = when (label) {
                                                    "DAY" -> blockPresets.day
                                                    "NIGHT" -> blockPresets.night
                                                    else -> blockPresets.away
                                                }
                                                viewModel.setPresetTemperature(entityId, newVal, "${zone.name} $label $presetLabelType")
                                                if (isActive) {
                                                    viewModel.setTargetTemperature(zone.climateEntityId, newVal, zone.name)
                                                }
                                            }
                                         }
                                         .testTag("preset_increase_${label.lowercase()}_${zone.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "increase preset",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Automation switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.toggleInputBoolean(zone.autoEntityId, zone.autoOn, "${zone.name} Auto")
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Automations", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Switch(
                                checked = zone.autoOn,
                                onCheckedChange = {
                                    viewModel.toggleInputBoolean(zone.autoEntityId, zone.autoOn, "${zone.name} Auto")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF10B981),
                                    checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.toggleInputBoolean(zone.overrideEntityId, zone.overrideOn, "${zone.name} Override")
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("OVERRIDE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Protection", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Switch(
                                checked = zone.overrideOn,
                                onCheckedChange = {
                                    viewModel.toggleInputBoolean(zone.overrideEntityId, zone.overrideOn, "${zone.name} Override")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFF59E0B),
                                    checkedTrackColor = Color(0xFFF59E0B).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ventilation & Vane deflection
                Text(
                    "VENTILATION & VANE DEFLECTION",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Vane Direction", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        val vaneScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .padding(4.dp)
                                .horizontalScroll(vaneScrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            zone.vaneOptions.forEach { mode ->
                                val isSelected = zone.vaneMode.lowercase() == mode.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) activeColor else Color.Transparent)
                                        .clickable {
                                            viewModel.selectVaneMode(zone.tiltEntityId, mode, zone.name)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Fan Power", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        val fanScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .padding(4.dp)
                                .horizontalScroll(fanScrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            zone.fanOptions.forEach { speed ->
                                val isSelected = zone.fanMode.lowercase() == speed.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) activeColor else Color.Transparent)
                                        .clickable {
                                            viewModel.selectFanMode(zone.fanEntityId, speed, zone.name)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = speed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom back/dismiss button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PresetTempRow(
    label: String,
    value: Double?,
    tint: Color,
    onAdjust: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "decrease config",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { value?.let { onAdjust(it - 1.0) } }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value?.let { "${it.toInt()}°" } ?: "--°",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = tint
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "increase config",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { value?.let { onAdjust(it + 1.0) } }
            )
        }
    }
}

// ======================== TAB 2: AUXILIARIES (LIGHTS & POWER) ========================

@Composable
fun AuxiliariesTab(
    state: HvacUiState.Success,
    viewModel: HvacViewModel
) {
    var activeLightPopupId by remember { mutableStateOf<String?>(null) }
    var activeSwitchPopupId by remember { mutableStateOf<String?>(null) }
    
    // Popup lookup & display
    val currentLightPopupState = activeLightPopupId?.let { id -> state.lights.find { it.entityId == id } }
    if (activeLightPopupId != null && currentLightPopupState != null) {
        LightingControlPopup(
            entityId = currentLightPopupState.entityId,
            name = currentLightPopupState.name,
            isOn = currentLightPopupState.isOn,
            brightness = currentLightPopupState.brightness,
            isLight = true,
            onDismiss = { activeLightPopupId = null },
            viewModel = viewModel
        )
    }

    val currentSwitchPopupState = activeSwitchPopupId?.let { id -> state.switches.find { it.entityId == id } }
    if (activeSwitchPopupId != null && currentSwitchPopupState != null) {
        LightingControlPopup(
            entityId = currentSwitchPopupState.entityId,
            name = currentSwitchPopupState.name,
            isOn = currentSwitchPopupState.isOn,
            brightness = null,
            isLight = false,
            onDismiss = { activeSwitchPopupId = null },
            viewModel = viewModel
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("auxiliaries_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "INTERIOR LIGHTING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        val interiorLights = state.lights.filter { !it.entityId.contains("exterior") && !it.entityId.contains("porch") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .height(150.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(interiorLights) { light ->
                    Card(
                        modifier = Modifier
                            .testTag("light_card_${light.entityId}")
                            .clickable { activeLightPopupId = light.entityId },
                        colors = CardDefaults.cardColors(
                            containerColor = if (light.isOn) Color(0xFFF59E0B).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (light.isOn) Color(0xFFF59E0B).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = if (light.isOn) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(light.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = if (light.isOn) "ACTIVE" else "POWER OFF",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (light.isOn) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "EXTERIOR PERIMETER & POWER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        val exteriorItems = state.lights.filter { it.entityId.contains("exterior") || it.entityId.contains("porch") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .height(210.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(exteriorItems) { light ->
                    Card(
                        modifier = Modifier
                            .testTag("exterior_card_${light.entityId}")
                            .clickable { activeLightPopupId = light.entityId },
                        colors = CardDefaults.cardColors(
                            containerColor = if (light.isOn) Color(0xFF2196F3).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (light.isOn) Color(0xFF2196F3).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashlightOn,
                                contentDescription = null,
                                tint = if (light.isOn) Color(0xFF2196F3) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(light.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = if (light.isOn) "ACTIVE" else "POWER OFF",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (light.isOn) Color(0xFF2196F3) else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "AUXILIARY POWER CONTROL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val chunks = state.switches.chunked(2)
                chunks.forEach { rowSwitches ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowSwitches.forEach { switch ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("switch_card_${switch.entityId}")
                                    .clickable { activeSwitchPopupId = switch.entityId },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (switch.isOn) Color(0xFF10B981).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)
                                ),
                                border = BorderStroke(1.dp, if (switch.isOn) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = if (switch.isOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(switch.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(
                                            text = if (switch.isOn) "ACTIVE" else "POWER OFF",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (switch.isOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                        if (rowSwitches.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                "GARAGE SECURITY COVERS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(state.covers) { cover ->
            val isOpen = cover.state.lowercase() == "open" || cover.state.lowercase() == "opening"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(
                    1.dp,
                    if (isOpen) Color(0xFFEF4444).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Garage,
                            contentDescription = null,
                            tint = if (isOpen) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(cover.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text(
                                text = "State: ${cover.state.uppercase()}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOpen) Color(0xFFEF4444) else Color(0xFF10B981)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.toggleCover(cover.entityId, cover.state, cover.name)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOpen) Color(0xFFEF4444).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isOpen) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = if (isOpen) "ACTIVATE CLOSE" else "ACTIVATE OPEN",
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ======================== TAB 4: SYSTEM UPDATES ========================

@Composable
fun UpdatesTab(
    viewModel: HvacViewModel
) {
    val context = LocalContext.current
    val theme = LocalHvacTheme.current
    val layoutVersion by viewModel.layoutVersion.collectAsStateWithLifecycle()
    val activeVersion by viewModel.activeVersion.collectAsStateWithLifecycle()
    val layoutUpdateError by viewModel.layoutUpdateError.collectAsStateWithLifecycle()
    val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val githubBranch by viewModel.githubBranch.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups >= units.size) units.size - 1 else digitGroups
        return try {
            String.format("%.2f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
        } catch (e: Exception) {
            "$bytes B"
        }
    }

    LaunchedEffect(activeVersion) {
        viewModel.checkForUpdates(activeVersion)
    }

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Success) {
            viewModel.installApk(context, (updateState as UpdateState.Success).apkPath)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_tab")
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // UNIFIED SYSTEM DECK UPDATER CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
            border = BorderStroke(1.dp, hvacBorderAlphaColor()),
            shape = hvacCardShape(16)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DYNAMIC SYSTEM DECK SYNC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Over-the-Air Broad Update",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = theme.ecoColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sync details are processed over-the-air to dynamically load configurations, layouts, and entities directly from your GitHub repository branch without rebuilding the application binary.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Connection Info Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = theme.coolColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Repository: $githubRepo",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = theme.coolColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Branch: $githubBranch",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Source File Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$githubRepo/blob/$githubBranch/layout_config.json"))
                            context.startActivity(webIntent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VIEW ON GITHUB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://raw.githubusercontent.com/$githubRepo/$githubBranch/layout_config.json"))
                            context.startActivity(webIntent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VIEW RAW JSON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                // Active Version & Live Actions Block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ACTIVE CORE STATUS",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            activeVersion,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.ecoColor
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.checkForUpdates(activeVersion)
                            android.widget.Toast.makeText(context, "Scanning branch for broad updates...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SCAN GITHUB", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamically Render UpdateState details
                when (val state = updateState) {
                    is UpdateState.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Scanning repository branch for updates...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = theme.ecoColor,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                    is UpdateState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "UPDATE CHECK FAILURE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFEF5350)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    is UpdateState.NoReleases -> {
                        Text(
                            "No commits or releases found on specified branch.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    is UpdateState.UpToDate -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = theme.ecoColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "System configuration is fully up to date (v${state.version})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE8F5E9)
                                    )
                                }
                            }

                            if (state.downloadUrl != null) {
                                Button(
                                    onClick = {
                                        viewModel.downloadUpdateAndInstall(context, state.downloadUrl)
                                        android.widget.Toast.makeText(context, "Initiating configuration pull...", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("FORCE REINSTALL CURRENT CONFIG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                    is UpdateState.UpdateAvailable -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = theme.ecoColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "NEW SYSTEM DECK CONFIG v${state.version} AVAILABLE!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = theme.ecoColor
                                        )
                                    }
                                    if (state.size > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Dynamic config payload size: ${formatBytes(state.size)}",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            if (state.releaseNotes.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        "UPDATE DETAILS:",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.5f),
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.releaseNotes,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 8,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.downloadUpdateAndInstall(context, state.downloadUrl)
                                    android.widget.Toast.makeText(context, "Pulling dynamic configurations...", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.ecoColor,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("DOWNLOAD & APPLY SYSTEM UPDATE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                    }
                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Downloading broad update payload...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    if (state.progress >= 0) "${state.progress}%" else "In progress",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = theme.ecoColor
                                )
                            }

                            if (state.progress >= 0) {
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = theme.ecoColor,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = theme.ecoColor,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                    is UpdateState.Installing -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = theme.ecoColor,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Applying broad deck update...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    "${state.progress}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = theme.ecoColor
                                )
                            }
                            
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = theme.ecoColor,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            
                            Text(
                                text = state.currentAction,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    is UpdateState.Success -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = theme.ecoColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Broad update package ready to install!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE8F5E9)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.installApk(context, state.apkPath)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.ecoColor,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("INSTALL APK NOW", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                viewModel.checkForUpdates(activeVersion)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("INITIALIZE BROAD UPDATE SCAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // RESTORE BUILT-IN LAYOUT ACTION
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = hvacCardShape(12),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "LAYOUT SYNC FIX",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Restore app's built-in layout configuration template",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Button(
                    onClick = {
                        viewModel.resetLayoutToDefault()
                        android.widget.Toast.makeText(context, "Layout has been reset to Default v5.1.0!", android.widget.Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = theme.coolColor
                    ),
                    border = BorderStroke(1.dp, theme.coolColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restore Default",
                        modifier = Modifier.size(13.dp),
                        tint = theme.coolColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTORE DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.coolColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HvacLoginScreen(
    viewModel: HvacViewModel,
    glowColor: Color,
    bgGradient: Brush
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginError by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isLoggingIn.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = size.width * 0.85f
                    ),
                    radius = size.width * 0.85f,
                    center = center
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon / Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Weekend,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Home Control",
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontSize = 24.sp,
                color = Color.White
            )

            Text(
                "HVAC SYSTEM CONTROLLER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Form container Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "SECURE SYSTEM LOGIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Username input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username", color = Color.White.copy(alpha = 0.4f)) },
                        placeholder = { Text("admin", color = Color.White.copy(alpha = 0.2f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF10B981),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = Color.White.copy(alpha = 0.4f)) },
                        placeholder = { Text("••••••••", color = Color.White.copy(alpha = 0.2f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF10B981),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Guide / Instructions
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Enter your Home Assistant username and password credentials. The configured server address is managed securely in the background.",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                lineHeight = 12.sp
                            )
                        }
                    }

                    if (loginError != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = loginError!!,
                                color = Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons
                    Button(
                        onClick = { viewModel.login(username, password) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CONNECTING...", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        } else {
                            Text(
                                "LOG IN",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = Color.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var showServerConfig by remember { mutableStateOf(false) }
                    val currentHaUrl by viewModel.haUrl.collectAsState()
                    val defaultHaUrl = remember { viewModel.getDefaultHaUrl() }
                    var urlText by remember(currentHaUrl) { mutableStateOf(currentHaUrl) }
                    val context = LocalContext.current

                    AnimatedVisibility(visible = !showServerConfig) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { showServerConfig = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CHANGE SERVER ADDRESS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showServerConfig) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SERVER CONFIGURATION",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 1.sp
                                )

                                IconButton(
                                    onClick = { showServerConfig = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Collapse Settings",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("Server Base URL", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color(0xFF10B981),
                                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_ha_url_input"),
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF10B981).copy(alpha = 0.8f))
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (urlText.isNotBlank()) {
                                            viewModel.updateHaUrl(urlText)
                                            Toast.makeText(context, "Server address updated successfully!", Toast.LENGTH_SHORT).show()
                                            showServerConfig = false
                                        } else {
                                            Toast.makeText(context, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                                        contentColor = Color(0xFF10B981)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("SAVE URL", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }

                                val isDefault = urlText.trim().removeSuffix("/") == defaultHaUrl.trim().removeSuffix("/")
                                if (!isDefault) {
                                    Button(
                                        onClick = {
                                            viewModel.restoreDefaultHaUrl()
                                            Toast.makeText(context, "Restored default Server URL!", Toast.LENGTH_SHORT).show()
                                            urlText = defaultHaUrl
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f),
                                            contentColor = Color(0xFFEF5350)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("RESTORE DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HvacSettingsDialog(
    viewModel: HvacViewModel,
    onDismiss: () -> Unit
) {
    val forceScreenOn by viewModel.forceScreenOn.collectAsState()
    val forceFullScreen by viewModel.forceFullScreen.collectAsState()
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
    val theme = LocalHvacTheme.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    var activeSubTab by remember { mutableStateOf(0) } // 0: Themes & Graphics, 1: Technical connections

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = screenHeight * 0.88f)
                .padding(16.dp)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0C1322) // match HvacBgSlate
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title and Close button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SYSTEM PARAMETERS",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "CONFIGURATION & PREFERENCES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color.White
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Dual-Tab Segmented Selection Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val subTabOptions = listOf(
                        Triple(0, "THEMES & GRAPHICS", Icons.Default.Palette),
                        Triple(1, "SYSTEM ENGINES", Icons.Default.Settings),
                        Triple(2, "CAR AUTOMOTIVE", Icons.Default.DirectionsCar)
                    )
                    subTabOptions.forEach { (idx, label, icon) ->
                        val isSelected = activeSubTab == idx
                        val activeColor = theme.coolColor
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    BorderStroke(1.dp, if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { activeSubTab = idx }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) activeColor else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                // Feature toggles list
                if (activeSubTab == 1) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    // Toggle 1: Force Screen On
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(hvacCardBgColor(), hvacCardShape(16))
                            .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(16))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Force Screen On",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Keeps the screen active at all times",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Switch(
                            checked = forceScreenOn,
                            onCheckedChange = { viewModel.setForceScreenOn(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                             )
                        )
                    }

                    // Toggle 1.5: Force Full Screen Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(hvacCardBgColor(), hvacCardShape(16))
                            .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(16))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Force Full Screen Mode",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Hides system and navigation status bars",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Switch(
                            checked = forceFullScreen,
                            onCheckedChange = { viewModel.setForceFullScreen(it) },
                            modifier = Modifier.testTag("force_full_screen_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }

                    // Toggle 2: Dark Mode Theme Accent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(hvacCardBgColor(), hvacCardShape(16))
                            .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(16))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF2196F3).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (darkModeEnabled) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Dark Mode Theme",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (darkModeEnabled) "High contrast night-eye theme active" else "Standard daytime display theme active",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Switch(
                            checked = darkModeEnabled,
                            onCheckedChange = { viewModel.setDarkModeEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }
                }

                if (activeSubTab == 0) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Theme Preset Selection Panel
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "SYSTEM VISUAL THEME CONTROLS",
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = theme.coolColor,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Select any premium preset theme engine below to update the deck visual cue",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }

                    HvacThemePresetsList.forEach { preset ->
                        val isSelected = viewModel.selectedThemePreset.collectAsState().value == preset.id
                        val presetAccentColor = parseHexColor(preset.accentColorHex, Color.White)
                        val presetCoolColor = parseHexColor(preset.coolColorHex, Color.White)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSelectedThemePreset(preset.id) }
                                .testTag("theme_preset_${preset.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) theme.coolColor else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color.White.copy(alpha = 0.04f), CircleShape)
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ThemeCoreIcon(presetId = preset.id, size = 30.dp)
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = preset.name,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = if (isSelected) theme.coolColor else Color.White
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(modifier = Modifier.size(6.dp).background(presetAccentColor, CircleShape))
                                            Box(modifier = Modifier.size(6.dp).background(presetCoolColor, CircleShape))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = preset.description,
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        lineHeight = 11.sp
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (isSelected) theme.coolColor else Color.Transparent,
                                            CircleShape
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) theme.coolColor else Color.White.copy(alpha = 0.3f)
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // DESIGN STYLE ADJUSTMENTS
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "DESIGN STYLE ADJUSTMENTS",
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tailor layout shapes and glassy depth across card surfaces",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }

                    // 1. CARD CORNER DEPTH
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CARD CORNER DEPTH",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )

                        val activeStyle by viewModel.cardCornerStyle.collectAsState()
                        val cornerOptions = listOf(
                            Triple("sharp", "SHARP EDGE", "Symmetric 0dp tech look"),
                            Triple("rounded", "ROUNDIVE", "Standard fluid 12dp style"),
                            Triple("ultra_rounded", "ULTRA CURVE", "Liquid glass 26dp frame")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            cornerOptions.forEach { (styleKey, title, desc) ->
                                val isChosen = activeStyle == styleKey
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clickable { viewModel.setCardCornerStyle(styleKey) }
                                        .testTag("corner_style_${styleKey}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChosen) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChosen) theme.heatColor else Color.White.copy(alpha = 0.05f)
                                    ),
                                    shape = RoundedCornerShape(
                                        when (styleKey) {
                                            "sharp" -> 0.dp
                                            "ultra_rounded" -> 16.dp
                                            else -> 8.dp
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp).fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = if (isChosen) theme.heatColor else Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            fontSize = 7.sp,
                                            color = Color.White.copy(alpha = 0.4f),
                                            lineHeight = 8.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. GLASSMORPHIC OPACITY
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val activeOpacity by viewModel.cardOpacity.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GLASS SURFACE OPACITY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${(activeOpacity * 100).toInt()}% GLASS",
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = theme.coolColor
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(hvacCardBgColor(), hvacCardShape(12))
                                .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(12))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = "Opacity icon",
                                tint = theme.coolColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Slider(
                                value = activeOpacity,
                                onValueChange = { viewModel.setCardOpacity(it) },
                                valueRange = 0.01f..0.20f,
                                modifier = Modifier.weight(1f).testTag("glass_opacity_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.coolColor,
                                    activeTrackColor = theme.coolColor.copy(alpha = 0.5f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }

                    // 2.5 BACKGROUND PATTERN & ART
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BACKGROUND STYLE & AMBIENT ART",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )

                        val activeDesign by viewModel.backgroundDesign.collectAsState()
                        val designOptions = listOf(
                            Triple("radial_glow", "RADIAL GLOW", "Pulsing soft center glow"),
                            Triple("grid", "SYSTEM GRID", "Technical sci-fi vectors"),
                            Triple("nebula", "NEBULA BLENDS", "Overlapping color spheres"),
                            Triple("aurora", "AURORA SHEEN", "Shimmering geometric curtains"),
                            Triple("minimal", "SLATE SOLID", "Clean fluid linear backdrop")
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            items(designOptions) { (key, title, desc) ->
                                val isChosen = activeDesign == key
                                Card(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .fillMaxHeight()
                                        .clickable { viewModel.setBackgroundDesign(key) }
                                        .testTag("bg_design_${key}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChosen) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChosen) theme.coolColor else Color.White.copy(alpha = 0.05f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp).fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = if (isChosen) theme.coolColor else Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            fontSize = 7.sp,
                                            color = Color.White.copy(alpha = 0.4f),
                                            lineHeight = 8.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. LIVE INTERACTIVE DECK PREVIEW
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "LIVE DESIGN SYSTEM PREVIEW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = theme.heatColor,
                            letterSpacing = 0.5.sp
                        )
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_style_preview_card"),
                            colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                            border = BorderStroke(1.dp, hvacBorderAlphaColor()),
                            shape = hvacCardShape(14)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Thermostat,
                                            contentDescription = null,
                                            tint = theme.heatColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PREVIEW CHANNEL",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(theme.ecoColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LIVE RENDER",
                                            color = theme.ecoColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Selected Profile Values",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).background(theme.heatColor, CircleShape))
                                            Box(modifier = Modifier.size(8.dp).background(theme.coolColor, CircleShape))
                                            Box(modifier = Modifier.size(8.dp).background(theme.ecoColor, CircleShape))
                                            Text(
                                                text = "${(theme.cardOpacity * 100).toInt()}% Glass | ${theme.cardCornerStyle.uppercase().replace("_", " ")}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(hvacCardBgColor(), hvacCardShape(6))
                                                .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(6)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(hvacActiveCardBgColor(theme.coolColor), hvacCardShape(6))
                                                .border(BorderStroke(1.dp, hvacActiveBorderAlphaColor(theme.coolColor)), hvacCardShape(6)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = theme.coolColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                } // Ends if (activeSubTab == 0)

                if (activeSubTab == 2) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Section 1: Automotive Screen Layout Mode
                        Column {
                            Text(
                                text = "AUTOMOTIVE SCREEN LAYOUT",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Choose how the dashboard screen presents on your Android Auto deck unit",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }

                        val autoLayoutStyle by viewModel.autoLayoutStyle.collectAsStateWithLifecycle()
                        val autoPrimaryZone by viewModel.autoPrimaryZone.collectAsStateWithLifecycle()
                        val autoShowFanAction by viewModel.autoShowFanAction.collectAsStateWithLifecycle()
                        val autoShowPowerAction by viewModel.autoShowPowerAction.collectAsStateWithLifecycle()
                        val autoTempStep by viewModel.autoTempStep.collectAsStateWithLifecycle()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val layoutOptions = listOf("list" to "LIST OVERVIEW", "pane" to "FOCUS ZONE PANE")
                            layoutOptions.forEach { (styleKey, styleLabel) ->
                                val isLayoutSelected = autoLayoutStyle == styleKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isLayoutSelected) theme.coolColor.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(
                                            BorderStroke(1.dp, if (isLayoutSelected) theme.coolColor.copy(alpha = 0.2f) else Color.Transparent),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setAutoLayoutStyle(styleKey) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = styleLabel,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        color = if (isLayoutSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        // Section 2: Primary Focus Zone
                        Column {
                            Text(
                                text = "PRIMARY FOCUS ZONE",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            val hvacUiState by viewModel.uiState.collectAsStateWithLifecycle()
                            val zonesList = if (hvacUiState is HvacUiState.Success) (hvacUiState as HvacUiState.Success).zones else emptyList()

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Select default climate zone for Focus Pane mode",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Display available zones beautifully in a horizontal scrollable row
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp)
                                    ) {
                                        // Option: Default / Auto
                                        val isAllSelected = autoPrimaryZone == "all"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isAllSelected) theme.coolColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                                .border(
                                                    BorderStroke(1.dp, if (isAllSelected) theme.coolColor else Color.White.copy(alpha = 0.1f)),
                                                    RoundedCornerShape(20.dp)
                                                )
                                                .clickable { viewModel.setAutoPrimaryZone("all") }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "AUTO (FIRST ZONE)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAllSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                            )
                                        }

                                        zonesList.forEach { zone ->
                                            val isSelected = autoPrimaryZone == zone.key
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(if (isSelected) theme.coolColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                                    .border(
                                                        BorderStroke(1.dp, if (isSelected) theme.coolColor else Color.White.copy(alpha = 0.1f)),
                                                        RoundedCornerShape(20.dp)
                                                    )
                                                    .clickable { viewModel.setAutoPrimaryZone(zone.key) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = zone.name.uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 3: Action Button Customization
                        Column {
                            Text(
                                text = "QUICK ACTION CONFIGURATIONS",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Show Power Switch toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(hvacCardBgColor(), hvacCardShape(16))
                                    .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(16))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show Power Toggle Button",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Enable turn off/on zone action button",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Switch(
                                    checked = autoShowPowerAction,
                                    onCheckedChange = { viewModel.setAutoShowPowerAction(it) },
                                    modifier = Modifier.testTag("auto_power_action_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF10B981),
                                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color(0xFF64748B),
                                        uncheckedTrackColor = Color(0xFF1E293B)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Show Fan Cycling toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(hvacCardBgColor(), hvacCardShape(16))
                                    .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(16))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show Fan Speed Button",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Enable cycle fan speeds action button",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Switch(
                                    checked = autoShowFanAction,
                                    onCheckedChange = { viewModel.setAutoShowFanAction(it) },
                                    modifier = Modifier.testTag("auto_fan_action_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF10B981),
                                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color(0xFF64748B),
                                        uncheckedTrackColor = Color(0xFF1E293B)
                                    )
                                )
                            }
                        }

                        // Section 4: Temp Delta Adjust Step
                        Column {
                            Text(
                                text = "TEMPERATURE ADJUST STEP",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = theme.coolColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(hvacCardBgColor(), hvacCardShape(16))
                                    .border(BorderStroke(1.dp, hvacBorderAlphaColor()), hvacCardShape(16))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val steps = listOf(0.5f to "± 0.5°F", 1.0f to "± 1.0°F", 2.0f to "± 2.0°F")
                                steps.forEach { (stepVal, stepLabel) ->
                                    val isStepSelected = autoTempStep == stepVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isStepSelected) theme.coolColor.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                BorderStroke(1.dp, if (isStepSelected) theme.coolColor.copy(alpha = 0.2f) else Color.Transparent),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { viewModel.setAutoTempStep(stepVal) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stepLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isStepSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeSubTab == 1) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // GitHub Settings overrides
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "GITHUB UPDATER & SPECIFICATION",
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configure repository source for OTA layout designs & compiled APKs",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    val currentRepo by viewModel.githubRepo.collectAsState()
                    val currentBranch by viewModel.githubBranch.collectAsState()
                    val currentToken by viewModel.githubToken.collectAsState()
                    var repoText by remember(currentRepo) { mutableStateOf(currentRepo) }
                    var branchText by remember(currentBranch) { mutableStateOf(currentBranch) }
                    var tokenText by remember(currentToken) { mutableStateOf(currentToken) }
                    val context = LocalContext.current

                    OutlinedTextField(
                        value = repoText,
                        onValueChange = { repoText = it },
                        label = { Text("GitHub Repository (owner/repo)", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF2196F3),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF2196F3).copy(alpha = 0.8f))
                        }
                    )

                    OutlinedTextField(
                        value = branchText,
                        onValueChange = { branchText = it },
                        label = { Text("Active Branch", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF2196F3),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_branch_input"),
                        leadingIcon = {
                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF2196F3).copy(alpha = 0.8f))
                        }
                    )

                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("GitHub Token / PAT (Highly Recommended)", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF2196F3),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_token_input"),
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF2196F3).copy(alpha = 0.8f))
                        },
                        supportingText = {
                            Text(
                                "Prevents API rate limiting, facilitating instantaneous and reliable layout update checks over CDN cache.",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (repoText.isNotBlank() && branchText.isNotBlank()) {
                                    viewModel.updateGithubSettings(repoText, branchText, tokenText)
                                    Toast.makeText(context, "GitHub repository parameters saved!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Parameters cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                                contentColor = Color(0xFF2196F3)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SAVE GITHUB SOURCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }

                        val isDefault = currentRepo == "cstone1983/HVAC-Android-App" && currentBranch == "main"
                        if (!isDefault) {
                            Button(
                                onClick = {
                                    viewModel.updateGithubSettings("cstone1983/HVAC-Android-App", "main", "")
                                    Toast.makeText(context, "Restored default GitHub repository source!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f),
                                    contentColor = Color(0xFFEF5350)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("RESTORE DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // OTA Update Simulator config
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "OTA UPDATE SIMULATOR & TESTING",
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Simulate mock system upgrades to test download/install routines or live increment versioning to repeat OTA loops.",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    val simLatestVersion by viewModel.simulatedLatestVersion.collectAsStateWithLifecycle()
                    val activeInstalledVersion by viewModel.activeVersion.collectAsStateWithLifecycle()
                    var simText by remember(simLatestVersion) { mutableStateOf(simLatestVersion) }
                    val context = LocalContext.current

                    OutlinedTextField(
                        value = simText,
                        onValueChange = { simText = it },
                        label = { Text("Simulated Update Version Tag", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = theme.coolColor,
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sim_version_input"),
                        leadingIcon = {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = theme.coolColor.copy(alpha = 0.8f))
                        },
                        supportingText = {
                            Text(
                                "Active running version is: $activeInstalledVersion. Set to a larger tag (e.g. v2.2.6) to trigger the updater.",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (simText.isNotBlank()) {
                                    viewModel.setSimulatedLatestVersion(simText)
                                    Toast.makeText(context, "Simulated latest target version set to $simText", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Version cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.coolColor.copy(alpha = 0.15f),
                                contentColor = theme.coolColor
                            ),
                            border = BorderStroke(1.dp, theme.coolColor.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SAVE SIMULATED VERSION", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.bumpSimulatedLatestVersion()
                                Toast.makeText(context, "Bumped target! OTA update is now available.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.ecoColor.copy(alpha = 0.15f),
                                contentColor = theme.ecoColor
                            ),
                            border = BorderStroke(1.dp, theme.ecoColor.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MOCK BUMP (+0.0.1)", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Home Assistant Settings overrides
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "HOME ASSISTANT CONNECTIVITY",
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Modify or override default background server address",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    val context = LocalContext.current
                    val currentHaUrl by viewModel.haUrl.collectAsState()
                    val defaultHaUrl = remember { viewModel.getDefaultHaUrl() }
                    var urlText by remember(currentHaUrl) { mutableStateOf(currentHaUrl) }

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Server Base URL", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF2196F3),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ha_override_url_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF2196F3).copy(alpha = 0.8f))
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (urlText.isNotBlank()) {
                                    viewModel.updateHaUrl(urlText)
                                    Toast.makeText(context, "Server URL updated & reconnected!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                                contentColor = Color(0xFF2196F3)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SAVE & RECONNECT", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }

                        val isDefault = currentHaUrl.trim().removeSuffix("/") == defaultHaUrl.trim().removeSuffix("/")
                        if (!isDefault) {
                            Button(
                                onClick = {
                                    viewModel.restoreDefaultHaUrl()
                                    Toast.makeText(context, "Restored default Home Assistant URL!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f),
                                    contentColor = Color(0xFFEF5350)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("RESTORE DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                } // Close if (activeSubTab == 1)

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Metadata block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HOME CONTROL CORE VERSION",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${viewModel.activeVersion.collectAsStateWithLifecycle().value} build-prod",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("DONE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CondensedPowerGroup(
    title: String,
    items: List<Any>,
    theme: HvacThemeColors,
    onLightClick: (String) -> Unit,
    onSwitchClick: (String) -> Unit,
    viewModel: HvacViewModel
) {
    if (items.isEmpty()) return
    Text(
        title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = Color.White.copy(alpha = 0.5f),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        val nonCovers = items.filter { it !is CoverControl }
        val covers = items.filterIsInstance<CoverControl>()

        val chunks = nonCovers.chunked(3)
        chunks.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (item) {
                            is LightControl -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("light_card_${item.entityId}").clickable { onLightClick(item.entityId) },
                                    colors = CardDefaults.cardColors(containerColor = if (item.isOn) hvacActiveCardBgColor(theme.heatColor) else hvacCardBgColor()),
                                    border = BorderStroke(1.dp, if (item.isOn) hvacActiveBorderAlphaColor(theme.heatColor) else hvacBorderAlphaColor()),
                                    shape = hvacCardShape(10)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = if (item.isOn) theme.heatColor else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(item.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (item.isOn) "ACTIVE" else "POWER OFF", fontSize = 7.sp, fontWeight = FontWeight.Black, color = if (item.isOn) theme.heatColor else Color.White.copy(alpha = 0.4f))
                                        }
                                    }
                                }
                            }
                            is SwitchControl -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("switch_card_${item.entityId}").clickable { onSwitchClick(item.entityId) },
                                    colors = CardDefaults.cardColors(containerColor = if (item.isOn) Color(0xFF10B981).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)),
                                    border = BorderStroke(1.dp, if (item.isOn) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)),
                                    shape = hvacCardShape(10)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = if (item.isOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(item.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (item.isOn) "ACTIVE" else "POWER OFF", fontSize = 7.sp, fontWeight = FontWeight.Black, color = if (item.isOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        
        val coverChunks = covers.chunked(3)
        coverChunks.forEach { rowCovers ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCovers.forEach { cover ->
                    val isOpen = cover.state.lowercase() == "open" || cover.state.lowercase() == "opening" || cover.state.lowercase() == "on"
                    Box(modifier = Modifier.weight(1f)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                            border = BorderStroke(1.dp, if (isOpen) hvacActiveBorderAlphaColor(Color(0xFFEF4444)) else hvacBorderAlphaColor()),
                            shape = hvacCardShape(10),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Garage, contentDescription = null, tint = if (isOpen) Color(0xFFEF4444) else Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(cover.name, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(cover.state.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Black, color = if (isOpen) Color(0xFFEF4444) else Color(0xFF10B981), letterSpacing = 0.5.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { viewModel.controlCover(cover.entityId, "open", cover.name) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) { Text("OPEN", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                                    Button(onClick = { viewModel.controlCover(cover.entityId, "close", cover.name) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) { Text("CLOSE", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                                }
                            }
                        }
                    }
                }
                repeat(3 - rowCovers.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
