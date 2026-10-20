package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CoverControl
import com.example.model.LightControl
import com.example.model.SwitchControl
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel

/**
 * The power tab, rebuilt 2026-10-18 (option A of three mockups, with B's "all off").
 *
 * Even tiles grouped by area, two areas side by side on a tablet so the whole house fits one
 * screen. Tap a light or switch to flip it; hold it for the brightness/details pop-up. A garage
 * door shows the one button that makes sense right now, and how long it has been open. A door
 * driven by a relay (the Workshop) cannot report whether it is open, so it says so and offers a
 * single Press.
 *
 * Areas come from `area` on each device in layout_config.json, in the order of `powerAreas`.
 * They used to be guessed from words in the entity id, which put the Workshop "inside".
 */
@Composable
fun PowerPage(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    onLightDetails: (String) -> Unit,
    onSwitchDetails: (String) -> Unit
) {
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()

    val all: List<Pair<String, Any>> =
        state.lights.map { (it.area ?: guessArea(it.entityId, garage = false)) to it } +
            state.switches.map { (it.area ?: guessArea(it.entityId, garage = false)) to it } +
            state.covers.map { (it.area ?: "Garage") to it }
    val configured = layoutConfig.powerAreas ?: listOf("Inside", "Outside", "Garage", "Pool")
    val areas = (configured + all.map { it.first }).distinct()
        .map { area -> area to all.filter { it.first == area }.map { it.second } }
        .filter { it.second.isNotEmpty() }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("power_page")) {
        // Two areas side by side on the A8; one per row with four across on the A9 and the Fold,
        // which is what lets Pool fit on the A9 without scrolling.
        val perRow = if (maxWidth >= 1000.dp) 2 else 1
        val columns = when {
            perRow == 2 -> 3
            maxWidth >= 700.dp -> 4
            maxWidth >= 480.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            layoutConfig.powerQuickActions?.takeIf { it.isNotEmpty() }?.let { actions ->
                PowerQuickActions(actions, columns = if (perRow == 2) 6 else columns, viewModel = viewModel)
            }
            areas.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (area, items) ->
                        PowerArea(
                            area = area,
                            items = items,
                            columns = columns,
                            viewModel = viewModel,
                            onLightDetails = onLightDetails,
                            onSwitchDetails = onSwitchDetails,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * The QUICK row: whole-house scripts such as Goodnight and the TV sleep timer. A script that is
 * running (the sleep timer counting down its 20 minutes) says so, and tapping it again stops it.
 */
@Composable
private fun PowerQuickActions(
    actions: List<com.example.model.QuickActionConfig>,
    columns: Int,
    viewModel: HvacViewModel
) {
    val states by viewModel.entityStates.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<com.example.model.QuickActionConfig?>(null) }
    pending?.let { action ->
        ConfirmDialog(
            title = "${action.label}?",
            message = "This runs ${action.label} for the whole house.",
            confirmLabel = action.label,
            destructive = false,
            onConfirm = {
                viewModel.callDynamicEntityService(action.domain, action.service, action.entityId ?: "", feedbackMessage = action.label)
            },
            onDismiss = { pending = null }
        )
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QUICK", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.take(columns).forEach { action ->
                val running = action.entityId?.startsWith("script.") == true &&
                    states[action.entityId]?.state == "on"
                PowerTile(
                    name = action.label,
                    icon = getIconByName(action.icon),
                    on = running,
                    status = if (running) "Running · tap to stop" else "Tap to start",
                    onTap = {
                        when {
                            running -> viewModel.callDynamicEntityService("script", "turn_off", action.entityId!!, feedbackMessage = "${action.label} stopped")
                            action.confirm == true -> pending = action
                            else -> viewModel.callDynamicEntityService(action.domain, action.service, action.entityId ?: "", feedbackMessage = action.label)
                        }
                    },
                    onHold = {},
                    modifier = Modifier.weight(1f).testTag("power_quick_${action.label.lowercase().replace(' ', '_')}")
                )
            }
            repeat((columns - actions.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The old rule, kept only for a device with no `area` in the config. */
private fun guessArea(entityId: String, garage: Boolean): String = when {
    garage -> "Garage"
    entityId.contains("pool") -> "Pool"
    entityId.contains("exterior") || entityId.contains("porch") -> "Outside"
    else -> "Inside"
}

private val OnColor = Color(0xFFF0A92A)
private val OpenColor = Color(0xFFEF4444)
private val ClosedColor = Color(0xFF3CCF91)

@Composable
private fun PowerArea(
    area: String,
    items: List<Any>,
    columns: Int,
    viewModel: HvacViewModel,
    onLightDetails: (String) -> Unit,
    onSwitchDetails: (String) -> Unit,
    modifier: Modifier
) {
    val lightsOn = items.filterIsInstance<LightControl>().filter { it.isOn }
    // The pool pump and the like are marked out of "all off" in the config: a group button
    // meant for lights should never stop the pump.
    val switchesOn = items.filterIsInstance<SwitchControl>().filter { it.isOn && it.includeInAllOff }
    val onCount = items.count { (it is LightControl && it.isOn) || (it is SwitchControl && it.isOn) }
    val openCount = items.count { it is CoverControl && !it.entityId.startsWith("switch.") && it.state.lowercase() in setOf("open", "opening") }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                area.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    openCount > 0 -> "$openCount open"
                    onCount > 0 -> "$onCount on"
                    items.all { it is CoverControl } -> "all closed"
                    else -> "all off"
                },
                fontSize = 11.sp,
                color = when {
                    openCount > 0 -> OpenColor
                    onCount > 0 -> OnColor
                    else -> Color.White.copy(alpha = 0.4f)
                }
            )
            Spacer(Modifier.weight(1f))
            if (lightsOn.isNotEmpty() || switchesOn.isNotEmpty()) {
                Text(
                    "All off",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7FB3EC),
                    modifier = Modifier
                        .clip(com.example.ui.theme.themedShape(6.dp))
                        .clickable { viewModel.turnOffAll(area, lightsOn, switchesOn) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("power_all_off_${area.lowercase()}")
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        val cell = Modifier.weight(1f)
                        when (item) {
                            is LightControl -> PowerTile(
                                name = item.name,
                                icon = Icons.Default.Lightbulb,
                                on = item.isOn,
                                status = if (!item.isOn) "Off"
                                    else item.brightness?.let { "On · ${(it * 100 / 255).coerceIn(1, 100)}%" } ?: "On",
                                onTap = { viewModel.toggleLight(item.entityId, item.isOn, item.name) },
                                onHold = { onLightDetails(item.entityId) },
                                modifier = cell.testTag("light_card_${item.entityId}")
                            )
                            is SwitchControl -> PowerTile(
                                name = item.name,
                                icon = Icons.Default.PowerSettingsNew,
                                on = item.isOn,
                                status = if (item.isOn) "On" else "Off",
                                onTap = { viewModel.toggleSwitch(item.entityId, item.isOn, item.name) },
                                onHold = { onSwitchDetails(item.entityId) },
                                modifier = cell.testTag("switch_card_${item.entityId}")
                            )
                            is CoverControl -> DoorTile(item, area, viewModel, cell.testTag("cover_card_${item.entityId}"))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PowerTile(
    name: String,
    icon: ImageVector,
    on: Boolean,
    status: String,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(com.example.ui.theme.themedShape(10.dp))
            .background(if (on) OnColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (on) OnColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.07f), com.example.ui.theme.themedShape(10.dp))
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (on) OnColor else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, fontSize = 11.sp, color = if (on) OnColor else Color.White.copy(alpha = 0.45f), maxLines = 1)
        }
    }
}

@Composable
private fun DoorTile(door: CoverControl, area: String, viewModel: HvacViewModel, modifier: Modifier) {
    // Under the GARAGE heading, "Garage South" only needs to say "South" — and the full name
    // was being cut off beside its button on the A8.
    val shownName = door.name.removePrefix("$area ").removePrefix(area.lowercase() + " ")
        .replaceFirstChar { it.uppercase() }
    // A relay pulses the opener; it has no idea where the door is.
    val relay = door.entityId.startsWith("switch.")
    val s = door.state.lowercase()
    val open = !relay && (s == "open" || s == "opening")
    val tint = when {
        relay -> Color.White.copy(alpha = 0.5f)
        open -> OpenColor
        else -> ClosedColor
    }
    val status = when {
        relay -> "Door button"
        s == "opening" -> "Opening…"
        s == "closing" -> "Closing…"
        s == "open" -> door.lastChanged?.let { since(it) }?.let { "Open since $it" } ?: "Open"
        s == "closed" -> "Closed"
        else -> s.replaceFirstChar { it.uppercase() }
    }
    val action = when {
        relay -> "Press"
        open -> "Close"
        else -> "Open"
    }
    // Opening, or pressing a button whose result the app cannot see, asks first. Closing does
    // not: it is the safe direction, and the opener's own sensors guard the doorway.
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        ConfirmDialog(
            title = if (relay) "Press the ${door.name} button?" else "Open ${door.name}?",
            message = if (relay) "The app can't see whether this door is open or closed, so check it after pressing."
                else "The door opens straight away.",
            confirmLabel = action,
            onConfirm = { viewModel.controlCover(door.entityId, "open", door.name) },
            onDismiss = { confirm = false }
        )
    }

    Row(
        modifier = modifier
            .height(54.dp)
            .clip(com.example.ui.theme.themedShape(10.dp))
            .background(if (open) OpenColor.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (open) OpenColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f), com.example.ui.theme.themedShape(10.dp))
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Garage, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(shownName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, fontSize = 11.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            action,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (open) OpenColor else Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(56.dp)
                .clip(com.example.ui.theme.themedShape(7.dp))
                .border(1.dp, if (open) OpenColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f), com.example.ui.theme.themedShape(7.dp))
                .clickable {
                    if (open) viewModel.controlCover(door.entityId, "close", door.name) else confirm = true
                }
                .padding(vertical = 6.dp)
        )
    }
}

/** "9:59" today, "Sat 9:59" earlier, from Home Assistant's last_changed. */
private fun since(iso: String): String? = try {
    val trimmed = iso.replace(Regex("\\.\\d+"), "")
    val then = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(trimmed) ?: throw IllegalArgumentException()
    val sameDay = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).let { it.format(then) == it.format(java.util.Date()) }
    java.text.SimpleDateFormat(if (sameDay) "h:mm" else "EEE h:mm", java.util.Locale.US).format(then)
} catch (e: Exception) {
    null
}
