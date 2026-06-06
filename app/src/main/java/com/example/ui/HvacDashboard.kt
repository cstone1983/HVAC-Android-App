package com.example.ui

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
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        HvacSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

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

    if (!isLoggedIn) {
        HvacLoginScreen(
            viewModel = viewModel,
            glowColor = Color(0xFF2196F3).copy(alpha = 0.12f),
            bgGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
        )
        return
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
                if (!isLandscape) {
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
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { viewModel.logout() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .testTag("logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Log out",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

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
    val tabs = listOf("ZONES & UNITS", "AUXILIARY POWER", "UPDATES")

    var isMenuExpanded by remember { mutableStateOf(true) }
    val sidePanelWidth by animateDpAsState(targetValue = if (isMenuExpanded) 260.dp else 72.dp, label = "side_panel_width")
    val tabIcons = listOf(
        Icons.Default.Layers,
        Icons.Default.Lightbulb,
        Icons.Default.CloudDownload
    )

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
                                    "HAVEN",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 3.sp,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    "HVAC CONTROLLER",
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
                        tabs.forEachIndexed { index, label ->
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
                                    imageVector = tabIcons[index],
                                    contentDescription = label,
                                    tint = if (isSelected) activeColor else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (isMenuExpanded) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { onShowSettings() },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                        .testTag("settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.logout() },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                        .testTag("logout_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = "Log out",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
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

                        IconButton(
                            onClick = { onShowSettings() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                                .testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Log out",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
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
                        ) { currentTab ->
                            when (currentTab) {
                                0 -> ClimateZonesTab(state = state, viewModel = viewModel)
                                1 -> AuxiliariesTab(state = state, viewModel = viewModel)
                                2 -> UpdatesTab(viewModel = viewModel)
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
                    1 -> AuxiliariesTab(state = state, viewModel = viewModel)
                    2 -> UpdatesTab(viewModel = viewModel)
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
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(6.dp))

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

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(6.dp))

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
    var expandedZoneKey by remember { mutableStateOf<String?>(null) }

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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "°F",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 2.dp, start = 1.dp)
                        )
                    }
                    Text(
                        text = "set to ${zone.targetTemp?.toInt() ?: "--"}°",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.toggleZonePower(zone.climateEntityId, zone.currentHvacMode, globalHvacMode, zone.name)
                    },
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(36.dp)
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
                        tint = if (zone.currentHvacMode.lowercase() != "off") {
                            activeColor
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
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

                    val blockPresets = if (globalHvacMode == "cool") zone.presetsCool else zone.presetsHeat
                    val presetLabelType = if (globalHvacMode == "cool") "Cool" else "Heat"

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
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
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
                                    IconButton(
                                        onClick = {
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
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "decrease preset",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = value?.let { "${it.toInt()}°" } ?: "--°",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) activeColor else Color.White
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    IconButton(
                                        onClick = {
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
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "DIRECT CLIMATE TARGET",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "Currently set to: ${zone.targetTemp?.toInt() ?: "--"}°F",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
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
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
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

                    is com.example.viewmodel.UpdateState.NoReleases -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(0xFF2196F3).copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                              ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "REPOSITORY CONNECTED",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color(0xFF2196F3),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            "No published releases found yet",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "The connection to cstone1983/HVAC-Android-App is active and functional. However, there are no official compiled releases (.apk) published on your GitHub repository. Once you create a Release on GitHub and attach your APK, the app will auto-detect it here.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(14.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Want to test the update downloader?",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.simulateUpdate() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Simulate",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "RUN UPDATE SIMULATION",
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
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                            "You are running version: v$currentVersion",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                if (state.downloadUrl != null) {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.05f),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "WANT TO REINSTALL?",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White.copy(alpha = 0.4f),
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                "Force build re-install (tag ${state.version})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.downloadUpdateAndInstall(context, state.downloadUrl)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Force reinstall",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "FORCE REINSTALL",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isConnecting = uiState is HvacUiState.Loading

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
                    .fillMaxWidth(),
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
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
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
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
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
