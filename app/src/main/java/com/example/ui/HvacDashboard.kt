package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showSettingsDialog by remember { mutableStateOf(false) }

    // Dynamic state background color mapping matching home assistant transitions
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val themeConfig = layoutConfig.theme ?: HvacThemeConfig()
    val cardCornerStyle by viewModel.cardCornerStyle.collectAsStateWithLifecycle()
    val cardOpacity by viewModel.cardOpacity.collectAsStateWithLifecycle()

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
            ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!isLandscape) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    layoutConfig.appTitle ?: "HAVEN",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 3.sp,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    layoutConfig.appSubtitle ?: "HVAC SYSTEM CONTROLLER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f),
                                    letterSpacing = 1.sp
                                )
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
                                val connectionColor = when (uiState) {
                                    is HvacUiState.Success -> Color(0xFF10B981) // active green
                                    is HvacUiState.Loading -> Color(0xFFF59E0B) // active yellow
                                    is HvacUiState.Error -> Color(0xFFEF4444)  // inactive red
                                }
                                val connectionText = when (uiState) {
                                    is HvacUiState.Success -> "CONNECTED"
                                    is HvacUiState.Loading -> "REFRESHING"
                                    is HvacUiState.Error -> "DISCONNECTED"
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
    viewModel: HvacViewModel
) {
    val context = LocalContext.current
    val theme = LocalHvacTheme.current
    val layoutUpdateAvailable by viewModel.layoutUpdateAvailable.collectAsStateWithLifecycle()
    var activeZoneDetail by remember { mutableStateOf<ClimateZone?>(null) }
    
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
            .testTag("tab_content_${tab.id}"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safe check for update available
        if (layoutUpdateAvailable != null && (tab.sections.contains("zones") || tab.sections.contains("updates"))) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            viewModel.downloadAndApplyLayoutUpdate { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        .testTag("zones_layout_update_banner_dynamic"),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "LAYOUT SPECIFICATION UPDATE AVAILABLE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                "Changes to layout_config.json detected on GitHub. Tap here to apply instantly!",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        tab.sections.forEach { section ->
            when (section.lowercase().trim()) {
                "sensors" -> {
                    item {
                        Column {
                            Text(
                                "ROOM SENSORS STATUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                            RoomSensorsStrip(rooms = state.roomSensors)
                        }
                    }
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
                                            .clickable { viewModel.toggleLight(light.entityId, light.isOn, light.name) },
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
                                            .clickable { viewModel.toggleLight(light.entityId, light.isOn, light.name) },
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
                                                    .clickable { viewModel.toggleSwitch(switch.entityId, switch.isOn, switch.name) },
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
    val rawTabs = layoutConfig.tabs ?: emptyList()
    
    val activeTabs = if (rawTabs.isNotEmpty()) rawTabs else listOf(
        TabConfig("zones", "ZONES & UNITS", "layers", listOf("sensors", "zones")),
        TabConfig("aux", "AUXILIARY POWER", "lightbulb", listOf("lights", "switches", "covers")),
        TabConfig("updates", "UPDATES", "cloud_download", listOf("updates"))
    )

    var isMenuExpanded by remember { mutableStateOf(true) }
    val sidePanelWidth by animateDpAsState(targetValue = if (isMenuExpanded) 260.dp else 72.dp, label = "side_panel_width")

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
                            Column {
                                Text(
                                    layoutConfig.appTitle ?: "HAVEN",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 3.sp,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    layoutConfig.appSubtitle ?: "HVAC CONTROLLER",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f),
                                    letterSpacing = 1.sp
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
                    val connectionColor = when (viewModel.uiState.value) {
                        is HvacUiState.Success -> Color(0xFF10B981)
                        is HvacUiState.Loading -> Color(0xFFF59E0B)
                        is HvacUiState.Error -> Color(0xFFEF4444)
                    }
                    val connectionText = when (viewModel.uiState.value) {
                        is HvacUiState.Success -> "CONNECTED"
                        is HvacUiState.Loading -> "REFRESHING"
                        is HvacUiState.Error -> "DISCONNECTED"
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
                                DynamicTabContent(tab = currentTab, state = state, viewModel = viewModel)
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

            // Custom unified tab row navigation conforming to standard M3 guidelines
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = when (state.globalSettings.globalHvacMode) {
                            "cool" -> Color(0xFF2196F3)
                            "off" -> Color(0xFF64748B)
                            else -> Color(0xFFF59E0B)
                        }
                    )
                },
                divider = {}
            ) {
                activeTabs.forEachIndexed { index, tabConfig ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier.testTag("nav_tab_$index")
                    ) {
                        Text(
                            text = tabConfig.title,
                            modifier = Modifier.padding(vertical = 12.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    DynamicTabContent(tab = currentTab, state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RoomSensorsStrip(rooms: List<RoomSensor>) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_sensors_strip"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rooms) { room ->
            Card(
                colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
                border = BorderStroke(1.dp, hvacBorderAlphaColor()),
                shape = hvacCardShape(12)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (room.id) {
                            "living_room" -> Icons.Default.Weekend
                            "bedroom" -> Icons.Default.Bed
                            "basement" -> Icons.Default.AcUnit
                            else -> Icons.Default.Thermostat
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = room.name,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = room.temp?.let { "${it.toInt()}°F" } ?: "--°F",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_quick_control_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(14)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // First row: HOUSE SCHEDULE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "HOUSE SCHEDULE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.globalSettings.houseSchedule.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (state.globalSettings.houseSchedule.lowercase()) {
                            "day" -> Color(0xFFF59E0B)
                            "night" -> Color(0xFF2196F3)
                            else -> Color(0xFF10B981)
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) color else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            // Second row: GLOBAL HVAC MODE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GLOBAL SEASON",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.globalSettings.globalHvacMode.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (state.globalSettings.globalHvacMode.lowercase()) {
                            "heat" -> Color(0xFFF59E0B)
                            "cool" -> Color(0xFF2196F3)
                            else -> Color(0xFF64748B) // off
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) color else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            // Third row: HOT WATER CONTROL
            Column {
                Text(
                    "HOT WATER MODE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) color else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
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
    val layoutUpdateAvailable by viewModel.layoutUpdateAvailable.collectAsStateWithLifecycle()
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
        if (layoutUpdateAvailable != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            viewModel.downloadAndApplyLayoutUpdate { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        .testTag("zones_layout_update_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LAYOUT SPECIFICATION UPDATE AVAILABLE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Changes to layout_config.json detected on GitHub. Tap here to apply instantly!",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

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
                            .clickable { viewModel.toggleLight(light.entityId, light.isOn, light.name) },
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
                            .clickable { viewModel.toggleLight(light.entityId, light.isOn, light.name) },
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
                                    .clickable { viewModel.toggleSwitch(switch.entityId, switch.isOn, switch.name) },
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
    val layoutUpdateAvailable by viewModel.layoutUpdateAvailable.collectAsStateWithLifecycle()
    val layoutUpdateError by viewModel.layoutUpdateError.collectAsStateWithLifecycle()
    val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val githubBranch by viewModel.githubBranch.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForLayoutUpdates()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_tab")
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GITHUB CONNECTION & SPECIFICATION PATH
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DYNAMIC DISPATCH SOURCE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            githubRepo,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = theme.coolColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Specification updates allow dynamic control configuration without full app compilation. The layout configuration is served directly from GitHub.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = theme.coolColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Source Branch: $githubBranch",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // EXTERNAL URL LINKS FOR SPECIFICATIONS
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Open file link",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VIEW ON GITHUB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://raw.githubusercontent.com/$githubRepo/$githubBranch/layout_config.json"))
                            context.startActivity(webIntent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Open raw link",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VIEW RAW JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // LAYOUT SPECIFICATION UPDATE CARD
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DYNAMIC LAYOUT ENGINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Layout Specification Version",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = theme.heatColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))

                // CURRENT VERSION ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ACTIVE SCHEMA VERSION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "v$layoutVersion",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.ecoColor
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.checkForLayoutUpdates()
                            android.widget.Toast.makeText(context, "Checking for latest schemas...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CHECK NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error State Display
                if (layoutUpdateError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "SPECIFICATION PARSING FAILURE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFEF5350),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = layoutUpdateError!!,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.clearLayoutUpdateError() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("CLEAR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Update Status Display
                if (layoutUpdateAvailable != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = theme.ecoColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val displayText = if (layoutUpdateAvailable == layoutVersion) {
                                "Layout draft changes detected on branch (v$layoutUpdateAvailable)"
                            } else {
                                "New design layout version available: v$layoutUpdateAvailable"
                            }
                            Text(
                                displayText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8F5E9)
                            )
                        }
                    }
                } else if (layoutUpdateError == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = theme.ecoColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Config is fully up to date with core layout specification",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // CONTROLSROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (layoutVersion != "1.0.0") {
                        Button(
                            onClick = {
                                viewModel.resetLayoutToDefault()
                                android.widget.Toast.makeText(context, "Specification restored to local default layout!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f),
                                contentColor = Color(0xFFEF5350)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("RESTORE DEFAULT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (layoutUpdateAvailable != null) {
                                viewModel.downloadAndApplyLayoutUpdate { success, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            } else {
                                viewModel.checkForLayoutUpdates()
                                android.widget.Toast.makeText(context, "Scanning branch specification...", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (layoutUpdateAvailable != null) theme.ecoColor else Color.White.copy(alpha = 0.08f),
                            contentColor = if (layoutUpdateAvailable != null) Color.Black else Color.White
                        ),
                        border = BorderStroke(1.dp, if (layoutUpdateAvailable != null) theme.ecoColor else Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (layoutUpdateAvailable != null) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PULL UPDATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHECK LAYOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                "HAVEN",
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
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
    val theme = LocalHvacTheme.current

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
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

                // Feature toggles list
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
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Select any premium preset theme engine below to update the deck visual cue",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }

                    val activeThemePreset by viewModel.selectedThemePreset.collectAsState()

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height(104.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(HvacThemePresetsList) { preset ->
                            val isSelected = activeThemePreset == preset.id
                            val presetAccentColor = parseHexColor(preset.accentColorHex, Color.White)
                            val presetBgStart = parseHexColor(preset.bgStartColorHex, Color.Black)
                            val presetBgEnd = parseHexColor(preset.bgEndColorHex, Color.DarkGray)
                            val presetCoolColor = parseHexColor(preset.coolColorHex, Color.White)

                            Card(
                                modifier = Modifier
                                    .width(180.dp)
                                    .fillMaxHeight()
                                    .clickable { viewModel.setSelectedThemePreset(preset.id) }
                                    .testTag("theme_preset_${preset.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) presetAccentColor else Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp).fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = preset.name.uppercase(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Visual dot representing the theme colors
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(presetAccentColor, CircleShape)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(presetCoolColor, CircleShape)
                                            )
                                        }
                                    }
                                    Text(
                                        text = preset.description,
                                        fontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        lineHeight = 10.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                    var repoText by remember(currentRepo) { mutableStateOf(currentRepo) }
                    var branchText by remember(currentBranch) { mutableStateOf(currentBranch) }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (repoText.isNotBlank() && branchText.isNotBlank()) {
                                    viewModel.updateGithubSettings(repoText, branchText)
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
                                    viewModel.updateGithubSettings("cstone1983/HVAC-Android-App", "main")
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

                // Metadata block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HAVEN CORE VERSION",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "v2.1.4 build-prod",
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
