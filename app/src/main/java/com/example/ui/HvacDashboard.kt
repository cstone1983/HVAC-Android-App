package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.*
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel
import kotlinx.coroutines.launch

private data class WaterHeaterItem(
    val option: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HvacDashboard(
    viewModel: HvacViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Status feedback toast notification
    LaunchedEffect(actionFeedback) {
        actionFeedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }

    // Dynamic state background color mapping matching home assistant transitions
    val scheduleState = when (val state = uiState) {
        is HvacUiState.Success -> state.globalSettings.houseSchedule
        else -> "Day"
    }

    val glowColor = when (scheduleState) {
        "Day" -> Color(0xFF2196F3).copy(alpha = 0.12f)
        "Night" -> Color(0xFFF59E0B).copy(alpha = 0.08f)
        else -> Color(0xFF10B981).copy(alpha = 0.1f)
    }

    val bgGradient = when (scheduleState) {
        "Day" -> Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
        "Night" -> Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))
        else -> Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF111827)))
    }

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
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "HAVEN",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                "HVAC SYSTEM CONTROLLER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
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
                                .padding(end = 12.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable {
                                    scope.launch { viewModel.fetchStates() }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(connectionColor, RoundedCornerShape(4.dp))
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
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
                        HvacDashboardContent(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun HvacDashboardContent(
    state: HvacUiState.Success,
    viewModel: HvacViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("ZONES & UNITS", "SCHEDULE & MODES", "AUXILIARY POWER", "UPDATES")

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
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("nav_tab_$index")
                ) {
                    Text(
                        text = label,
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
        ) { currentTab ->
            when (currentTab) {
                0 -> ClimateZonesTab(state = state, viewModel = viewModel)
                1 -> SystemScheduleTab(state = state, viewModel = viewModel)
                2 -> AuxiliariesTab(state = state, viewModel = viewModel)
                3 -> UpdatesTab(viewModel = viewModel)
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
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp)
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
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // First row: HOUSE SCHEDULE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("Day", Icons.Default.WbSunny, Color(0xFFF59E0B)),
                        Triple("Night", Icons.Default.NightsStay, Color(0xFF2196F3)),
                        Triple("Away", Icons.Default.ExitToApp, Color(0xFF10B981))
                    ).forEach { (label, icon, color) ->
                        val isSelected = state.globalSettings.houseSchedule.lowercase() == label.lowercase()
                        IconButton(
                            onClick = { viewModel.selectHouseSchedule(label) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f),
                                    RoundedCornerShape(10.dp)
                                )
                                .testTag("main_schedule_btn_${label.lowercase()}"),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isSelected) color else Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Set schedule to $label",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(10.dp))

            // Second row: GLOBAL HVAC MODE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("heat", Icons.Default.Whatshot, Color(0xFFF59E0B)),
                        Triple("cool", Icons.Default.AcUnit, Color(0xFF2196F3)),
                        Triple("off", Icons.Default.PowerSettingsNew, Color(0xFFEF4444))
                    ).forEach { (label, icon, color) ->
                        val isSelected = state.globalSettings.globalHvacMode.lowercase() == label.lowercase()
                        IconButton(
                            onClick = { viewModel.selectGlobalHvacMode(label) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f),
                                    RoundedCornerShape(10.dp)
                                )
                                .testTag("main_hvac_mode_btn_${label.lowercase()}"),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isSelected) color else Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Set global mode to $label",
                                modifier = Modifier.size(18.dp)
                            )
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
    var expandedZoneKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("climate_zones_tab"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    "6 ACTIVE ZONE CONTROLLERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }

        items(state.zones) { zone ->
            val isExpanded = expandedZoneKey == zone.key

            ZoneCardItem(
                zone = zone,
                isExpanded = isExpanded,
                globalHvacMode = state.globalSettings.globalHvacMode,
                activeScheduleState = state.globalSettings.houseSchedule,
                onHeaderClick = {
                    expandedZoneKey = if (isExpanded) null else zone.key
                },
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ZoneCardItem(
    zone: ClimateZone,
    isExpanded: Boolean,
    globalHvacMode: String,
    activeScheduleState: String,
    onHeaderClick: () -> Unit,
    viewModel: HvacViewModel
) {
    val activeColor = when (zone.currentHvacMode.lowercase()) {
        "heat" -> Color(0xFFF59E0B)
        "cool" -> Color(0xFF2196F3)
        "off" -> Color(0xFF64748B)
        else -> Color(0xFFF59E0B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("zone_card_${zone.key}")
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (zone.overrideOn) {
                Color(0xFFF59E0B).copy(alpha = 0.08f)
            } else {
                Color.White.copy(alpha = 0.03f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (zone.overrideOn) {
                Color(0xFFF59E0B).copy(alpha = 0.6f)
            } else if (!zone.autoOn) {
                Color(0xFFEF4444).copy(alpha = 0.4f)
            } else {
                Color.White.copy(alpha = 0.06f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(activeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
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

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = zone.currentTemp?.let { "${it.toInt()}" } ?: "--",
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            color = Color.White
                        )
                        Text(
                            text = "°F",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp, start = 1.dp)
                        )
                    }
                    Text(
                        text = "set to ${zone.targetTemp?.toInt() ?: "--"}°",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.15f))
                        .padding(16.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(bottom = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1.2f),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "THERMOSTAT SETPOINT",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    IconButton(
                                        onClick = {
                                            val current = zone.targetTemp
                                            if (current != null) {
                                                viewModel.setTargetTemperature(zone.climateEntityId, current - 1.0, zone.name)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                            .testTag("stepper_dec_${zone.key}")
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Target", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = zone.targetTemp?.let { "${it.toInt()}°" } ?: "--°",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Light,
                                        color = activeColor
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    IconButton(
                                        onClick = {
                                            val current = zone.targetTemp
                                            if (current != null) {
                                                viewModel.setTargetTemperature(zone.climateEntityId, current + 1.0, zone.name)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                            .testTag("stepper_inc_${zone.key}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase Target", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Direct override",
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    "SCHEDULE PRESETS",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val blockPresets = if (globalHvacMode == "cool") zone.presetsCool else zone.presetsHeat
                                val presetLabelType = if (globalHvacMode == "cool") "Cool" else "Heat"

                                PresetTempRow(
                                    label = "DAY",
                                    value = blockPresets.dayValue,
                                    tint = if (activeScheduleState == "Day") activeColor else Color.White.copy(alpha = 0.6f),
                                    onAdjust = { newVal ->
                                        viewModel.setPresetTemperature(blockPresets.day, newVal, "${zone.name} Day $presetLabelType")
                                    }
                                )
                                PresetTempRow(
                                    label = "NIGHT",
                                    value = blockPresets.nightValue,
                                    tint = if (activeScheduleState == "Night") activeColor else Color.White.copy(alpha = 0.6f),
                                    onAdjust = { newVal ->
                                        viewModel.setPresetTemperature(blockPresets.night, newVal, "${zone.name} Night $presetLabelType")
                                    }
                                )
                                PresetTempRow(
                                    label = "AWAY",
                                    value = blockPresets.awayValue,
                                    tint = if (activeScheduleState == "Away") activeColor else Color.White.copy(alpha = 0.6f),
                                    onAdjust = { newVal ->
                                        viewModel.setPresetTemperature(blockPresets.away, newVal, "${zone.name} Away $presetLabelType")
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Enable automation", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f))
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
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("OVERRIDE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Temp protection", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f))
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

                    Text(
                        "VENTILATION & VANE DEFLECTION",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vane Direction", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                listOf("Auto", "Swing", "Up", "Down").forEach { mode ->
                                    val isSelected = zone.vaneMode.lowercase() == mode.lowercase()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) activeColor else Color.Transparent)
                                            .clickable {
                                                viewModel.selectVaneMode(zone.tiltEntityId, mode, zone.name)
                                            }
                                            .padding(vertical = 6.dp),
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fan Power", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                listOf("Auto", "Quiet", "Low", "High").forEach { speed ->
                                    val isSelected = zone.fanMode.lowercase() == speed.lowercase()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) activeColor else Color.Transparent)
                                            .clickable {
                                                viewModel.selectFanMode(zone.fanEntityId, speed, zone.name)
                                            }
                                            .padding(vertical = 6.dp),
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
            color = tint
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

// ======================== TAB 2: SYSTEM SCHEDULES ========================

@Composable
fun SystemScheduleTab(
    state: HvacUiState.Success,
    viewModel: HvacViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("system_schedule_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "HOUSE SCHEDULE MODE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Day", Icons.Default.WbSunny, Color(0xFFF59E0B)),
                            Triple("Night", Icons.Default.NightsStay, Color(0xFF2196F3)),
                            Triple("Away", Icons.Default.ExitToApp, Color(0xFF10B981))
                        ).forEach { (label, icon, color) ->
                            val isSelected = state.globalSettings.houseSchedule.lowercase() == label.lowercase()
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("schedule_btn_${label.lowercase()}")
                                    .clickable { viewModel.selectHouseSchedule(label) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) color else Color.White.copy(alpha = 0.05f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) color else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = label.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "GLOBAL SEASONAL MODE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("heat", Icons.Default.Whatshot, Color(0xFFF59E0B)),
                            Triple("cool", Icons.Default.AcUnit, Color(0xFF2196F3)),
                            Triple("off", Icons.Default.PowerSettingsNew, Color(0xFFEF4444))
                        ).forEach { (label, icon, color) ->
                            val isSelected = state.globalSettings.globalHvacMode.lowercase() == label.lowercase()
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("global_hvac_btn_${label.lowercase()}")
                                    .clickable { viewModel.selectGlobalHvacMode(label) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) color else Color.White.copy(alpha = 0.05f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) color else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = label.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "WATER HEATER OPERATIONAL MODE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) color else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
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
}

// ======================== TAB 3: AUXILIARIES (LIGHTS & POWER) ========================

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val currentVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    LaunchedEffect(Unit) {
        if (updateState is com.example.viewmodel.UpdateState.Idle) {
            viewModel.checkForUpdates(currentVersion)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("updates_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
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
                        Column {
                            Text(
                                "GITHUB REPOSITORY UPDATE PROVISION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "cstone1983/HVAC-Android-App",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "INSTALLED APP VERSION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "v$currentVersion",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2196F3)
                            )
                        }

                        Button(
                            onClick = { viewModel.checkForUpdates(currentVersion) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHECK NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            AnimatedContent(
                targetState = updateState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "update_state_crossfade"
            ) { state ->
                when (state) {
                    is com.example.viewmodel.UpdateState.Idle -> {
                        // Checking update status placeholder
                    }

                    is com.example.viewmodel.UpdateState.Checking -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF2196F3)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "Checking GitHub releases...",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    is com.example.viewmodel.UpdateState.UpToDate -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            Color(0xFF10B981).copy(alpha = 0.15f),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "SYSTEM IS UP TO DATE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        "You are running the latest version: v$currentVersion",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    is com.example.viewmodel.UpdateState.UpdateAvailable -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "NEW UPDATE AVAILABLE!",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = Color(0xFFF59E0B),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            "Version ${state.version}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "RELEASE NOTES",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 140.dp)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = state.releaseNotes,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sizeMb = remember(state.size) {
                                        String.format("%.1f MB", state.size.toDouble() / (1024 * 1024))
                                    }

                                    Text(
                                        text = "Size: $sizeMb",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.downloadUpdateAndInstall(context, state.downloadUrl)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download package",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "DOWNLOAD UPDATE",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is com.example.viewmodel.UpdateState.Downloading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "DOWNLOADING HVAC STAGING PACKAGE...",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.5f),
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        "${state.progress}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2196F3)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val progressFloat = if (state.progress >= 0) state.progress / 100f else 0f
                                LinearProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF2196F3),
                                    trackColor = Color.White.copy(alpha = 0.08f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val dlMb = String.format("%.1f MB", state.downloaded.toDouble() / (1024 * 1024))
                                val totalMb = String.format("%.1f MB", state.totalSize.toDouble() / (1024 * 1024))
                                Text(
                                    text = "$dlMb / $totalMb",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    is com.example.viewmodel.UpdateState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.06f)),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(0xFF10B981).copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "DOWNLOAD COMPLETE",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color(0xFF10B981),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            "Update package is ready to apply",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        viewModel.installApk(context, state.apkPath)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "Install Apk",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "INSTALL NOW",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    is com.example.viewmodel.UpdateState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(0xFFEF4444).copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BugReport,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "PROVISIONING FAILURE",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color(0xFFEF4444),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            "Could not process updates",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = state.message,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        viewModel.checkForUpdates(currentVersion)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RETRY CHECK", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
