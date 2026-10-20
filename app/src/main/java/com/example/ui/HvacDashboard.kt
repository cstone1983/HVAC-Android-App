package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
        "shield", "alarm", "security" -> Icons.Default.Shield
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
    val context = LocalContext.current

    val sharedPrefsSetup = remember { context.getSharedPreferences("hvac_settings", android.content.Context.MODE_PRIVATE) }
    var showFirstTimeDialog by remember {
        mutableStateOf(!sharedPrefsSetup.getBoolean("first_time_setup_completed_v3", false))
    }

    if (showFirstTimeDialog) {
        FirstTimeSetupDialog(
            onDismiss = {},
            onComplete = {
                sharedPrefsSetup.edit().putBoolean("first_time_setup_completed_v3", true).apply()
                showFirstTimeDialog = false
            }
        )
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

    val glowColorFactor = themeConfig.glowAlpha ?: 0.12f

    val scheduleState = when (val state = uiState) {
        is HvacUiState.Success -> state.globalSettings.houseSchedule
        else -> "Day"
    }

    // All of this is remembered on the few things it actually depends on — the theme config and
    // the schedule slot — rather than recomputed on every recomposition.
    //
    // It is not just the cost of re-parsing ten hex strings. LocalHvacTheme is a
    // staticCompositionLocalOf, which does not track individual reads: when the value it is given
    // changes, Compose recomposes the WHOLE content lambda below the provider. Building a fresh
    // HvacThemeColors each pass handed it a new instance every time, so any recomposition of this
    // composable — and it reads the entity map, which changes constantly — tore down and rebuilt
    // the entire dashboard rather than the one card whose number had moved. That is the largest
    // single cause of the panel feeling slow.
    val bgStartColor = remember(themeConfig.bgStartColorHex, scheduleState) {
        when (scheduleState) {
            "Day" -> parseHexColor(themeConfig.bgStartColorHex, Color(0xFF0F172A))
            "Night" -> parseHexColor(themeConfig.bgStartColorHex, Color(0xFF020617))
            else -> parseHexColor(themeConfig.bgStartColorHex, Color(0xFF050505))
        }
    }
    val bgEndColor = remember(themeConfig.bgEndColorHex, scheduleState) {
        when (scheduleState) {
            "Day" -> parseHexColor(themeConfig.bgEndColorHex, Color(0xFF1E293B))
            "Night" -> parseHexColor(themeConfig.bgEndColorHex, Color(0xFF0F172A))
            else -> parseHexColor(themeConfig.bgEndColorHex, Color(0xFF111827))
        }
    }
    val glowColor = remember(
        themeConfig.glowColorHex, themeConfig.accentColorHex, glowColorFactor, scheduleState
    ) {
        when (scheduleState) {
            "Day" -> parseHexColor(themeConfig.glowColorHex, Color(0xFF2196F3))
                .copy(alpha = glowColorFactor)
            "Night" -> parseHexColor(themeConfig.accentColorHex, Color(0xFFF59E0B))
                .copy(alpha = 0.08f)
            else -> Color(0xFF10B981).copy(alpha = 0.1f)
        }
    }
    val bgGradient = remember(bgStartColor, bgEndColor) {
        Brush.verticalGradient(listOf(bgStartColor, bgEndColor))
    }

    val hvacThemeColors = remember(
        themeConfig, bgStartColor, bgEndColor, glowColor, glowColorFactor,
        cardCornerStyle, cardOpacity
    ) {
        HvacThemeColors(
            // Falls back to the accent only when no explicit heat colour is configured, so the
            // built-in presets keep their identity while a config that sets heatColorHex gets a
            // heat colour independent of its accent.
            heatColor = parseHexColor(
                themeConfig.heatColorHex ?: themeConfig.accentColorHex,
                Color(0xFFF59E0B)
            ),
            coolColor = parseHexColor(themeConfig.coolColorHex, Color(0xFF2196F3)),
            dryColor = parseHexColor(themeConfig.dryColorHex, Color(0xFF8B5CF6)),
            offColor = parseHexColor(themeConfig.offColorHex, Color(0xFF64748B)),
            bgStart = bgStartColor,
            bgEnd = bgEndColor,
            glowColor = glowColor,
            glowAlpha = glowColorFactor,
            cardCornerStyle = cardCornerStyle,
            cardOpacity = cardOpacity,
            chartShadingAlpha = themeConfig.chartShadingAlpha ?: 0.05f,
            showChartShading = themeConfig.showChartShading ?: true
        )
    }

    androidx.compose.runtime.SideEffect { com.example.ui.theme.HvacCorners.style = cardCornerStyle }

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
                    .drawBehind { drawHvacBackground(backgroundDesign, hvacThemeColors, glowColor, activeThemePreset) }
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
                                Text(
                                    layoutConfig.appTitle ?: "Home Control",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.5.sp,
                                    fontSize = 18.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                // No outdoor reading here any more: the home screen's own top line
                                // ("41° outside · sunny") already shows it, and this copy squeezed
                                // the app name off the phone's title bar (2026-10-18).

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
                                        .clip(com.example.ui.theme.themedShape(18.dp))
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
                                shape = com.example.ui.theme.themedShape(16.dp)
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
                        // Sits at the root, outside the tab content, so an entry delay or a
                        // trigger puts the keypad in front of whatever is on screen rather than
                        // requiring someone to find the alarm tab while the clock runs down.
                        AlarmKeypadDialog(viewModel = viewModel)

                        val showModeConfirmDialog by viewModel.showModeConfirmDialog.collectAsStateWithLifecycle()
                        val pendingHvacMode by viewModel.pendingHvacMode.collectAsStateWithLifecycle()

                        if (showModeConfirmDialog && pendingHvacMode != null) {
                            Dialog(onDismissRequest = { viewModel.cancelGlobalHvacModeChange() }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .testTag("mode_change_confirm_dialog"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1E293B)
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    shape = com.example.ui.theme.themedShape(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Warning",
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = "Confirm Mode Change",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                        val currentLabel = state.globalSettings.lastNonOffHvacMode.lowercase().replaceFirstChar { it.uppercase() }
                                        val targetLabel = pendingHvacMode!!.lowercase().replaceFirstChar { it.uppercase() }
                                        Text(
                                            text = "Are you sure you want to change the system mode from $currentLabel to $targetLabel?",
                                            fontSize = 14.sp,
                                            color = Color.White.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.cancelGlobalHvacModeChange()
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("mode_change_cancel_button")
                                            ) {
                                                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.confirmGlobalHvacModeChange()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = when (pendingHvacMode!!.lowercase()) {
                                                        "cool" -> hvacThemeColors.coolColor
                                                        "dry" -> hvacThemeColors.dryColor
                                                        else -> hvacThemeColors.heatColor
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("mode_change_confirm_button")
                                            ) {
                                                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
    listState: LazyListState,
    lastInteractionTime: Long = 0L,
    onInteraction: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = LocalHvacTheme.current
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    var activeZoneDetail by remember { mutableStateOf<ClimateZone?>(null) }

    // A zone tapped in the notification. Waits for that zone to exist, then opens it once.
    val zoneFromNotification by LaunchRequests.openZone.collectAsStateWithLifecycle()
    LaunchedEffect(zoneFromNotification, state.zones) {
        val key = zoneFromNotification ?: return@LaunchedEffect
        if (tab.id != "zones") return@LaunchedEffect
        val zone = state.zones.find { it.key == key } ?: return@LaunchedEffect
        activeZoneDetail = zone
        LaunchRequests.openZone.value = null
    }
    var activeLightPopupId by remember { mutableStateOf<String?>(null) }
    var activeSwitchPopupId by remember { mutableStateOf<String?>(null) }

    val popupTimeoutMillis = (layoutConfig.popupTimeoutSeconds ?: 20).coerceAtLeast(5) * 1000L
    LaunchedEffect(lastInteractionTime) {
        if (lastInteractionTime > 0) {
            kotlinx.coroutines.delay(popupTimeoutMillis)
            activeZoneDetail = null
            activeLightPopupId = null
            activeSwitchPopupId = null
        }
    }
    
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
            viewModel = viewModel,
            onInteraction = onInteraction
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Measured size of the content area, not the screen: on the wall tablets the rail and
    // the house panel take ~270dp before any card is drawn.
    val maxContentWidth = this@BoxWithConstraints.maxWidth
    val maxContentHeight = this@BoxWithConstraints.maxHeight
    val screen = LocalConfiguration.current
    val deviceMode by viewModel.deviceMode.collectAsStateWithLifecycle()
    val commandLayout = if (tab.id == "zones") commandCenterLayout(
        enabled = layoutConfig.homeStatus?.commandCenter,
        phoneEnabled = layoutConfig.homeStatus?.phoneCommandCenter,
        isLandscape = isLandscape,
        phone = deviceMode == com.example.model.DeviceMode.PHONE,
        widthDp = screen.screenWidthDp,
        heightDp = screen.screenHeightDp
    ) else null
    val tightZones = tab.id == "zones" && (isLandscape || commandLayout != null)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("tab_content_${tab.id}"),
        contentPadding = PaddingValues(bottom = if (tightZones) 10.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (tightZones) 8.dp else 16.dp)
    ) {
        tab.sections.forEach { section ->
            when (section.lowercase().trim()) {
                "sensors" -> {
                    // Redundant copy removed since it's already displayed in the top header.
                }
                "zones" -> {
                    if (commandLayout != null) {
                        // The command center. One item, sized to the measured content area, so
                        // the whole screen fits without scrolling (the cover screen scrolls).
                        item {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = if (isLandscape && commandLayout == CommandLayout.WIDE) 4.dp else 12.dp
                                )
                            ) {
                                // Alerts live inside the command center now, beside the Other
                                // rooms, so a confirmation no longer shoves the screen down.
                                CommandCenterHome(
                                    state = state,
                                    viewModel = viewModel,
                                    layout = commandLayout,
                                    height = maxContentHeight - 12.dp,
                                    onZoneClick = { activeZoneDetail = it }
                                )
                            }
                        }
                        return@forEach
                    }
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            GlobalSettingsQuickControl(state = state, viewModel = viewModel)
                        }
                    }

                    // Normally absent. Appears for an open door, a lost connection, or a
                    // command that failed or is still being applied.
                    item {
                        HomeAlertStrip(
                            viewModel = viewModel,
                            homeStatus = layoutConfig.homeStatus,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // A fixed two-column grid left ~500dp-wide cards on the wall tablets.
                    // Pick the column count from the space actually available instead.
                    val zoneColumns = when {
                        maxContentWidth >= 1000.dp -> 3
                        maxContentWidth >= 660.dp && isLandscape -> 3
                        else -> 2
                    }
                    val chunkedZones = state.zones.chunked(zoneColumns)

                    // Size the cards to the space that is actually left so every zone fits
                    // without scrolling. The control card's height is known from the same
                    // width breakpoints it uses itself.
                    //
                    // These estimates are deliberately a little generous. Measured on the Tab A9
                    // at 1007x601dp the control card renders ~232dp against the 218dp assumed
                    // here, and the shortfall all landed on the last row, which was clipped. An
                    // over-estimate only costs a few dp of slack; an under-estimate hides a zone.
                    val controlWidth = maxContentWidth - 32.dp
                    val controlHeight = when {
                        controlWidth >= 980.dp -> 132.dp
                        controlWidth >= 560.dp -> 232.dp
                        else -> 344.dp
                    }
                    val zoneRowCount = chunkedZones.size.coerceAtLeast(1)
                    // One gap above each zone row: the first separates it from the control card.
                    // The old count assumed a trailing gap that the list does not draw, while
                    // ignoring the bottom content padding that it does.
                    val rowSpacing = if (isLandscape && tab.id == "zones") 8.dp else 16.dp
                    val bottomPadding = if (isLandscape && tab.id == "zones") 10.dp else 24.dp
                    // The alert strip above is its OWN list item, always emitted. So the section
                    // lays out `2 + zoneRowCount` items and therefore draws `zoneRowCount + 1`
                    // gaps, not `zoneRowCount` — and when the strip is visible it is ~42dp tall
                    // on top of that. The budget accounted for neither, which left about 6dp of
                    // real slack on the Tab A9: enough while the strip was hidden, and not enough
                    // the moment a door opened or a command failed, which clipped the bottom row's
                    // "SET nn° / OFF" line exactly when the panel had something to say.
                    //
                    // The allowance is unconditional rather than tracking the strip's visibility.
                    // Reserving it costs a little slack while no alert is up; not reserving it
                    // hides information whenever one is. The Tab A8 has ~72dp spare and is
                    // unaffected either way.
                    val alertStripReserve = 42.dp
                    val totalGaps = rowSpacing * (zoneRowCount + 1)
                    val zoneCardHeight =
                        ((maxContentHeight - controlHeight - alertStripReserve - totalGaps - bottomPadding) / zoneRowCount)
                            .coerceIn(92.dp, 190.dp)
                    items(chunkedZones, key = { pair -> pair.joinToString("-") { it.key } }) { pair ->
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
                                        viewModel = viewModel,
                                        cardHeight = zoneCardHeight
                                    )
                                }
                            }
                            repeat(zoneColumns - pair.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                "condensed_power" -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = if (isLandscape) 4.dp else 16.dp)) {
                            PowerPage(
                                state = state,
                                viewModel = viewModel,
                                onLightDetails = { activeLightPopupId = it },
                                onSwitchDetails = { activeSwitchPopupId = it }
                            )
                        }
                    }
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
                                items(interiorLights, key = { it.entityId }) { light ->
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
                                items(exteriorItems, key = { it.entityId }) { light ->
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

                        items(state.covers, key = { it.entityId }) { cover ->
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
                                                shape = com.example.ui.theme.themedShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Button(
                                                onClick = { viewModel.controlCover(cover.entityId, "close", cover.name) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                                shape = com.example.ui.theme.themedShape(8.dp),
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
                    if (section.lowercase().trim() == "alarm") {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                AlarmSection(viewModel = viewModel)
                            }
                        }
                    } else if (section.lowercase().trim() == "pool") {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                PoolDashboardView(viewModel = viewModel)
                            }
                        }
                    } else if (section.lowercase().trim() == "solar") {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SolarDashboardView(viewModel = viewModel, theme = theme)
                            }
                        }
                    } else {
                        val dynamicSection = layoutConfig.dynamicSections?.find { it.id.lowercase().trim() == section.lowercase().trim() }
                        if (dynamicSection != null) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    DynamicSectionRenderer(sectionConfig = dynamicSection, theme = theme, viewModel = viewModel)
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

@Composable
fun HvacDashboardContent(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    onShowSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val theme = LocalHvacTheme.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Phones keep the bottom tab bar whichever way they are held; the side rail is for the wall
    // panels. The Fold's inner screen is nearly square and flips between the two otherwise.
    val deviceMode by viewModel.deviceMode.collectAsStateWithLifecycle()
    val isPhone = deviceMode == com.example.model.DeviceMode.PHONE
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val activeThemePreset by viewModel.selectedThemePreset.collectAsStateWithLifecycle()
    // A tab marked hidden keeps its code in the app but gets no button in the navigation, so a
    // finished feature can ship dark and be switched on later with a config push alone.
    val rawTabs = (layoutConfig.tabs ?: emptyList()).filter { !it.hidden }

    val activeTabs = if (rawTabs.isNotEmpty()) rawTabs else listOf(
        TabConfig("zones", "ZONES & UNITS", "layers", listOf("sensors", "zones")),
        TabConfig("aux", "AUXILIARY POWER", "lightbulb", listOf("lights", "switches", "covers")),
        TabConfig("updates", "UPDATES", "cloud_download", listOf("updates"))
    )

    // Keep track of scroll states for each tab to reset back to top on inactivity
    val listStates = remember { List(10) { LazyListState() } }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Revert to Main Tab and Top of Page after a period of inactivity. Dialogs live in their
    // own windows, so their touches never reached the root pointerInput below — which meant
    // this fired mid-adjustment and disposed the open popup. They now report interaction
    // through onInteraction, and the window is long enough to read a chart on the wall.
    val idleReturnMillis = (layoutConfig.idleReturnSeconds ?: 20).coerceAtLeast(5) * 1000L
    val popupTimeoutMillis = (layoutConfig.popupTimeoutSeconds ?: 20).coerceAtLeast(5) * 1000L

    // The alarm keypad is a modal in its own window, so it never reaches the root pointerInput
    // that feeds lastInteractionTime. Without this the idle timer kept running underneath it and
    // silently switched the page back to Home behind the dialog — so whatever you tapped next was
    // aimed at a screen that had already changed. That is not merely cosmetic on a keypad: the
    // buttons underneath include the whole-house mode row.
    val alarmKeypadVisible by viewModel.showAlarmKeypad.collectAsStateWithLifecycle()

    // Opened from the notification. A zone tap goes to the zones tab (the tab itself opens the
    // popup once its zones are loaded); a Heat/Cool tap that crosses heating and cooling asks
    // the same confirmation the panel's own buttons ask.
    val zoneFromNotification by LaunchRequests.openZone.collectAsStateWithLifecycle()
    LaunchedEffect(zoneFromNotification) {
        if (zoneFromNotification == null) return@LaunchedEffect
        val zonesTab = activeTabs.indexOfFirst { it.id == "zones" }
        if (zonesTab >= 0) selectedTab = zonesTab
        lastInteractionTime = System.currentTimeMillis()
    }
    val modeFromNotification by LaunchRequests.confirmMode.collectAsStateWithLifecycle()
    LaunchedEffect(modeFromNotification) {
        val mode = modeFromNotification ?: return@LaunchedEffect
        viewModel.requestGlobalHvacMode(mode)
        LaunchRequests.confirmMode.value = null
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(lastInteractionTime, alarmKeypadVisible, deviceMode) {
        if (alarmKeypadVisible) return@LaunchedEffect
        // Wall panels only; a phone stays on whatever page it was left on.
        if (!deviceMode.returnsHomeWhenIdle) return@LaunchedEffect
        kotlinx.coroutines.delay(idleReturnMillis)
        selectedTab = 0
        if (listStates.isNotEmpty()) {
            try {
                listStates[0].scrollToItem(0)
            } catch (e: Exception) {
                // Safeguard against scroll interruptions
            }
        }
    }

    // Raising the keypad also brings the alarm tab up behind it, so when the keypad closes — by
    // hand, or on its own one-minute timeout — you are left looking at the alarm screen rather
    // than at whichever tab happened to be open when it fired. The normal idle countdown then
    // restarts from that moment and returns to Home as usual.
    val alarmTabIndex = activeTabs.indexOfFirst { it.id.equals("alarm", ignoreCase = true) }
    LaunchedEffect(alarmKeypadVisible) {
        if (alarmKeypadVisible) {
            if (alarmTabIndex >= 0) selectedTab = alarmTabIndex
        } else {
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // ...but once the code has actually been accepted, the alarm tab is the wrong place to
    // be left standing. You walked in, the panel was counting down, you typed the code: what
    // you want next is the house — temperatures, what is open, whether anything needs doing
    // — not the screen that just finished its job. The idle timer would get there eventually,
    // twenty seconds later, which is twenty seconds of staring at a solved problem.
    //
    // Deliberately scoped to the keypad having just been used, rather than to the house
    // becoming disarmed. Those are different events: if the alarm tab is open because
    // somebody is reading it, and the house is disarmed from a different panel or from Home
    // Assistant, yanking the page out from under them would be wrong. This only fires for
    // the person who just typed the code here.
    val alarmState by viewModel.alarmState.collectAsStateWithLifecycle()
    var keypadWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(alarmKeypadVisible, alarmState.phase) {
        if (alarmKeypadVisible) {
            keypadWasOpen = true
        } else if (keypadWasOpen) {
            keypadWasOpen = false
            // Arming through the keypad leaves the phase ARMING, not DISARMED, so this does
            // not fire on the way out of the door — only on the way back in.
            if (alarmState.phase == com.example.model.AlarmPhase.DISARMED) {
                selectedTab = 0
                lastInteractionTime = System.currentTimeMillis()
                if (listStates.isNotEmpty()) {
                    try {
                        listStates[0].scrollToItem(0)
                    } catch (e: Exception) {
                        // Safeguard against scroll interruptions
                    }
                }
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
        if (isLandscape && !isPhone) {
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
                    .background(Color.White.copy(alpha = 0.02f), com.example.ui.theme.themedShape(16.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), com.example.ui.theme.themedShape(16.dp))
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
                                Text(
                                    layoutConfig.appTitle ?: "Home Control",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                            val activeColor = theme.houseAccentColor(state.globalSettings.globalHvacMode)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("nav_tab_$index")
                                    .clip(com.example.ui.theme.themedShape(10.dp))
                                    .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) activeColor.copy(alpha = 0.4f) else Color.Transparent
                                        ),
                                        com.example.ui.theme.themedShape(10.dp)
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
                                .background(Color.White.copy(alpha = 0.05f), com.example.ui.theme.themedShape(12.dp))
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
                                        .background(connectionColor, com.example.ui.theme.themedShape(4.dp))
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

                        // Outdoor reading + settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutdoorReadingChip(
                                viewModel = viewModel,
                                entityId = layoutConfig.homeStatus?.outdoorTempEntityId,
                                compact = false
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


                            }
                        }
                    } else {
                        // Collapsed Bottom Controls
                        IconButton(
                            onClick = { scope.launch { viewModel.fetchStates() } },
                            modifier = Modifier
                                .size(36.dp)
                                .background(connectionColor.copy(alpha = 0.15f), com.example.ui.theme.themedShape(18.dp))
                                .border(BorderStroke(1.dp, connectionColor.copy(alpha = 0.4f)), com.example.ui.theme.themedShape(18.dp))
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
                                    listState = listStates[currentTabIdx.coerceIn(0, listStates.lastIndex)],
                                    lastInteractionTime = lastInteractionTime,
                                    onInteraction = { lastInteractionTime = System.currentTimeMillis() }
                                )
                            }
                        }
                    }
                }

                // The command center carries its own alarm, energy and people column, so the
                // house panel stands aside on the home tab when it is showing.
                val commandCenterShowing = activeTabs.getOrNull(selectedTab)?.id == "zones" &&
                    useCommandCenter(
                        layoutConfig.homeStatus?.commandCenter,
                        isLandscape,
                        LocalConfiguration.current.screenWidthDp,
                        LocalConfiguration.current.screenHeightDp
                    )
                val housePanelHidden = commandCenterShowing ||
                    activeTabs.getOrNull(selectedTab)?.id in (layoutConfig.homeStatus?.housePanelHiddenTabs ?: listOf("aux"))
                if (!housePanelHidden) {
                    Spacer(modifier = Modifier.width(16.dp))

                    // Right-hand house panel. This used to be a "TEMPS" column that repeated
                    // rooms which already have their own zone card — Living read 65° here while
                    // Main Level read 67° two inches away. It now carries only what the zone
                    // cards don't: humidity, presence, the non-zone rooms, and quick actions.
                    val homeStatusCfg = layoutConfig.homeStatus
                    val hiddenRoomIds = homeStatusCfg?.hideRoomSensorIds.orEmpty().toSet()
                    val secondaryRooms = state.roomSensors.filter { it.id !in hiddenRoomIds }

                    Column(
                        modifier = Modifier
                            .width(168.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (homeStatusCfg?.showAlarmCard != false) {
                            HomeAlarmCard(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (homeStatusCfg?.showHumidityCard == true) {
                            HumidityCard(
                                viewModel = viewModel,
                                sensors = homeStatusCfg?.humiditySensors.orEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (secondaryRooms.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                                border = BorderStroke(1.dp, hvacBorderAlphaColor()),
                                shape = hvacCardShape(12),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = "OTHER ROOMS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.55f),
                                        letterSpacing = 1.3.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    secondaryRooms.forEach { room ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = room.name.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.55f),
                                                letterSpacing = 0.6.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = room.temp?.let { "${it.toInt()}°" } ?: "--°",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PresenceCard(
                            viewModel = viewModel,
                            presenceEntityIds = homeStatusCfg?.presenceEntityIds.orEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            historyWindowDays = homeStatusCfg?.presenceHistoryDays ?: 30,
                            lastInteractionTime = lastInteractionTime,
                            popupTimeoutMillis = popupTimeoutMillis,
                            onInteraction = { lastInteractionTime = System.currentTimeMillis() }
                        )

                        QuickActionsCard(
                            viewModel = viewModel,
                            actions = homeStatusCfg?.quickActions.orEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    } else {
        // Portrait / phone layout
        Column(modifier = Modifier.fillMaxSize()) {
            val homeStatusCfg = layoutConfig.homeStatus
            val hiddenRoomIds = homeStatusCfg?.hideRoomSensorIds.orEmpty().toSet()

            // The alarm tab swaps this whole header out. Humidity, presence, room temperatures
            // and the weather forecast say nothing about the alarm, and together they pushed the
            // status and keypad below the fold on the phone — so the tab's own summary takes
            // their place: whether it is armed, and whether anything is open or detecting.
            // The header belongs to the main dashboard, and only to it.
            //
            // The status row, the room temperature strip and the weather card describe the house
            // as a whole. On solar and pool they say nothing about what the page is for, and they
            // were taking roughly a third of the height the graph could have had — on a panel
            // read from across the room that is the difference between following a line and
            // squinting at it.
            //
            // The alarm tab stays a separate case: it does not merely drop the header, it swaps
            // in its own summary, because whether it is armed and whether anything is open is
            // exactly what someone opening that tab came to find out.
            val headerTabId = activeTabs.getOrNull(selectedTab)?.id?.lowercase()
            val headerTabs = homeStatusCfg?.headerTabIds ?: listOf("zones")
            // The command center brings its own alarm, people and weather, so the header would
            // only repeat them.
            val commandCenterHere = headerTabId == "zones" && commandCenterLayout(
                enabled = homeStatusCfg?.commandCenter,
                phoneEnabled = homeStatusCfg?.phoneCommandCenter,
                isLandscape = isLandscape,
                phone = isPhone,
                widthDp = configuration.screenWidthDp,
                heightDp = configuration.screenHeightDp
            ) != null
            val showHeader = headerTabId != null && !commandCenterHere &&
                headerTabs.any { it.equals(headerTabId, ignoreCase = true) }

            if (headerTabId == "alarm") {
                AlarmSummaryStrip(
                    viewModel = viewModel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (showHeader) {
            // Status row: the two things the phone had no way to show at all. Rooms that
            // already appear as zone cards are excluded from the strip below so the same
            // room can't report two different temperatures on one screen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (homeStatusCfg?.showAlarmCard != false) {
                    HomeAlarmCard(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (homeStatusCfg?.showHumidityCard == true) {
                    HumidityCard(
                        viewModel = viewModel,
                        sensors = homeStatusCfg?.humiditySensors.orEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }
                PresenceCard(
                    viewModel = viewModel,
                    presenceEntityIds = homeStatusCfg?.presenceEntityIds.orEmpty(),
                    modifier = Modifier.weight(1f),
                    historyWindowDays = homeStatusCfg?.presenceHistoryDays ?: 30,
                    lastInteractionTime = lastInteractionTime,
                    popupTimeoutMillis = popupTimeoutMillis,
                    onInteraction = { lastInteractionTime = System.currentTimeMillis() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Horizontal sensors header strip (non-zone rooms only)
            RoomSensorsStrip(rooms = state.roomSensors.filter { it.id !in hiddenRoomIds })

            Spacer(modifier = Modifier.height(8.dp))

            // Added ThreeDayWeatherCard integrating Open-Meteo & MET Norway
            if (layoutConfig.showWeatherCard != false) {
                val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
                val isMinimized by viewModel.weatherCardMinimized.collectAsStateWithLifecycle()
                ThreeDayWeatherCard(
                    weatherState = weatherState,
                    isMinimized = isMinimized,
                    onToggleMinimize = { viewModel.setWeatherCardMinimized(!isMinimized) },
                    onRefresh = { scope.launch { viewModel.fetchWeather() } },
                    modifier = Modifier.testTag("weather_card")
                )
                Spacer(modifier = Modifier.height(if (isMinimized) 6.dp else 10.dp))
            }
            }

            UpdateAlertBanner(
                viewModel = viewModel,
                activeTabs = activeTabs,
                onNavigateToUpdates = { selectedTab = it }
            )

            // Body content swap based on selected tab containing robust details
            Box(modifier = Modifier.weight(1f)) {
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
                            listState = listStates[currentTabIdx.coerceIn(0, listStates.lastIndex)],
                            lastInteractionTime = lastInteractionTime,
                            onInteraction = { lastInteractionTime = System.currentTimeMillis() }
                        )
                    }
                }
            }

            // Bottom navigation. The old top row gave five cards equal weight with
            // maxLines = 1, so titles like "SYSTEM DISPATCH" ran off the right edge of the
            // screen. Short titles come from the layout config, and the bar now sits where
            // a thumb actually reaches.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.025f))
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeTabs.forEachIndexed { index, tabConfig ->
                    val isSelected = selectedTab == index
                    val activeColor = theme.houseAccentColor(state.globalSettings.globalHvacMode)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(com.example.ui.theme.themedShape(10.dp))
                            .clickable { selectedTab = index }
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .testTag("nav_tab_$index"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = getIconByName(tabConfig.icon),
                            contentDescription = tabConfig.title,
                            tint = if (isSelected) activeColor else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tabConfig.shortTitle
                                ?: tabConfig.title.substringBefore(" &").substringBefore(" ").uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center,
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
            shape = com.example.ui.theme.themedShape(12.dp),
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
                        text = "LAYOUT UPDATE AVAILABLE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.coolColor,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "A new layout is on GitHub (${availableUpdate.version}). Tap to see it on the Updates tab.",
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
    // One set of sizes for every screen. Landscape used to shrink these by ~25%, which meant
    // the wall tablets — the devices read from furthest away — got the smallest controls in
    // the app, including a whole-house Off button 5dp from Cool.
    val cardPadding = 14.dp
    val rowSpacerHeight = 10.dp
    val titleFontSize = 10.sp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_quick_control_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(14)
    ) {
        // Stacking all three groups cost ~470dp of vertical space on the wall tablets and
        // pushed half the zone cards below the fold. There is plenty of width going spare, so
        // the groups move side by side as soon as it fits and the whole card collapses to
        // roughly a third of its height.
        BoxWithConstraints(modifier = Modifier.padding(cardPadding)) {
            val availableWidth = this@BoxWithConstraints.maxWidth
            val threeAcross = availableWidth >= 980.dp
            val twoAcross = availableWidth >= 560.dp

            val divider: @Composable () -> Unit = {
                Spacer(modifier = Modifier.height(rowSpacerHeight))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(rowSpacerHeight))
            }

            when {
                threeAcross -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    ScheduleGroup(state, viewModel, titleFontSize, Modifier.weight(1f))
                    HouseModeGroup(state, viewModel, titleFontSize, Modifier.weight(1.4f))
                    HotWaterGroup(state, viewModel, titleFontSize, Modifier.weight(1.4f))
                }

                twoAcross -> Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        ScheduleGroup(state, viewModel, titleFontSize, Modifier.weight(1f))
                        HouseModeGroup(state, viewModel, titleFontSize, Modifier.weight(1.4f))
                    }
                    divider()
                    HotWaterGroup(state, viewModel, titleFontSize, Modifier.fillMaxWidth())
                }

                else -> Column(modifier = Modifier.fillMaxWidth()) {
                    ScheduleGroup(state, viewModel, titleFontSize, Modifier.fillMaxWidth())
                    divider()
                    HouseModeGroup(state, viewModel, titleFontSize, Modifier.fillMaxWidth())
                    divider()
                    HotWaterGroup(state, viewModel, titleFontSize, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun ScheduleGroup(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "HOUSE SCHEDULE",
            fontSize = titleFontSize,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                Triple("Day", Icons.Default.WbSunny, Color(0xFFF59E0B)),
                Triple("Night", Icons.Default.NightsStay, Color(0xFF2196F3)),
                Triple("Away", Icons.Default.ExitToApp, Color(0xFF10B981))
            ).forEach { (label, icon, color) ->
                val isSelected = state.globalSettings.houseSchedule.lowercase() == label.lowercase()
                SegmentedControlButton(
                    label = label,
                    icon = icon,
                    color = color,
                    isSelected = isSelected,
                    contentDescription = "Set schedule to $label",
                    testTagId = "main_schedule_btn_${label.lowercase()}",
                    onClick = { viewModel.selectHouseSchedule(label) }
                )
            }
        }
    }
}

@Composable
internal fun HouseModeGroup(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val theme = LocalHvacTheme.current
    Column(modifier = modifier) {
        Text(
            "HOUSE MODE",
            fontSize = titleFontSize,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                Triple("heat", Icons.Default.Whatshot, theme.heatColor),
                Triple("cool", Icons.Default.AcUnit, theme.coolColor),
                Triple("dry", Icons.Default.Air, theme.dryColor)
            ).forEach { (label, icon, color) ->
                val isSelected = state.globalSettings.globalHvacMode.lowercase() == label.lowercase()
                SegmentedControlButton(
                    label = label,
                    icon = icon,
                    color = color,
                    isSelected = isSelected,
                    contentDescription = "Set global mode to $label",
                    testTagId = "main_hvac_mode_btn_${label.lowercase()}",
                    onClick = { viewModel.requestGlobalHvacMode(label) }
                )
            }
            // Whole-house Off is set apart from the running modes so it is never the
            // button you hit while aiming for Cool.
            Spacer(modifier = Modifier.width(9.dp))
            var confirmOff by remember { mutableStateOf(false) }
            val alreadyOff = state.globalSettings.globalHvacMode.lowercase() == "off"
            SegmentedControlButton(
                label = "off",
                icon = Icons.Default.PowerSettingsNew,
                color = Color(0xFFEF4444),
                isSelected = alreadyOff,
                contentDescription = "Turn the whole house off",
                testTagId = "main_hvac_mode_btn_off",
                weight = 0.85f,
                onClick = { if (!alreadyOff) confirmOff = true }
            )
            if (confirmOff) {
                ConfirmDialog(
                    title = "Turn the whole house off?",
                    message = "Every heat pump stops until Heat, Cool or Dry is chosen again.",
                    confirmLabel = "Turn off",
                    onConfirm = { viewModel.requestGlobalHvacMode("off") },
                    onDismiss = { confirmOff = false }
                )
            }
        }
    }
}

@Composable
internal fun HotWaterGroup(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    // Shortened along with the other footer buttons when the command center asks.
    val compactWater = LocalCompactControls.current
    val waterBtnPadding = PaddingValues(horizontal = 4.dp, vertical = if (compactWater) 4.dp else 7.dp)
    val waterIconSize = if (compactWater) 16.dp else 20.dp
    val waterSpacerHeight = if (compactWater) 3.dp else 4.dp
    val waterLabelFontSize = 8.5.sp

    Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "HOT WATER",
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val wsStatesHw by viewModel.entityStates.collectAsStateWithLifecycle()
                    val hwCfg = viewModel.getActiveLayoutConfig()
                    // Heating or not, always shown — the tank can read 100% and still be heating to
                    // its target — and UNKNOWN when the sensor is not reporting either way.
                    val heating = hwCfg.waterHeaterRunningEntityId
                        ?.let { wsStatesHw[it]?.state?.lowercase() }
                        ?.let { if (it == "on") true else if (it == "off") false else null }
                    if (hwCfg.waterHeaterRunningEntityId != null) {
                        val statusColor = if (heating == true) Color(0xFF10B981) else Color.White.copy(alpha = 0.35f)
                        Spacer(modifier = Modifier.width(9.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            when (heating) {
                                true -> hwCfg.waterHeaterHeatingText ?: "HEATING"
                                false -> hwCfg.waterHeaterIdleText ?: "NOT HEATING"
                                null -> hwCfg.waterHeaterUnknownText ?: "UNKNOWN"
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = statusColor,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val hwConfig = viewModel.getActiveLayoutConfig()
                    val hwStates by viewModel.entityStates.collectAsStateWithLifecycle()
                    // What the input_select offers right now. A configured mode it does not offer
                    // (yet) is left out, rather than drawn as a button that would be refused.
                    val offered = (hwStates[hwConfig.waterHeaterEntityId ?: "input_select.water_heater_mode"]
                        ?.attributes?.get("options") as? List<*>)?.map { it.toString().lowercase() }
                    (hwConfig.waterHeaterModes ?: com.example.model.DefaultWaterHeaterModes)
                        .filter { offered == null || it.option.lowercase() in offered }
                        .map { m ->
                            WaterHeaterItem(
                                m.option,
                                m.label,
                                when (m.icon) {
                                    "water_drop" -> Icons.Default.WaterDrop
                                    "heat_pump" -> Icons.Default.HeatPump
                                    "fire" -> Icons.Default.LocalFireDepartment
                                    else -> Icons.Default.Bolt
                                },
                                parseHexColor(m.color, Color(0xFFEF4444))
                            )
                        }
                        .forEach { item ->
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
                            shape = com.example.ui.theme.themedShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(waterBtnPadding),
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
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 4th item: Available Hot Water Tank Fullness Box
                    val fullness = state.globalSettings.waterHeaterFullness
                    val fullnessPct = fullness?.coerceIn(0.0, 100.0)
                    val fullnessFormatted = if (fullness != null) "${fullnessPct?.toInt()}%" else "--%"
                    val fillFraction = ((fullnessPct ?: 0.0) / 100.0).toFloat()
                    val tankColor = Color(0xFF0EA5E9)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(com.example.ui.theme.themedShape(8.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(
                                1.dp,
                                if (fullness != null) tankColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                                com.example.ui.theme.themedShape(8.dp)
                            )
                            .drawWithContent {
                                if (fillFraction > 0.01f) {
                                    val fillHeight = size.height * fillFraction
                                    val topY = size.height - fillHeight
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                tankColor.copy(alpha = 0.28f),
                                                tankColor.copy(alpha = 0.50f)
                                            ),
                                            startY = topY,
                                            endY = size.height
                                        ),
                                        topLeft = Offset(0f, topY),
                                        size = Size(size.width, fillHeight)
                                    )
                                    drawLine(
                                        color = tankColor.copy(alpha = 0.9f),
                                        start = Offset(0f, topY),
                                        end = Offset(size.width, topY),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                                drawContent()
                            }
                            .padding(waterBtnPadding)
                            .testTag("water_heater_tank_fullness_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Waves,
                                contentDescription = "Available hot water fullness: $fullnessFormatted",
                                tint = if (fullness != null) tankColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(waterIconSize)
                            )
                            Spacer(modifier = Modifier.height(waterSpacerHeight))
                            Text(
                                text = fullnessFormatted,
                                fontSize = waterLabelFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (fullness != null) Color.White else Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
    }
}

@Composable
fun ConsolidatedZoneCard(
    zone: ClimateZone,
    onClick: () -> Unit,
    viewModel: HvacViewModel,
    // When the caller has measured the space available, the card fills exactly that and its
    // hero numeral scales with it, so every zone fits on screen without scrolling.
    cardHeight: androidx.compose.ui.unit.Dp? = null
) {
    val heroFontSize = when {
        cardHeight == null -> 38.sp
        else -> (cardHeight.value * 0.245f).coerceIn(24f, 40f).sp
    }
    val theme = LocalHvacTheme.current
    val activeColor = theme.colorForMode(zone.currentHvacMode)
    val modeTint = theme.tintForMode(zone.currentHvacMode)

    // Advanced Compose Animations: Thermal Aura Gradient and Pulsing Vector Indicators
    val infiniteTransition = rememberInfiniteTransition(label = "zone_aura")
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = if (zone.isCalling) 0.22f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_scale"
    )

    // Only animate while the unit is genuinely working. Pulsing on mode alone meant every
    // non-off card glowed permanently, so the glow carried no information.
    val activeHvacIsRunning = zone.isCalling
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )
    // Lambda form reads the animated value in the layer phase, so the pulse doesn't recompose the card every frame.
    val runningModifier = if (activeHvacIsRunning) {
        Modifier.graphicsLayer {
            scaleX = iconPulse
            scaleY = iconPulse
        }
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (cardHeight != null) Modifier.height(cardHeight) else Modifier)
            .clickable { onClick() }
            .testTag("zone_card_${zone.key}")
            .drawBehind {
                if (zone.isCalling) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = auraPulse),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.5f),
                            radius = size.width * 0.55f * auraScale
                        )
                    )
                }
            },
        // The card body now carries the MODE: orange heating, blue cooling, purple drying,
        // plain when off. Previously the body only tinted for override and borrowed the heat
        // colour to do it, so a held, idle zone looked like it was heating while a genuinely
        // heating zone looked like every other card.
        colors = CardDefaults.cardColors(
            containerColor = modeTint?.let { hvacActiveCardBgColor(it) } ?: hvacCardBgColor()
        ),
        // The border carries the EXCEPTION: red for a zone that is not following its schedule,
        // whether that is an override or automations being off. Mode owns the body, so red
        // here never gets confused for heat.
        border = BorderStroke(
            1.dp,
            when {
                zone.overrideOn || !zone.autoOn -> hvacActiveBorderAlphaColor(theme.boostColor)
                modeTint != null -> hvacActiveBorderAlphaColor(modeTint)
                else -> hvacBorderAlphaColor()
            }
        ),
        shape = hvacCardShape(12)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = if (cardHeight != null) Arrangement.SpaceBetween else Arrangement.Top
        ) {
            // Row 1: Icon + Name & Status Badges
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
                        modifier = Modifier.size(17.dp).then(runningModifier)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = zone.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
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
                                theme.coolColor.copy(alpha = 0.14f),
                                com.example.ui.theme.themedShape(5.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = zone.fanMode.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = theme.coolColor,
                            letterSpacing = 0.2.sp
                        )
                    }

                    // Auto/Hold Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (zone.autoOn) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFEF4444).copy(alpha = 0.14f),
                                com.example.ui.theme.themedShape(5.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (zone.autoOn) "AUTO" else "HOLD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = if (zone.autoOn) Color(0xFF10B981) else Color(0xFFEF4444),
                            letterSpacing = 0.2.sp
                        )
                    }

                    // Override (protection) marker — kept visible as its own symbol.
                    if (zone.overrideOn) {
                        Box(
                            modifier = Modifier
                                .background(theme.boostColor.copy(alpha = 0.14f), com.example.ui.theme.themedShape(5.dp))
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Override active",
                                tint = theme.boostColor,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            }

            if (cardHeight == null) Spacer(modifier = Modifier.height(10.dp))

            // Row 2: the room temperature as the dominant value, with what the zone is
            // actually doing and the target beneath it.
            Text(
                text = zone.currentTemp?.let { "${it.toInt()}°" } ?: "--°",
                fontWeight = FontWeight.Light,
                fontSize = heroFontSize,
                letterSpacing = (-1.5).sp,
                lineHeight = heroFontSize * 1.05f,
                color = Color.White,
                maxLines = 1
            )

            if (cardHeight == null) Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // The target stays useful when the zone is off — it says what the room
                    // will aim for once it runs. The status text on the right already says OFF.
                    text = "SET ${zone.targetTemp?.toInt() ?: "--"}°",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (zone.isCalling) activeColor else Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = zone.statusLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = if (zone.isCalling) activeColor else Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.6.sp,
                    maxLines = 1
                )
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
    viewModel: HvacViewModel,
    onInteraction: () -> Unit = {}
) {
    val theme = LocalHvacTheme.current
    val activeColor = if (isOn) theme.heatColor else Color(0xFF64748B)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .dialogInteractionReporter(onInteraction)
                .testTag("lighting_control_popup_$entityId"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = com.example.ui.theme.themedShape(20.dp)
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
                                .background(activeColor.copy(alpha = 0.15f), com.example.ui.theme.themedShape(10.dp)),
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
                        .background(Color.White.copy(alpha = 0.03f), com.example.ui.theme.themedShape(12.dp))
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
                            .background(Color.White.copy(alpha = 0.03f), com.example.ui.theme.themedShape(12.dp))
                            .padding(12.dp)
                    ) {
                        var sliderValue by remember(brightness) {
                            mutableStateOf(brightness?.toFloat() ?: 0f)
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
    viewModel: HvacViewModel,
    onInteraction: () -> Unit = {}
) {
    val theme = LocalHvacTheme.current
    var pendingModeConflict by remember { mutableStateOf<com.example.model.ModeConflict?>(null) }
    pendingModeConflict?.let { conflict ->
        ModeConflictDialog(
            conflict = conflict,
            zoneName = zone.name,
            onDismiss = { pendingModeConflict = null },
            onOverride = {
                viewModel.applyHouseModeOverride(
                    conflict.requestedMode, zone.name, conflict.blockingMode, zone.climateEntityId
                )
                pendingModeConflict = null
                onDismiss()
            },
            onInteraction = onInteraction
        )
    }
    val activeColor = theme.colorForMode(zone.currentHvacMode)

    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            bannerMessage = actionFeedback
            bannerVisible = true
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(bannerVisible, bannerMessage) {
        if (bannerVisible && bannerMessage != null) {
            kotlinx.coroutines.delay(4000L)
            bannerVisible = false
        }
    }

    // Creative and robust Material 3 animations inside popup
    val infiniteTransition = rememberInfiniteTransition(label = "detail_animations")
    // Use the derived "is it actually working" state rather than a mode whitelist, which left
    // out dry and treated a zone sitting at target as though it were running.
    val isRunning = zone.isCalling
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )
    val headerIconModifier = if (isRunning) {
        Modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }
    } else {
        Modifier
    }

    val detailAuraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = if (zone.currentHvacMode.lowercase() != "off") 0.28f else 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detail_aura_alpha"
    )
    val detailAuraScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detail_aura_scale"
    )

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dialogInteractionReporter(onInteraction)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = screenHeight * 0.85f)
                    .padding(top = 40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept clicks inside Card
                    )
                    .testTag("zone_detail_popup_${zone.key}"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B) // slate dark background
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = com.example.ui.theme.themedShape(20.dp)
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
                                .background(activeColor.copy(alpha = 0.15f), com.example.ui.theme.themedShape(10.dp)),
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
                                modifier = Modifier.size(18.dp).then(headerIconModifier)
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
                                Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.4f), com.example.ui.theme.themedShape(2.dp)))
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
                            .background(Color.White.copy(alpha = 0.05f), com.example.ui.theme.themedShape(8.dp))
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

                // Temps row & power button layout with dual Thermal Aura Gradients
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f), com.example.ui.theme.themedShape(14.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), com.example.ui.theme.themedShape(14.dp))
                        .drawBehind {
                            if (zone.currentHvacMode.lowercase() != "off") {
                                // Left Current Temp Aura Pool
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            activeColor.copy(alpha = detailAuraAlpha),
                                            Color.Transparent
                                        ),
                                        radius = size.width * 0.45f * detailAuraScale
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.5f)
                                )
                                // Right Target Temp Aura Pool
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            activeColor.copy(alpha = detailAuraAlpha * 0.8f),
                                            Color.Transparent
                                        ),
                                        radius = size.width * 0.4f * detailAuraScale
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.5f)
                                )
                            }
                        }
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
                            // Powering a zone on is a mode change, so it gets the same check the
                            // mode buttons get. Without this the power button was a way around
                            // the conflict guard.
                            val conflict = if (zone.currentHvacMode.lowercase() == "off") {
                                viewModel.detectModeConflict(
                                    zone.key, viewModel.powerOnModeFor(globalHvacMode)
                                )
                            } else null
                            if (conflict != null) {
                                pendingModeConflict = conflict
                            } else {
                                viewModel.toggleZonePower(zone.climateEntityId, zone.currentHvacMode, globalHvacMode, zone.name)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (zone.currentHvacMode.lowercase() != "off") {
                                    activeColor.copy(alpha = 0.15f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                },
                                com.example.ui.theme.themedShape(10.dp)
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

                // Per-zone mode. Off is deliberately absent — the power button above owns it.
                Text(
                    text = "ZONE MODE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.3.sp
                )
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(
                        Triple("heat", Icons.Default.Whatshot, theme.heatColor),
                        Triple("cool", Icons.Default.AcUnit, theme.coolColor),
                        Triple("dry", Icons.Default.Air, theme.dryColor)
                    ).forEach { (mode, icon, color) ->
                        SegmentedControlButton(
                            label = mode,
                            icon = icon,
                            color = color,
                            isSelected = zone.currentHvacMode.lowercase() == mode,
                            contentDescription = "Set ${zone.name} to $mode",
                            testTagId = "zone_mode_btn_${zone.key}_$mode",
                            onClick = {
                                // Check before sending: a refusal after the fact is far worse
                                // feedback than not sending at all. The n8n watchdog remains
                                // the real enforcement if anything gets past this.
                                val conflict = viewModel.detectModeConflict(zone.key, mode)
                                if (conflict != null) {
                                    pendingModeConflict = conflict
                                } else {
                                    viewModel.setZoneHvacMode(zone.climateEntityId, mode, zone.name)
                                }
                            }
                        )
                    }
                }

                // A multi-split shares one outdoor unit, so a head asking for cool while the
                // house is heating (or the reverse) is a conflict the equipment cannot serve.
                val zoneModeLower = zone.currentHvacMode.lowercase()
                val houseModeLower = globalHvacMode.lowercase()
                val coolFamily = setOf("cool", "dry")
                val conflictsWithHouse = houseModeLower != "off" && zoneModeLower != "off" &&
                    ((zoneModeLower == "heat" && houseModeLower in coolFamily) ||
                        (zoneModeLower in coolFamily && houseModeLower == "heat"))
                if (conflictsWithHouse) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(com.example.ui.theme.themedShape(8.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "This zone is set to ${zoneModeLower.uppercase()} while the house is " +
                                "${houseModeLower.uppercase()}. The outdoor unit can only do one at a time.",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B).copy(alpha = 0.95f),
                            lineHeight = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Schedule Setpoints Section
                //
                // Which block this edits follows the ZONE's own mode whenever the head is actually
                // running, and only falls back to the house mode when it is not. It used to follow
                // the house mode unconditionally — even though the popup detects and displays the
                // divergence thirty lines above ("This zone is set to COOL while the house is
                // HEAT") and then ignored it here. With the house off and a zone manually cooling,
                // the panel labelled itself "(Heat Mode)", showed the heat numbers as that zone's
                // live targets, and the stepper below pushed a heat setpoint at a cooling head.
                val zoneMode = zone.currentHvacMode.lowercase()
                val zoneIsRunning = !com.example.model.isNotReporting(zoneMode) && zoneMode != "off"
                val activeModeForPresets = when {
                    zoneIsRunning -> zoneMode
                    globalHvacMode.lowercase() == "off" -> lastNonOffHvacMode
                    else -> globalHvacMode
                }
                val blockPresets = if (activeModeForPresets.lowercase() == "cool") zone.presetsCool else zone.presetsHeat
                val presetLabelType = if (activeModeForPresets.lowercase() == "cool") "Cool" else "Heat"
                // The stepper writes the helper always, but only pushes the value straight at the
                // head when that head is awake and running the family this block belongs to.
                // Writing to a sleeping head is silently dropped today, and would actively power
                // it on if the Airstage integration's turn-on-before-set-temp option is enabled.
                val applyNowSafe = zoneIsRunning

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
                    // Spring-Physics Morphing presets scale / opacity animation card pop
                    val springScale by animateFloatAsState(
                        targetValue = if (isActive) 1.02f else 1.00f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "preset_scale_${label}"
                    )
                    val springAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.82f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "preset_alpha_${label}"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .graphicsLayer(
                                scaleX = springScale,
                                scaleY = springScale,
                                alpha = springAlpha
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) activeColor.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.02f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) activeColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f)
                        ),
                        shape = com.example.ui.theme.themedShape(12.dp)
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
                                                .background(activeColor.copy(alpha = 0.15f), com.example.ui.theme.themedShape(4.dp))
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
                                        .clip(com.example.ui.theme.themedShape(8.dp))
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
                                                if (isActive && applyNowSafe) {
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
                                        .clip(com.example.ui.theme.themedShape(8.dp))
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
                                                if (isActive && applyNowSafe) {
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

                val isDebouncing by viewModel.isDebouncing.collectAsStateWithLifecycle()
                val activeBannerShow = bannerVisible || isDebouncing

                AnimatedVisibility(
                    visible = activeBannerShow,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isDebouncing) activeColor.copy(alpha = 0.6f) else Color(0xFF10B981).copy(alpha = 0.6f)
                        ),
                        shape = com.example.ui.theme.themedShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isDebouncing) {
                                val pulseTransition = rememberInfiniteTransition(label = "banner_pulse")
                                val dotAlpha by pulseTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "dot_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(activeColor.copy(alpha = dotAlpha), CircleShape)
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), CircleShape)
                                )
                                Text(
                                    text = "Holding adjustments... Syncing in 2s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = bannerMessage ?: "Settings synced",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
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
                                .background(Color.White.copy(alpha = 0.04f), com.example.ui.theme.themedShape(8.dp))
                                .padding(4.dp)
                                .horizontalScroll(vaneScrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            zone.vaneOptions.forEach { mode ->
                                val isSelected = zone.vaneMode.lowercase() == mode.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(com.example.ui.theme.themedShape(6.dp))
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
                                .background(Color.White.copy(alpha = 0.04f), com.example.ui.theme.themedShape(8.dp))
                                .padding(4.dp)
                                .horizontalScroll(fanScrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            zone.fanOptions.forEach { speed ->
                                val isSelected = zone.fanMode.lowercase() == speed.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(com.example.ui.theme.themedShape(6.dp))
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
                    shape = com.example.ui.theme.themedShape(12.dp),
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
}

// ======================== TAB 2: AUXILIARIES (LIGHTS & POWER) ========================

// ======================== TAB 4: SYSTEM UPDATES ========================

/**
 * The Updates tab: is this panel's layout current, and one button to make it so.
 *
 * Rebuilt 2026-10-18 because the action was below the fold — on the A9 you scrolled past a
 * paragraph about over-the-air sync, the repository box and two link buttons to reach it. Now the
 * status and the button are the first thing on the page, the history is a compact list beneath,
 * and the links and the restore are one quiet footer line.
 *
 * Honest about scope: this page pulls layout_config.json and nothing else. New app code arrives
 * by reinstalling, so a push that changed only code is reported as such rather than offered.
 */
@Composable
fun UpdatesTab(
    viewModel: HvacViewModel
) {
    val context = LocalContext.current
    val theme = LocalHvacTheme.current
    val layoutVersion by viewModel.layoutVersion.collectAsStateWithLifecycle()
    val activeVersion by viewModel.activeVersion.collectAsStateWithLifecycle()
    val appliedLayoutCommit by viewModel.appliedLayoutCommit.collectAsStateWithLifecycle()
    val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val githubBranch by viewModel.githubBranch.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val recentCommits by viewModel.recentCommits.collectAsStateWithLifecycle()
    // Rolling back or restoring replaces the layout this panel runs on; ask first.
    var pendingConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    pendingConfirm?.let { (title, message, action) ->
        ConfirmDialog(
            title = title,
            message = message,
            confirmLabel = "Replace layout",
            destructive = false,
            onConfirm = action,
            onDismiss = { pendingConfirm = null }
        )
    }
    val latestPush by viewModel.latestPush.collectAsStateWithLifecycle()

    LaunchedEffect(activeVersion) {
        viewModel.checkForUpdates(activeVersion)
    }

    // No auto-install. A staged APK opens the real system installer, so firing it from a side
    // effect would throw an OS dialog at whoever is standing at the panel.

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_tab")
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        UpdateStatusCard(
            state = updateState,
            appVersion = activeVersion,
            layoutVersion = layoutVersion,
            appliedCommit = appliedLayoutCommit,
            latestPush = latestPush,
            onCheck = { viewModel.checkForUpdates(activeVersion) },
            onApplyLayout = { url -> viewModel.downloadUpdateAndInstall(context, url) },
            onDownloadApk = { url, version -> viewModel.downloadApkAndStage(context, url, version) },
            onInstallApk = { path -> viewModel.installApk(context, path) }
        )

        if (recentCommits.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                border = BorderStroke(1.dp, hvacBorderAlphaColor()),
                shape = hvacCardShape(14)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "RECENT PUSHES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    recentCommits.forEach { commit ->
                        val shortSha = commit.sha.take(7)
                        val isActive = appliedLayoutCommit.isNotEmpty() && appliedLayoutCommit.startsWith(shortSha)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = shortSha,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (isActive) theme.ecoColor else Color.White.copy(alpha = 0.45f)
                            )
                            Text(
                                text = commit.commit?.message?.lineSequence()?.firstOrNull().orEmpty(),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = if (isActive) 0.95f else 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = relativeCommitTime(commit.commit?.author?.date),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            if (isActive) {
                                Text(
                                    "RUNNING",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    color = theme.ecoColor,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .background(theme.ecoColor.copy(alpha = 0.12f), com.example.ui.theme.themedShape(4.dp))
                                        .padding(vertical = 3.dp),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                // Pull this commit's layout — for going back to an earlier one.
                                Text(
                                    "USE THIS",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .clip(com.example.ui.theme.themedShape(4.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), com.example.ui.theme.themedShape(4.dp))
                                        .clickable {
                                            pendingConfirm = Triple(
                                                "Use the layout from $shortSha?",
                                                "This panel switches to the layout as it was at that push. Newer layout changes stop applying here until you update again.",
                                                {
                                                    viewModel.downloadUpdateAndInstall(
                                                        context,
                                                        "https://raw.githubusercontent.com/$githubRepo/${commit.sha}/layout_config.json",
                                                        commitSha = commit.sha
                                                    )
                                                }
                                            )
                                        }
                                        .padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // The rarely-used things, on one line.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "$githubRepo · $githubBranch",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FooterLink("GitHub") {
                try {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/$githubRepo/tree/$githubBranch")
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "No browser available", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            FooterLink("Raw file") {
                try {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://raw.githubusercontent.com/$githubRepo/$githubBranch/layout_config.json")
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "No browser available", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            FooterLink("Pull again") {
                viewModel.downloadUpdateAndInstall(
                    context,
                    "https://raw.githubusercontent.com/$githubRepo/$githubBranch/layout_config.json"
                )
            }
            FooterLink("Restore built-in", theme.coolColor) {
                pendingConfirm = Triple(
                    "Restore the built-in layout?",
                    "This panel drops the layout pulled from GitHub and goes back to the one that came with the app.",
                    {
                        viewModel.resetLayoutToDefault()
                        android.widget.Toast.makeText(
                            context,
                            "Layout reset to the built-in ${viewModel.layoutVersion.value}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }
}

/** Status first, with the one action that matters right now beside it. */
@Composable
private fun UpdateStatusCard(
    state: UpdateState,
    appVersion: String,
    layoutVersion: String,
    appliedCommit: String,
    latestPush: com.example.viewmodel.LatestPush?,
    onCheck: () -> Unit,
    onApplyLayout: (String) -> Unit,
    onDownloadApk: (String, String) -> Unit,
    onInstallApk: (String) -> Unit
) {
    val theme = LocalHvacTheme.current
    val errorColor = Color(0xFFEF4444)

    val (icon, tint, title) = when (state) {
        is UpdateState.UpdateAvailable -> Triple(Icons.Default.CloudDownload, theme.ecoColor, "Layout update available")
        is UpdateState.Checking -> Triple(Icons.Default.Sync, Color.White.copy(alpha = 0.6f), "Checking GitHub…")
        is UpdateState.Downloading -> Triple(Icons.Default.CloudDownload, theme.coolColor, "Downloading…")
        is UpdateState.Installing -> Triple(Icons.Default.Sync, theme.coolColor, state.currentAction.ifBlank { "Applying…" })
        is UpdateState.Success -> Triple(Icons.Default.Build, theme.ecoColor, "App update downloaded")
        is UpdateState.Error -> Triple(Icons.Default.Warning, errorColor, "Couldn't check for updates")
        is UpdateState.UpToDate -> Triple(Icons.Default.CheckCircle, theme.ecoColor, "Up to date")
        else -> Triple(Icons.Default.CheckCircle, Color.White.copy(alpha = 0.6f), "Not checked yet")
    }
    val running = buildString {
        append("App $appVersion · layout $layoutVersion")
        if (appliedCommit.isNotEmpty()) append(" · ${appliedCommit.take(7)}")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(
            1.dp,
            if (state is UpdateState.UpdateAvailable) theme.ecoColor.copy(alpha = 0.45f) else hvacBorderAlphaColor()
        ),
        shape = hvacCardShape(14)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(running, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                when (state) {
                    is UpdateState.UpdateAvailable -> {
                        val isApk = state.downloadUrl.endsWith(".apk", ignoreCase = true)
                        Button(
                            onClick = { if (isApk) onDownloadApk(state.downloadUrl, state.version) else onApplyLayout(state.downloadUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.ecoColor, contentColor = Color.Black),
                            shape = com.example.ui.theme.themedShape(10.dp)
                        ) {
                            Text(if (isApk) "DOWNLOAD" else "UPDATE", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                    is UpdateState.Success -> Button(
                        onClick = { onInstallApk(state.apkPath) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.ecoColor, contentColor = Color.Black),
                        shape = com.example.ui.theme.themedShape(10.dp)
                    ) {
                        Text("INSTALL", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                    is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> {}
                    else -> OutlinedButton(
                        onClick = onCheck,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        shape = com.example.ui.theme.themedShape(10.dp)
                    ) {
                        Text("CHECK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // One line of detail under the headline, depending on what is going on.
            val detail: String? = when (state) {
                is UpdateState.UpdateAvailable -> listOfNotNull(
                    latestPush?.layoutVersion?.let { "Layout $it" },
                    latestPush?.headline?.takeIf { it.isNotBlank() }?.let { "“$it”" },
                    latestPush?.date?.let { relativeCommitTime(it) }?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifBlank { "New layout at ${state.version}" }
                is UpdateState.UpToDate -> if (latestPush?.layoutChanged == false)
                    "The latest push (“${latestPush.headline}”) changed app code only — nothing to pull. New app code needs a reinstall."
                else null
                is UpdateState.Error -> state.message
                else -> null
            }
            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    fontSize = 12.sp,
                    color = if (state is UpdateState.Error) errorColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 38.dp)
                )
            }

            val progress = when (state) {
                is UpdateState.Downloading -> state.progress
                is UpdateState.Installing -> state.progress
                else -> null
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .padding(start = 38.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(com.example.ui.theme.themedShape(2.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                            .fillMaxHeight()
                            .background(theme.coolColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterLink(label: String, color: Color = Color.White.copy(alpha = 0.6f), onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(com.example.ui.theme.themedShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** "3h ago" or "Oct 14" from GitHub's ISO time. */
private fun relativeCommitTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", java.util.Locale.US)
        val then = format.parse(iso)?.time ?: return ""
        val minutes = (System.currentTimeMillis() - then) / 60_000
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
            else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(then))
        }
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HvacLoginScreen(
    viewModel: HvacViewModel,
    glowColor: Color,
    bgGradient: Brush
) {
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
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
                    .background(Color(0xFF10B981).copy(alpha = 0.15f), com.example.ui.theme.themedShape(20.dp)),
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
                shape = com.example.ui.theme.themedShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "SECURE SYSTEM ACCESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Long-lived access token input
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Long-Lived Access Token", color = Color.White.copy(alpha = 0.4f)) },
                        placeholder = { Text("ey...", color = Color.White.copy(alpha = 0.2f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF10B981),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        visualTransformation = if (tokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ha_token_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                        },
                        trailingIcon = {
                            val image = if (tokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (tokenVisible) "Hide token" else "Show token"
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Guide / Instructions
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                        shape = com.example.ui.theme.themedShape(10.dp)
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
                                "Enter your Home Assistant Long-Lived Access Token. You can generate this token in your Home Assistant profile settings at the bottom of the page.",
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
                            shape = com.example.ui.theme.themedShape(10.dp),
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
                        onClick = { viewModel.login(token) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth().testTag("login_button"),
                        shape = com.example.ui.theme.themedShape(10.dp)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.clearSessions() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().testTag("clear_sessions_button"),
                        shape = com.example.ui.theme.themedShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Sessions",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "CLEAR SESSIONS / TOKENS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
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
                                    shape = com.example.ui.theme.themedShape(10.dp),
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
                                        shape = com.example.ui.theme.themedShape(10.dp)
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

@Composable
fun ThreeDayWeatherCard(
    weatherState: com.example.model.WeatherForecastState,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalHvacTheme.current
    val shape = hvacCardShape(defaultDp = 12)
    val bgColor = hvacCardBgColor()
    val borderColor = hvacBorderAlphaColor()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row with Icon, title, Refresh button and Minimize toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Weather Icon",
                        tint = theme.coolColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "INTEGRATED WEATHER FORECAST",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.2.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(com.example.ui.theme.themedShape(4.dp))
                            .background(theme.ecoColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AVG OF 2 SOURCES",
                            fontSize = 6.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.ecoColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp).testTag("weather_refresh_button")
                    ) {
                        if (weatherState.isLoading) {
                            CircularProgressIndicator(
                                color = theme.coolColor,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh weather",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleMinimize,
                        modifier = Modifier.size(28.dp).testTag("weather_minimize_toggle")
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isMinimized) "Expand weather" else "Minimize weather",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isMinimized) {
                Spacer(modifier = Modifier.height(8.dp))

                if (weatherState.error != null && weatherState.days.isEmpty()) {
                    Text(
                        text = weatherState.error,
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (weatherState.days.isEmpty() && weatherState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = theme.coolColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Aggregating Open-Meteo & MET Norway feeds...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        weatherState.days.take(3).forEach { day ->
                            WeatherForecastDayColumn(
                                day = day,
                                themeColors = theme,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Minimized view: show a compact today forecast summary to save space
                if (weatherState.days.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val today = weatherState.days.first()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (today.iconName.lowercase()) {
                                "sunny" -> Icons.Default.WbSunny
                                "cloudy" -> Icons.Default.Cloud
                                "rainy" -> Icons.Default.WaterDrop
                                "snowy" -> Icons.Default.Grain
                                "stormy" -> Icons.Default.Thunderstorm
                                else -> Icons.Default.Cloud
                            },
                            contentDescription = today.condition,
                            tint = when (today.iconName.lowercase()) {
                                "sunny" -> theme.heatColor
                                "cloudy" -> Color.White.copy(alpha = 0.7f)
                                "rainy" -> theme.coolColor
                                "snowy" -> Color(0xFFE2E8F0)
                                "stormy" -> Color(0xFFA855F7)
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Today: ${today.condition} · High: ${today.avgHighTemp.toInt()}°F · Low: ${today.avgLowTemp.toInt()}°F",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherForecastDayColumn(
    day: com.example.model.WeatherDay,
    themeColors: com.example.ui.theme.HvacThemeColors,
    modifier: Modifier = Modifier
) {
    Card(
        shape = com.example.ui.theme.themedShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.dayLabel,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = if (day.dayLabel == "TODAY") themeColors.heatColor else Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Icon(
                imageVector = when (day.iconName) {
                    "sunny" -> Icons.Default.WbSunny
                    "cloudy" -> Icons.Default.Cloud
                    "rainy" -> Icons.Default.WaterDrop
                    "snowy" -> Icons.Default.Grain
                    "stormy" -> Icons.Default.Thunderstorm
                    else -> Icons.Default.Cloud
                },
                contentDescription = day.condition,
                tint = when (day.iconName) {
                    "sunny" -> themeColors.heatColor
                    "cloudy" -> Color.White.copy(alpha = 0.7f)
                    "rainy" -> themeColors.coolColor
                    "snowy" -> Color(0xFFE2E8F0)
                    "stormy" -> Color(0xFFA855F7)
                    else -> Color.White.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = day.condition,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Temperatures Display: HIGH and LOW
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${day.avgHighTemp.toInt()}°",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "${day.avgLowTemp.toInt()}°",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }

            // Sources info (mini text)
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(themeColors.coolColor)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "OM:${day.openMeteoHigh?.toInt() ?: "--"}°|MN:${day.metNoHigh?.toInt() ?: "--"}°",
                    fontSize = 5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.3f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun FirstTimeSetupDialog(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val requestNotificationLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
        if (granted) {
            try {
                com.example.service.HvacForegroundService.startService(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true
                }
                isNotificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .border(BorderStroke(2.dp, Color(0xFF2196F3).copy(alpha = 0.5f)), com.example.ui.theme.themedShape(28.dp)),
            shape = com.example.ui.theme.themedShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F172A)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF2196F3).copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Welcome to Home HVAC!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "To ensure uninterrupted connection with your smart thermostat and live updates in the background, please complete the two critical permission checks below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Item 1: Notifications
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), com.example.ui.theme.themedShape(16.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), com.example.ui.theme.themedShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "1. Enable Notifications",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isNotificationGranted) "Permission granted" else "Required to show active thermostat connection",
                            fontSize = 11.sp,
                            color = if (isNotificationGranted) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    if (!isNotificationGranted) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    isNotificationGranted = true
                                    try {
                                        com.example.service.HvacForegroundService.startService(context)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                                contentColor = Color(0xFF2196F3)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f)),
                            shape = com.example.ui.theme.themedShape(10.dp)
                        ) {
                            Text("GRANT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Item 2: Unrestricted Battery Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), com.example.ui.theme.themedShape(16.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), com.example.ui.theme.themedShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. Unrestricted Battery",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isIgnoringBattery) "App is whitelisted" else "Bypass standby to keep active connection alive",
                            fontSize = 11.sp,
                            color = if (isIgnoringBattery) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    if (!isIgnoringBattery) {
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent().apply {
                                        action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallbackIntent)
                                    } catch (ex: Exception) {
                                        android.widget.Toast.makeText(context, "Could not open settings automatically", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                isIgnoringBattery = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                                } else {
                                    true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                                contentColor = Color(0xFF2196F3)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f)),
                            shape = com.example.ui.theme.themedShape(10.dp)
                        ) {
                            Text("OPTIMIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Done Button
                Button(
                    onClick = {
                        onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    shape = com.example.ui.theme.themedShape(12.dp)
                ) {
                    Text("DONE & CONTINUE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
