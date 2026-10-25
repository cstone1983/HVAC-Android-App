package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ClimateZone
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.colorForMode
import com.example.ui.theme.themedShape
import com.example.viewmodel.HvacViewModel

/**
 * The heat pump pop-up, rebuilt 2026-10-21 (review step 4, option B).
 *
 * The old one led with the head's own reading under "Current Temp", so it said 72° while the card
 * behind it said 67° — the head measures its return air inside a casing high on the wall. It had
 * 28 dp +/- buttons, switches scaled to 0.7, 7–9 sp labels, and scrolled on both tablets.
 *
 * Now: the room reading leads (head reading underneath, small), one large stepper edits the
 * schedule block that is in force, and Off sits with Heat/Cool/Dry instead of a separate power
 * button. On a wide screen it is two columns — temperatures, mode and switches on the left; the
 * other schedule blocks, fan and vane on the right — so neither tablet scrolls. Narrower than
 * [TWO_COLUMN_MIN_WIDTH] it stacks.
 */
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
    val isDebouncing by viewModel.isDebouncing.collectAsStateWithLifecycle()
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

    // Which block the steppers edit follows the ZONE's own mode whenever the head is running, and
    // only falls back to the house mode when it is not. Following the house mode unconditionally
    // once pushed a heat setpoint at a head that was manually cooling.
    val zoneMode = zone.currentHvacMode.lowercase()
    val zoneIsRunning = !com.example.model.isNotReporting(zoneMode) && zoneMode != "off"
    val activeModeForPresets = when {
        zoneIsRunning -> zoneMode
        globalHvacMode.lowercase() == "off" -> lastNonOffHvacMode
        else -> globalHvacMode
    }
    val coolBlock = activeModeForPresets.lowercase() == "cool"
    val blockPresets = if (coolBlock) zone.presetsCool else zone.presetsHeat
    val familyLabel = if (coolBlock) "Cool" else "Heat"
    // The stepper always writes the helper, but only pushes the value straight at the head when
    // that head is running. Writing to a sleeping head is dropped today, and would power it on if
    // the Airstage integration's turn-on-before-set-temp option were ever enabled.
    val blocks = listOf(
        ScheduleBlock("Day", blockPresets.day, blockPresets.dayValue, activeScheduleState == "Day"),
        ScheduleBlock("Night", blockPresets.night, blockPresets.nightValue, activeScheduleState == "Night"),
        ScheduleBlock("Away", blockPresets.away, blockPresets.awayValue, activeScheduleState == "Away")
    )
    val activeBlock = blocks.firstOrNull { it.active }
    val stepBlock: (ScheduleBlock, Double) -> Unit = { block, delta ->
        block.value?.let { current ->
            val newVal = current + delta
            if (block.active) {
                // The zone's own temperature: tracked, so the card and this pop-up show
                // "Setting to" (or "Updating" when the zone is off) until it is confirmed.
                viewModel.changeZoneTarget(zone, block.entityId, "${zone.name} ${block.label} $familyLabel", newVal, pushToHead = zoneIsRunning)
            } else {
                viewModel.setPresetTemperature(block.entityId, newVal, "${zone.name} ${block.label} $familyLabel")
            }
        }
    }
    val pendingSetpoints by viewModel.pendingSetpoints.collectAsStateWithLifecycle()

    val onModeTap: (String) -> Unit = { mode ->
        if (mode == "off") {
            if (zoneMode != "off") {
                viewModel.toggleZonePower(zone.climateEntityId, zone.currentHvacMode, globalHvacMode, zone.name)
            }
        } else {
            // Check before sending: a refusal after the fact is far worse feedback than not
            // sending at all. The n8n watchdog remains the real enforcement.
            val conflict = viewModel.detectModeConflict(zone.key, mode)
            if (conflict != null) pendingModeConflict = conflict
            else viewModel.setZoneHvacMode(zone.climateEntityId, mode, zone.name)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
    CompositionLocalProvider(LocalPendingSetpoints provides pendingSetpoints) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .dialogInteractionReporter(onInteraction)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            val twoColumns = maxWidth >= TWO_COLUMN_MIN_WIDTH
            // The A9 is about 640 dp tall: at full spacing the vane row fell just below the edge.
            val compact = maxHeight < 700.dp
            CompositionLocalProvider(LocalPopupCompact provides compact) {
            Card(
                modifier = Modifier
                    .widthIn(max = if (twoColumns) 920.dp else 520.dp)
                    .fillMaxWidth(if (twoColumns) 0.9f else 0.94f)
                    .heightIn(max = maxHeight * 0.96f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("zone_detail_popup_${zone.key}"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = themedShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 14.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    PopupHeader(zone, activeColor, onDismiss)

                    val left: @Composable ColumnScope.() -> Unit = {
                        ReadingAndTarget(zone, activeBlock, activeColor, familyLabel) { delta ->
                            activeBlock?.let { stepBlock(it, delta) }
                        }
                        ModeRow(zone, theme, onModeTap)
                        HouseConflictNote(zoneMode, globalHvacMode.lowercase())
                        SwitchRow(zone, viewModel)
                    }
                    val right: @Composable ColumnScope.() -> Unit = {
                        ScheduleCard(blocks, familyLabel, activeColor, zone.key, stepBlock)
                        ChoiceCard("Fan", zone.fanOptions, zone.fanMode, activeColor, "fan_${zone.key}") {
                            viewModel.selectFanMode(zone.fanEntityId, it, zone.name)
                        }
                        ChoiceCard("Vane", zone.vaneOptions, zone.vaneMode, activeColor, "vane_${zone.key}") {
                            viewModel.selectVaneMode(zone.tiltEntityId, it, zone.name)
                        }
                    }
                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp), content = left)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp), content = right)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            left()
                            right()
                        }
                    }

                    AnimatedVisibility(
                        visible = bannerVisible || isDebouncing,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FeedbackLine(
                            text = if (isDebouncing) "Waiting a moment in case you tap again…" else bannerMessage ?: "Done",
                            color = if (isDebouncing) activeColor else Color(0xFF10B981)
                        )
                    }
                }
            }
            }
        }
    }
    }
}

/** Shorter screens get tighter spacing so the whole pop-up fits without scrolling. */
private val LocalPopupCompact = compositionLocalOf { false }

/** Wide enough for two columns of controls: both wall tablets and the unfolded Fold. */
private val TWO_COLUMN_MIN_WIDTH: Dp = 640.dp

private data class ScheduleBlock(val label: String, val entityId: String, val value: Double?, val active: Boolean)

private fun plainStatus(zone: ClimateZone): String {
    val mode = zone.currentHvacMode.lowercase()
    val doing = when (zone.statusLabel) {
        "RUNNING" -> when (mode) {
            "cool" -> "Cooling"
            "dry" -> "Drying"
            else -> "Heating"
        }
        "AT TARGET" -> "${mode.replaceFirstChar { it.uppercase() }} · at target"
        "OFF" -> "Off"
        "SPLIT" -> "The two heads are doing different things"
        "UNAVAILABLE" -> "Not reporting"
        else -> "${mode.replaceFirstChar { it.uppercase() }} · idle"
    }
    val who = when {
        zone.overrideOn -> "held"
        zone.autoOn -> "on schedule"
        else -> "manual"
    }
    return "$doing · $who"
}

@Composable
private fun PopupHeader(zone: ClimateZone, activeColor: Color, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(44.dp).background(activeColor.copy(alpha = 0.15f), themedShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(popupZoneIcon(zone.key), contentDescription = null, tint = activeColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(zone.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(plainStatus(zone), fontSize = 14.sp, color = activeColor)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(themedShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onDismiss)
                .testTag("zone_popup_close"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

private fun popupZoneIcon(key: String): ImageVector = when (key) {
    "main_level" -> Icons.Default.Weekend
    "bedroom_1", "bedroom_2" -> Icons.Default.Bed
    "basement" -> Icons.Default.AcUnit
    else -> Icons.Default.Person
}

/** The room reading (what the card shows) beside the big stepper for the block in force. */
@Composable
private fun ReadingAndTarget(
    zone: ClimateZone,
    activeBlock: ScheduleBlock?,
    activeColor: Color,
    familyLabel: String,
    onStep: (Double) -> Unit
) {
    val room = zone.roomTemp ?: zone.headTemp ?: zone.currentTemp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(themedShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), themedShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Room", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
            Text(room?.let { "${it.toInt()}°" } ?: "--°", fontSize = 52.sp, fontWeight = FontWeight.Light, color = Color.White)
            val extras = buildList {
                if (zone.roomTemp != null) add("head ${zone.headTemp?.toInt()?.let { "$it°" } ?: "--"}")
                zone.roomHumidity?.let { add("${it.toInt()}% humidity") }
            }
            if (extras.isNotEmpty()) {
                Text(extras.joinToString(" · "), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                activeBlock?.let { "${it.label} target" } ?: "Set to",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeBlock != null) StepButton(Icons.Default.Remove, "Lower ${activeBlock.label} target", 56.dp, "zone_target_down_${zone.key}") { onStep(-1.0) }
                Text(
                    (activeBlock?.value ?: zone.targetTemp)?.let { "${it.toInt()}°" } ?: "--°",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 92.dp)
                )
                if (activeBlock != null) StepButton(Icons.Default.Add, "Raise ${activeBlock.label} target", 56.dp, "zone_target_up_${zone.key}") { onStep(1.0) }
            }
            // The schedule number and what the head is actually holding can differ (a manual
            // change, or a head that is off); say so rather than show one and imply the other.
            val live = zone.targetTemp
            if (LocalPendingSetpoints.current.containsKey(zone.key)) {
                SetpointStatusLine(zone.key, fontSize = 14.sp)
            } else if (activeBlock?.value != null && live != null && live.toInt() != activeBlock.value.toInt()) {
                Text("head set to ${live.toInt()}°", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            } else if (activeBlock != null) {
                Text("$familyLabel schedule", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, size: Dp, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(themedShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), themedShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
private fun ModeRow(zone: ClimateZone, theme: com.example.ui.theme.HvacThemeColors, onModeTap: (String) -> Unit) {
    val current = zone.currentHvacMode.lowercase()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("heat", Icons.Default.Whatshot, theme.heatColor),
            Triple("cool", Icons.Default.AcUnit, theme.coolColor),
            Triple("dry", Icons.Default.Air, theme.dryColor),
            Triple("off", Icons.Default.PowerSettingsNew, Color(0xFF94A3B8))
        ).forEach { (mode, icon, color) ->
            val selected = current == mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(themedShape(12.dp))
                    .background(if (selected) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                    .border(
                        BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f)),
                        themedShape(12.dp)
                    )
                    .clickable { onModeTap(mode) }
                    .testTag("zone_mode_btn_${zone.key}_$mode"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) color else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.replaceFirstChar { it.uppercase() },
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * A multi-split shares one outdoor unit, so a head asking for cool while the house is heating (or
 * the reverse) is a conflict the equipment cannot serve.
 */
@Composable
private fun HouseConflictNote(zoneMode: String, houseMode: String) {
    val coolFamily = setOf("cool", "dry")
    val conflicts = houseMode != "off" && zoneMode != "off" &&
        ((zoneMode == "heat" && houseMode in coolFamily) || (zoneMode in coolFamily && houseMode == "heat"))
    if (!conflicts) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(themedShape(10.dp))
            .background(Color(0xFFF59E0B).copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
        Text(
            "This room is on $zoneMode while the house is on $houseMode. The outdoor unit can only do one at a time.",
            fontSize = 13.sp,
            color = Color(0xFFF59E0B),
            lineHeight = 17.sp
        )
    }
}

/** The zone's two helpers: follow the house schedule, and hold a manual setting. */
@Composable
private fun SwitchRow(zone: ClimateZone, viewModel: HvacViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchTile(
            title = "Auto",
            subtitle = "Follows the schedule",
            checked = zone.autoOn,
            color = Color(0xFF10B981),
            tag = "zone_auto_${zone.key}",
            modifier = Modifier.weight(1f)
        ) { viewModel.toggleInputBoolean(zone.autoEntityId, zone.autoOn, "${zone.name} Auto") }
        SwitchTile(
            title = "Override",
            subtitle = "Holds your setting",
            checked = zone.overrideOn,
            color = Color(0xFFF59E0B),
            tag = "zone_override_${zone.key}",
            modifier = Modifier.weight(1f)
        ) { viewModel.toggleInputBoolean(zone.overrideEntityId, zone.overrideOn, "${zone.name} Override") }
    }
}

@Composable
private fun SwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    color: Color,
    tag: String,
    modifier: Modifier,
    onToggle: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(themedShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), themedShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = color, checkedTrackColor = color.copy(alpha = 0.3f))
        )
    }
}

/**
 * Day, Night and Away for the family in force. The block in force is edited by the big stepper,
 * so here it is a plain reading marked "now"; the other two get their own steppers.
 */
@Composable
private fun ScheduleCard(
    blocks: List<ScheduleBlock>,
    familyLabel: String,
    activeColor: Color,
    zoneKey: String,
    onStep: (ScheduleBlock, Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(themedShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), themedShape(14.dp))
            .padding(horizontal = 14.dp, vertical = if (LocalPopupCompact.current) 8.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("$familyLabel schedule", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
        blocks.forEach { block ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = if (LocalPopupCompact.current) 40.dp else 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    block.label,
                    fontSize = 16.sp,
                    fontWeight = if (block.active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (block.active) activeColor else Color.White
                )
                if (block.active) {
                    Spacer(Modifier.width(8.dp))
                    Text("now", fontSize = 13.sp, color = activeColor.copy(alpha = 0.8f))
                }
                Spacer(Modifier.weight(1f))
                if (!block.active) {
                    StepButton(Icons.Default.Remove, "Lower ${block.label}", if (LocalPopupCompact.current) 40.dp else 44.dp, "preset_decrease_${block.label.lowercase()}_$zoneKey") { onStep(block, -1.0) }
                }
                Text(
                    block.value?.let { "${it.toInt()}°" } ?: "--°",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (block.active) activeColor else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp)
                )
                if (!block.active) {
                    StepButton(Icons.Default.Add, "Raise ${block.label}", if (LocalPopupCompact.current) 40.dp else 44.dp, "preset_increase_${block.label.lowercase()}_$zoneKey") { onStep(block, 1.0) }
                }
            }
        }
    }
}

/** Fan speed or vane position: every option visible as a wrapping row of chips, no sideways scroll. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceCard(
    title: String,
    options: List<String>,
    selected: String,
    activeColor: Color,
    tag: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(themedShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), themedShape(14.dp))
            .padding(horizontal = 14.dp, vertical = if (LocalPopupCompact.current) 8.dp else 10.dp)
    ) {
        Text(title, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(if (LocalPopupCompact.current) 6.dp else 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val isSelected = option.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .heightIn(min = if (LocalPopupCompact.current) 36.dp else 40.dp)
                        .clip(themedShape(10.dp))
                        .background(if (isSelected) activeColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            BorderStroke(1.dp, if (isSelected) activeColor.copy(alpha = 0.8f) else Color.Transparent),
                            themedShape(10.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp)
                        .testTag("${tag}_${option.lowercase().replace(' ', '_')}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option.replace("Vertical Swing", "Swing"),
                        fontSize = 14.sp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackLine(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(themedShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), themedShape(12.dp))
            .padding(horizontal = 14.dp, vertical = if (LocalPopupCompact.current) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, fontSize = 14.sp, color = Color.White)
    }
}
