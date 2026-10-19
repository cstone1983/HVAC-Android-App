package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.theme.hvacActiveBorderAlphaColor
import com.example.ui.theme.hvacActiveCardBgColor
import com.example.ui.theme.hvacBorderAlphaColor
import com.example.ui.theme.hvacCardBgColor
import com.example.ui.theme.hvacCardShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DeviceMode
import com.example.ui.theme.HvacThemePresetsList
import com.example.ui.theme.LocalHvacTheme
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel
import kotlinx.coroutines.launch

/**
 * Settings, rebuilt 2026-10-18 (option C of three mockups).
 *
 * The old dialog was three tabs — "Themes & Graphics", "System Engines", "Car Automotive" — whose
 * first screen was a list of theme cards taller than a tablet, with the things that decide whether
 * this device actually works scattered through the second tab or missing entirely.
 *
 * Now it opens on a health check for *this* device: connected, notifications allowed, able to get
 * through Do Not Disturb, free of battery limits. Anything amber taps straight to its fix. Under
 * that, the sections, each a short page. Wide screens show the section list beside the page; the
 * phone taps in and back. The theme picker is its own pop-up, opened from Appearance.
 *
 * Gone: the OTA update simulator (a test harness that faked updates) and the "live design preview"
 * (the app behind the dialog is the preview).
 */
@Composable
fun HvacSettingsDialog(
    viewModel: HvacViewModel,
    onDismiss: () -> Unit
) {
    val screen = LocalConfiguration.current
    var section by remember { mutableStateOf(SettingsSection.OVERVIEW) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = screen.screenHeightDp.dp * 0.92f)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), com.example.ui.theme.themedShape(20.dp))
                .testTag("settings_dialog"),
            shape = com.example.ui.theme.themedShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1322))
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 640.dp
                if (wide) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SectionList(
                            selected = section,
                            onSelect = { section = it },
                            modifier = Modifier
                                .width(200.dp)
                                .padding(12.dp)
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.06f))
                        )
                        SectionPage(
                            viewModel = viewModel,
                            section = section,
                            wide = true,
                            showBack = false,
                            onBack = { section = SettingsSection.OVERVIEW },
                            onOpen = { section = it },
                            onDismiss = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    SectionPage(
                        viewModel = viewModel,
                        section = section,
                        wide = false,
                        showBack = section != SettingsSection.OVERVIEW,
                        onBack = { section = SettingsSection.OVERVIEW },
                        onOpen = { section = it },
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Info),
    DEVICE("This device", Icons.Default.TabletAndroid),
    APPEARANCE("Appearance", Icons.Default.Palette),
    ALERTS("Alarm and alerts", Icons.Default.Notifications),
    CONNECTION("Connection", Icons.Default.Wifi),
    UPDATES("Updates", Icons.Default.CloudDownload),
    WEATHER("Weather", Icons.Default.Cloud),
    AUTO("Android Auto", Icons.Default.DirectionsCar),
    ABOUT("About", Icons.Default.Info)
}

@Composable
private fun SectionList(selected: SettingsSection, onSelect: (SettingsSection) -> Unit, modifier: Modifier) {
    val theme = LocalHvacTheme.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Settings",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 10.dp)
        )
        SettingsSection.values().forEach { s ->
            val on = s == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(com.example.ui.theme.themedShape(8.dp))
                    .background(if (on) theme.coolColor.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(s) }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .testTag("settings_nav_${s.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(s.icon, contentDescription = null, tint = if (on) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(17.dp))
                Text(s.title, fontSize = 13.sp, color = if (on) Color.White else Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SectionPage(
    viewModel: HvacViewModel,
    section: SettingsSection,
    wide: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpen: (SettingsSection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                // Beside the side list, which already says "Settings", the page names itself.
                if (section == SettingsSection.OVERVIEW && !wide) "Settings" else section.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close settings", tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (section) {
                SettingsSection.OVERVIEW -> OverviewPage(viewModel, showSections = !wide, onOpen = onOpen)
                SettingsSection.DEVICE -> DevicePage(viewModel)
                SettingsSection.APPEARANCE -> AppearancePage(viewModel)
                SettingsSection.ALERTS -> AlertsPage(viewModel)
                SettingsSection.CONNECTION -> ConnectionPage(viewModel)
                SettingsSection.UPDATES -> UpdatesSourcePage(viewModel)
                SettingsSection.WEATHER -> WeatherPage(viewModel)
                SettingsSection.AUTO -> AndroidAutoPage(viewModel)
                SettingsSection.ABOUT -> AboutPage(viewModel)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Health
// -------------------------------------------------------------------------------------------

/** What the system currently allows this app. Re-read whenever the app comes back to the front. */
private data class DeviceHealth(
    val notificationsAllowed: Boolean,
    val dndAccess: Boolean,
    val batteryUnrestricted: Boolean
)

private fun readHealth(context: Context): DeviceHealth {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return DeviceHealth(
        notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
        dndAccess = nm.isNotificationPolicyAccessGranted,
        batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)
    )
}

/** Health that refreshes when you come back from a system settings screen. */
@Composable
private fun rememberDeviceHealth(): DeviceHealth {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return remember(tick) { readHealth(context) }
}

private fun openSystemScreen(context: Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Try the next, broader screen.
        }
    }
    Toast.makeText(context, "Couldn't open that settings screen", Toast.LENGTH_SHORT).show()
}

private fun openNotificationSettings(context: Context) = openSystemScreen(
    context,
    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
)

private fun openDndAccess(context: Context) = openSystemScreen(
    context,
    Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
)

@android.annotation.SuppressLint("BatteryLife")
private fun openBatterySettings(context: Context) = openSystemScreen(
    context,
    Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}")),
    Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewPage(viewModel: HvacViewModel, showSections: Boolean, onOpen: (SettingsSection) -> Unit) {
    val context = LocalContext.current
    val health = rememberDeviceHealth()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val usingBackup by viewModel.usingBackupUrl.collectAsStateWithLifecycle()
    val deviceMode by viewModel.deviceMode.collectAsStateWithLifecycle()
    val appVersion by viewModel.activeVersion.collectAsStateWithLifecycle()
    val phone = deviceMode == DeviceMode.PHONE

    Text(
        "${android.os.Build.MODEL} · ${deviceMode.label} · $appVersion",
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.5f)
    )

    BoxWithConstraints {
        val fourAcross = maxWidth >= 520.dp
        val tiles: List<@Composable androidx.compose.foundation.layout.RowScope.() -> Unit> = listOf(
            {
                HealthTile(
                    modifier = Modifier.weight(1f),
                    icon = if (isOffline) Icons.Default.CloudOff else Icons.Default.Wifi,
                    label = "Connection",
                    value = if (isOffline) "Offline" else "Connected",
                    detail = if (isOffline) "tap for details" else if (usingBackup) "direct, at home" else "remote",
                    ok = !isOffline
                ) { onOpen(SettingsSection.CONNECTION) }
            },
            {
                HealthTile(
                    modifier = Modifier.weight(1f),
                    icon = if (health.notificationsAllowed) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    label = "Notifications",
                    value = if (health.notificationsAllowed) "Allowed" else "Turned off",
                    detail = if (health.notificationsAllowed) "alarm and status" else "tap to allow",
                    ok = health.notificationsAllowed
                ) { openNotificationSettings(context) }
                // A panel sounds through the alarm stream, which Do Not Disturb already lets through;
                // the access only matters for the phone's triggered-alarm notification.
            },
            {
                HealthTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DoNotDisturbOn,
                    label = "Do Not Disturb",
                    value = when {
                        health.dndAccess -> "Gets through"
                        phone -> "Can't get through"
                        else -> "Not needed"
                    },
                    detail = when {
                        health.dndAccess -> "alarm reaches you"
                        phone -> "tap to allow"
                        else -> "siren uses alarm volume"
                    },
                    ok = health.dndAccess || !phone
                ) { openDndAccess(context) }
            },
            {
                HealthTile(
                    modifier = Modifier.weight(1f),
                    icon = if (health.batteryUnrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                    label = "Battery",
                    value = if (health.batteryUnrestricted) "Unrestricted" else "Limited",
                    detail = if (health.batteryUnrestricted) "stays connected" else "tap to lift limits",
                    ok = health.batteryUnrestricted
                ) { if (!health.batteryUnrestricted) openBatterySettings(context) }
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(if (fourAcross) 4 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { it() } }
            }
        }
    }

    // The three things changed most often, right here.
    SettingsGroup {
        DeviceModeRow(viewModel)
        SwitchRow("Keep screen on", null, viewModel.forceScreenOn.collectAsStateWithLifecycle().value) { viewModel.setForceScreenOn(it) }
        SwitchRow("Full screen", "Hides the status and navigation bars", viewModel.forceFullScreen.collectAsStateWithLifecycle().value) { viewModel.setForceFullScreen(it) }
    }

    if (showSections) SettingsGroup {
        listOf(
            SettingsSection.APPEARANCE to currentThemeName(viewModel),
            SettingsSection.ALERTS to if (phone) "Notifications" else "Siren and beeps",
            SettingsSection.CONNECTION to friendlyServer(viewModel.haUrl.collectAsStateWithLifecycle().value),
            SettingsSection.UPDATES to viewModel.githubBranch.collectAsStateWithLifecycle().value,
            SettingsSection.WEATHER to "Forecast location",
            SettingsSection.AUTO to "Not working yet",
            SettingsSection.ABOUT to appVersion
        ).forEach { (s, value) ->
            NavRow(s.icon, s.title, value) { onOpen(s) }
        }
    }
}

@Composable
private fun HealthTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    ok: Boolean,
    onClick: () -> Unit
) {
    val good = Color(0xFF3CCF91)
    val warn = Color(0xFFF0A92A)
    Column(
        modifier = modifier
            .clip(com.example.ui.theme.themedShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (ok) Color.Transparent else warn.copy(alpha = 0.5f), com.example.ui.theme.themedShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = if (ok) good else warn, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp, color = if (ok) good else warn, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// -------------------------------------------------------------------------------------------
// Sections
// -------------------------------------------------------------------------------------------

@Composable
private fun DevicePage(viewModel: HvacViewModel) {
    val context = LocalContext.current
    val health = rememberDeviceHealth()
    SettingsGroup {
        DeviceModeRow(viewModel)
        SwitchRow("Keep screen on", "The screen never sleeps", viewModel.forceScreenOn.collectAsStateWithLifecycle().value) { viewModel.setForceScreenOn(it) }
        SwitchRow("Full screen", "Hides the status and navigation bars", viewModel.forceFullScreen.collectAsStateWithLifecycle().value) { viewModel.setForceFullScreen(it) }
        SwitchRow("Dark mode", null, viewModel.darkModeEnabled.collectAsStateWithLifecycle().value) { viewModel.setDarkModeEnabled(it) }
        ValueRow(
            "Battery limits",
            if (health.batteryUnrestricted) "Unrestricted — stays connected in the background"
            else "Limited — Android may cut the connection when idle",
            action = if (health.batteryUnrestricted) null else "Lift"
        ) { openBatterySettings(context) }
    }
}

@Composable
private fun AppearancePage(viewModel: HvacViewModel) {
    val theme = LocalHvacTheme.current
    var pickingTheme by remember { mutableStateOf(false) }
    val corner by viewModel.cardCornerStyle.collectAsStateWithLifecycle()
    val opacity by viewModel.cardOpacity.collectAsStateWithLifecycle()
    val background by viewModel.backgroundDesign.collectAsStateWithLifecycle()
    val presetId by viewModel.selectedThemePreset.collectAsStateWithLifecycle()

    // The settings window covers most of the screen, so the app behind it is no use for judging
    // a change. This is: it redraws with every tap and every slider movement.
    AppearancePreview(background, presetId)

    SettingsGroup {
        val preset = HvacThemePresetsList.firstOrNull { it.id == presetId }
        ValueRow("Theme", preset?.name ?: "Custom", action = "Change", leading = {
            if (preset != null) ThemeSwatch(preset.bgStartColorHex, preset.accentColorHex, preset.coolColorHex)
        }) { pickingTheme = true }
        SegmentRow(
            "Card corners",
            listOf("sharp" to "Sharp", "rounded" to "Soft", "ultra_rounded" to "Round"),
            corner
        ) { viewModel.setCardCornerStyle(it) }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Card glass", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text("${(opacity * 100).toInt()}%", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Slider(
                value = opacity,
                onValueChange = { viewModel.setCardOpacity(it) },
                valueRange = 0.01f..0.20f,
                colors = SliderDefaults.colors(thumbColor = theme.coolColor, activeTrackColor = theme.coolColor)
            )
        }
        SegmentRow(
            "Background",
            listOf("radial_glow" to "Glow", "grid" to "Grid", "nebula" to "Nebula", "aurora" to "Aurora", "minimal" to "Plain"),
            background
        ) { viewModel.setBackgroundDesign(it) }
    }

    if (pickingTheme) ThemePickerDialog(viewModel) { pickingTheme = false }
}

/** The theme presets, in a pop-up of their own so they never crowd the settings page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePickerDialog(viewModel: HvacViewModel, onDismiss: () -> Unit) {
    val presetId by viewModel.selectedThemePreset.collectAsStateWithLifecycle()
    val theme = LocalHvacTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101A2B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = com.example.ui.theme.themedShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Theme", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    Text(
                        "Done",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.coolColor,
                        modifier = Modifier
                            .clip(com.example.ui.theme.themedShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                Text("Changes apply as you tap.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))
                val background by viewModel.backgroundDesign.collectAsStateWithLifecycle()
                AppearancePreview(background, presetId, height = 120.dp)
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HvacThemePresetsList.forEach { preset ->
                        val on = preset.id == presetId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(com.example.ui.theme.themedShape(10.dp))
                                .background(if (on) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                                .border(1.dp, if (on) theme.coolColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f), com.example.ui.theme.themedShape(10.dp))
                                .clickable { viewModel.setSelectedThemePreset(preset.id) }
                                .padding(10.dp)
                                .testTag("theme_preset_${preset.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeCoreIcon(presetId = preset.id, size = 26.dp)
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(preset.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ThemeSwatch(preset.bgStartColorHex, preset.accentColorHex, preset.coolColorHex)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A miniature of the home screen in the current look: the theme's background and glow, one
 * heat pump running and one off, and a row of buttons — all through the same card colour, glass,
 * border and corner helpers the real screens use, so what it shows is what the panels will show.
 */
@Composable
private fun AppearancePreview(background: String, presetId: String, height: Dp = 150.dp) {
    val theme = LocalHvacTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(com.example.ui.theme.themedShape(12.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(theme.bgStart, theme.bgEnd)))
            .drawBehind { drawHvacBackground(background, theme, theme.glowColor, presetId) }
            .border(1.dp, Color.White.copy(alpha = 0.08f), com.example.ui.theme.themedShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreviewZone("Main Level", "RUNNING", "66°", "SET 70°", theme.heatColor, Modifier.weight(1f))
            PreviewZone("Anthony", "OFF", "67°", "SET 70°", null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("DAY" to true, "NIGHT" to false, "AWAY" to false).forEach { (label, on) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(hvacCardShape(8))
                        .background(if (on) theme.heatColor.copy(alpha = 0.15f) else hvacCardBgColor())
                        .border(1.dp, if (on) theme.heatColor else hvacBorderAlphaColor(), hvacCardShape(8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (on) Color.White else Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun PreviewZone(name: String, status: String, temp: String, target: String, tint: Color?, modifier: Modifier) {
    val shape = hvacCardShape(12)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(tint?.let { hvacActiveCardBgColor(it) } ?: hvacCardBgColor())
            .border(1.dp, tint?.let { hvacActiveBorderAlphaColor(it) } ?: hvacBorderAlphaColor(), shape)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Text(status, fontSize = 7.sp, fontWeight = FontWeight.Black, color = tint ?: Color.White.copy(alpha = 0.45f))
        }
        Text(temp, fontSize = 24.sp, fontWeight = FontWeight.Light, color = Color.White)
        Text(target, fontSize = 8.sp, fontWeight = FontWeight.Black, color = tint ?: Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun ThemeSwatch(bgHex: String, accentHex: String, coolHex: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(bgHex, accentHex, coolHex).forEach { hex ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex, Color.Gray))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            )
        }
    }
}

@Composable
private fun currentThemeName(viewModel: HvacViewModel): String {
    val presetId by viewModel.selectedThemePreset.collectAsStateWithLifecycle()
    return HvacThemePresetsList.firstOrNull { it.id == presetId }?.name ?: "Custom"
}

@Composable
private fun AlertsPage(viewModel: HvacViewModel) {
    val context = LocalContext.current
    val health = rememberDeviceHealth()
    val deviceMode by viewModel.deviceMode.collectAsStateWithLifecycle()

    Text(deviceMode.summary, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
    SettingsGroup {
        ValueRow(
            "Notifications",
            if (health.notificationsAllowed) "Allowed" else "Turned off — you won't hear about the alarm on this device",
            action = "Open"
        ) { openNotificationSettings(context) }
        ValueRow(
            "Through Do Not Disturb",
            when {
                health.dndAccess -> "Allowed — a triggered alarm reaches you on silent"
                deviceMode == DeviceMode.PHONE -> "Not allowed — a triggered alarm may be held back by Do Not Disturb"
                else -> "Not needed on a wall panel: the siren uses the alarm volume"
            },
            action = "Open"
        ) { openDndAccess(context) }
        ValueRow("Test beep", "One countdown beep at this device's volume", action = "Play", leading = {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }) { com.example.service.AlarmBeeper.beep(context) }
    }
    Text(
        "Which alarm events notify (armed, disarmed, triggered) is set for the whole house in layout_config.json under alarm.notifyEvents.",
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.4f)
    )
}

@Composable
private fun ConnectionPage(viewModel: HvacViewModel) {
    val context = LocalContext.current
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val usingBackup by viewModel.usingBackupUrl.collectAsStateWithLifecycle()
    val haUrl by viewModel.haUrl.collectAsStateWithLifecycle()
    val backupUrl by viewModel.backupHaUrl.collectAsStateWithLifecycle()
    var primary by remember(haUrl) { mutableStateOf(haUrl) }
    var backup by remember(backupUrl) { mutableStateOf(backupUrl) }

    Text(
        when {
            isOffline -> "Not connected to Home Assistant. Showing the last values it sent."
            usingBackup -> "Connected directly, through the home address."
            else -> "Connected through the main (remote) address."
        },
        fontSize = 13.sp,
        color = if (isOffline) Color(0xFFF0A92A) else Color(0xFF3CCF91)
    )
    HomeWifiSection(viewModel)
    SettingsField("Main address (away from home)", primary, { primary = it }, keyboard = KeyboardType.Uri)
    SettingsField("Home address (on the home Wi-Fi, and the backup)", backup, { backup = it }, keyboard = KeyboardType.Uri)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Save and reconnect", primary = true) {
            viewModel.updateHaUrl(primary)
            viewModel.updateBackupHaUrl(backup)
            Toast.makeText(context, "Saved — reconnecting", Toast.LENGTH_SHORT).show()
        }
        PillButton("Restore default") {
            viewModel.restoreDefaultHaUrl()
            primary = viewModel.getDefaultHaUrl()
        }
    }
    Text("The access token is built into the app and isn't shown here.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
}

/**
 * Which Wi-Fi counts as home. On it the app uses the home (local) address directly.
 *
 * "Use this Wi-Fi" asks for location access first, because Android will not tell any app the
 * network's name without it. Without a name the app still recognises home by its network
 * address, so the setting is a refinement, not a requirement.
 */
@Composable
private fun HomeWifiSection(viewModel: HvacViewModel) {
    val context = LocalContext.current
    val homeSsid by viewModel.homeWifiSsid.collectAsStateWithLifecycle()
    val onHome by viewModel.onHomeNetwork.collectAsStateWithLifecycle()
    val usingBackup by viewModel.usingBackupUrl.collectAsStateWithLifecycle()
    var ssidText by remember(homeSsid) { mutableStateOf(homeSsid) }
    var current by remember { mutableStateOf(viewModel.currentWifiSsid()) }

    fun useCurrent() {
        current = viewModel.currentWifiSsid()
        val ssid = current
        if (ssid == null) {
            Toast.makeText(context, "Android didn't give the Wi-Fi name — check location is on", Toast.LENGTH_LONG).show()
        } else {
            ssidText = ssid
            viewModel.setHomeWifiSsid(ssid)
            Toast.makeText(context, "Home Wi-Fi set to $ssid", Toast.LENGTH_SHORT).show()
        }
    }
    val askLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) useCurrent()
        else Toast.makeText(context, "Without location access the app can't read the Wi-Fi name", Toast.LENGTH_LONG).show()
    }

    SettingsGroup {
        ValueRow(
            "Home network",
            when (onHome) {
                true -> "On the home network — talking to Home Assistant directly" + if (usingBackup) "" else " (switching…)"
                false -> "Away from home — using the main address"
                null -> "Can't tell right now"
            }
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            SettingsField("Home Wi-Fi name (optional)", ssidText, { ssidText = it })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Use this Wi-Fi" + (current?.let { " ($it)" } ?: ""), primary = true) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) useCurrent() else askLocation.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
                PillButton("Save name") { viewModel.setHomeWifiSsid(ssidText) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Left blank, home is recognised by the network address instead, which needs no permission.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun UpdatesSourcePage(viewModel: HvacViewModel) {
    val context = LocalContext.current
    val repo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val branch by viewModel.githubBranch.collectAsStateWithLifecycle()
    val token by viewModel.githubToken.collectAsStateWithLifecycle()
    var repoText by remember(repo) { mutableStateOf(repo) }
    var branchText by remember(branch) { mutableStateOf(branch) }
    var tokenText by remember(token) { mutableStateOf(token) }

    Text(
        "Where the Updates tab looks for new layouts. The token is optional: it lets the devices check GitHub more often, and is required if the repository is ever made private.",
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.6f)
    )
    SettingsField("Repository (owner/name)", repoText, { repoText = it })
    SettingsField("Branch", branchText, { branchText = it })
    SettingsField("GitHub token (optional)", tokenText, { tokenText = it }, secret = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Save", primary = true) {
            viewModel.updateGithubSettings(repoText, branchText, tokenText)
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun WeatherPage(viewModel: HvacViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lat by viewModel.weatherLatitude.collectAsStateWithLifecycle()
    val lon by viewModel.weatherLongitude.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.example.api.GeocodingResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    Text(
        "Forecast for ${"%.3f".format(lat)}, ${"%.3f".format(lon)}",
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.7f)
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { SettingsField("Search for a place", query, { query = it }) }
        PillButton(if (searching) "Searching…" else "Search", primary = true) {
            if (query.isBlank() || searching) return@PillButton
            searching = true
            scope.launch {
                try {
                    val url = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(query, "UTF-8")}&count=5"
                    val response = com.example.api.WeatherClient.service.searchLocation(url)
                    results = response.body()?.results.orEmpty()
                    if (results.isEmpty()) Toast.makeText(context, "Nothing found for '$query'", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                } finally {
                    searching = false
                }
            }
        }
    }
    if (results.isNotEmpty()) {
        SettingsGroup {
            results.forEach { r ->
                val place = listOfNotNull(r.name, r.admin1, r.country).joinToString(", ")
                NavRow(Icons.Default.Search, place, "") {
                    val la = r.latitude
                    val lo = r.longitude
                    if (la != null && lo != null) {
                        viewModel.updateWeatherLocation(la, lo)
                        results = emptyList()
                        query = ""
                        Toast.makeText(context, "Forecast set to $place", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    PillButton("Reset to default location") { viewModel.resetWeatherLocation() }
}

@Composable
private fun AndroidAutoPage(viewModel: HvacViewModel) {
    val layout by viewModel.autoLayoutStyle.collectAsStateWithLifecycle()
    val primaryZone by viewModel.autoPrimaryZone.collectAsStateWithLifecycle()
    val showPower by viewModel.autoShowPowerAction.collectAsStateWithLifecycle()
    val showFan by viewModel.autoShowFanAction.collectAsStateWithLifecycle()
    val step by viewModel.autoTempStep.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val zones = (uiState as? HvacUiState.Success)?.zones.orEmpty()

    Text(
        "The car screen has not been working, so these are parked here. They only affect Android Auto.",
        fontSize = 12.sp,
        color = Color(0xFFF0A92A).copy(alpha = 0.85f)
    )
    SettingsGroup {
        SegmentRow("Car layout", listOf("list" to "List", "pane" to "Focus zone"), layout) { viewModel.setAutoLayoutStyle(it) }
        SegmentRow(
            "Focus zone",
            listOf("all" to "First") + zones.map { it.key to it.name },
            primaryZone
        ) { viewModel.setAutoPrimaryZone(it) }
        SwitchRow("Power button", null, showPower) { viewModel.setAutoShowPowerAction(it) }
        SwitchRow("Fan speed button", null, showFan) { viewModel.setAutoShowFanAction(it) }
        SegmentRow(
            "Temperature step",
            listOf("0.5" to "0.5°", "1.0" to "1°", "2.0" to "2°"),
            step.toString()
        ) { viewModel.setAutoTempStep(it.toFloat()) }
    }
}

@Composable
private fun AboutPage(viewModel: HvacViewModel) {
    val appVersion by viewModel.activeVersion.collectAsStateWithLifecycle()
    val layoutVersion by viewModel.layoutVersion.collectAsStateWithLifecycle()
    val commit by viewModel.appliedLayoutCommit.collectAsStateWithLifecycle()
    val deviceMode by viewModel.deviceMode.collectAsStateWithLifecycle()
    val haUrl by viewModel.haUrl.collectAsStateWithLifecycle()
    SettingsGroup {
        ValueRow("App", appVersion)
        ValueRow("Layout", layoutVersion + if (commit.isNotEmpty()) " · ${commit.take(7)}" else " · built in")
        ValueRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
        ValueRow("Mode", deviceMode.label)
        ValueRow("Home Assistant", friendlyServer(haUrl))
    }
}

/** "Nabu Casa remote" rather than a random subdomain; otherwise the host. */
private fun friendlyServer(url: String): String {
    val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
    return when {
        host.endsWith("nabu.casa") -> "Nabu Casa remote"
        host.isBlank() -> "not set"
        else -> host
    }
}

// -------------------------------------------------------------------------------------------
// Building blocks
// -------------------------------------------------------------------------------------------

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.example.ui.theme.themedShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            subtitle?.let { Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF10B981),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
    RowDivider()
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    action: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val theme = LocalHvacTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Text(value, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        leading?.invoke()
        action?.let {
            Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.coolColor)
        }
    }
    RowDivider()
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
        Text(title, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
    }
    RowDivider()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val theme = LocalHvacTheme.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title, fontSize = 14.sp, color = Color.White)
        Spacer(Modifier.height(7.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, label) ->
                val on = key == selected
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(com.example.ui.theme.themedShape(8.dp))
                        .background(if (on) theme.coolColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f))
                        .border(1.dp, if (on) theme.coolColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), com.example.ui.theme.themedShape(8.dp))
                        .clickable { onSelect(key) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
    RowDivider()
}

@Composable
private fun DeviceModeRow(viewModel: HvacViewModel) {
    val mode by viewModel.deviceMode.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Device mode", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(com.example.ui.theme.themedShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), com.example.ui.theme.themedShape(8.dp))
            ) {
                DeviceMode.values().forEach { m ->
                    val on = m == mode
                    Text(
                        m.label,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color(0xFF7FE0BD) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .background(if (on) Color(0xFF10B981).copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { viewModel.setDeviceMode(m) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("device_mode_" + m.name.lowercase())
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(mode.summary, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
    RowDivider()
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White, fontFamily = if (secret) FontFamily.Default else FontFamily.Default),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LocalHvacTheme.current.coolColor,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedLabelColor = Color.White.copy(alpha = 0.7f),
            unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
            cursorColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PillButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val theme = LocalHvacTheme.current
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (primary) Color.White else Color.White.copy(alpha = 0.75f),
        modifier = Modifier
            .clip(com.example.ui.theme.themedShape(10.dp))
            .background(if (primary) theme.coolColor.copy(alpha = 0.3f) else Color.Transparent)
            .border(1.dp, if (primary) theme.coolColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f), com.example.ui.theme.themedShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}
