package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ClimateZone
import com.example.model.HouseConditionStatus
import com.example.model.NightDelayConfig
import com.example.model.NightDelayScope
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.themedShape
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel

/** Amber: something deliberately not running as usual. Matches the mock-up agreed 2026-11-06. */
internal val ConditionOnColor = Color(0xFFF0A24B)
private val NightColor = Color(0xFF5AA9FF)

/** Any touch inside a dialog window counts as use, so the idle timer does not close it mid-tap. */
private fun Modifier.touchesClock(clock: InteractionClock): Modifier = pointerInput(clock) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            clock.touch()
        }
    }
}

/**
 * The Heat pump settings page, opened from the gear beside HEAT PUMPS on the home screen.
 *
 * Big full-width rows, so they are easy to hit on the wall tablets. The rows come from
 * `hvacSettings` in layout_config.json (wording, minutes, which helpers), and from each zone's
 * `headChoice` for Living room only. Closes itself after `popupTimeoutSeconds` untouched, like the
 * zone pop-up.
 */
@Composable
fun HeatPumpSettingsDialog(state: HvacUiState.Success, viewModel: HvacViewModel, onDismiss: () -> Unit) {
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val cfg = layoutConfig.hvacSettings ?: return
    val clock = LocalInteractionClock.current
    val timeoutMs = (layoutConfig.popupTimeoutSeconds ?: 20).coerceAtLeast(5) * 1000L

    // Minutes left on the timers, and the idle close.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        clock.touch()
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            nowMs = System.currentTimeMillis()
            if (clock.idleMillis() >= timeoutMs) { onDismiss(); break }
        }
    }

    var askDuration by remember { mutableStateOf(false) }
    var chosenMinutes by remember { mutableStateOf<Int?>(null) }

    val houseOff = state.globalSettings.globalHvacMode.lowercase() == "off"
    val conditions = state.houseConditions.conditions.filter { it.config.name != null }
    val stayOn = conditions.filter { !it.config.timed }
    val timed = conditions.filter { it.config.timed }
    val headChoiceZones = state.zones.filter { it.headChoice != null }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(themedShape(16.dp))
                .background(Color(0xFF101A2B))
                .border(1.dp, Color.White.copy(alpha = 0.12f), themedShape(16.dp))
                .touchesClock(clock)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("hp_settings")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cfg.title ?: "HEAT PUMP SETTINGS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .testTag("hp_settings_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            if (headChoiceZones.isNotEmpty() || stayOn.isNotEmpty()) {
                SectionLabel(cfg.stayOnHeading ?: "STAYS ON UNTIL YOU TURN IT OFF")
                headChoiceZones.forEach { zone -> HeadChoiceRow(zone, viewModel) }
                stayOn.forEach { c ->
                    SwitchRow(
                        title = c.config.name.orEmpty(),
                        description = c.config.description,
                        on = c.on,
                        tag = "hp_cond_${c.config.entityId}",
                        onToggle = { viewModel.setHouseCondition(c.config, it) }
                    )
                }
            }

            if (timed.isNotEmpty() || cfg.nightDelay != null) {
                SectionLabel(cfg.timedHeading ?: "TURNS ITSELF OFF")
                timed.forEach { c -> TimedRow(c, state, houseOff, nowMs, viewModel) }
                cfg.nightDelay?.let { n ->
                    val status = state.houseConditions.nightDelay
                    val running = status?.running == true
                    // Heat pumps only has already happened: the heat pumps are on Night while the
                    // house is still on Day. END puts them back.
                    val early = state.houseConditions.conditions.firstOrNull { it.config.entityId == n.earlyNightEntityId }
                    val earlyOn = !running && early?.on == true &&
                        state.globalSettings.houseSchedule.equals(early.config.fromSchedule ?: "day", ignoreCase = true)
                    if (earlyOn && early != null) {
                        ActionRow(
                            title = n.earlyNightTitle ?: "Heat pumps on Night",
                            description = n.earlyNightDescription,
                            active = true,
                            accent = NightColor,
                            action = "END",
                            enabled = true,
                            tag = "hp_early_night_end",
                            onAction = { viewModel.setHouseCondition(early.config, false, n.earlyNightTitle) }
                        )
                        return@let
                    }
                    val allowed = n.onlyFromSchedule == null ||
                        state.globalSettings.houseSchedule.equals(n.onlyFromSchedule, ignoreCase = true)
                    val title = if (running) {
                        "${n.name ?: "Night early"} · in ${minutesLeft(status?.endsAt, nowMs)} min"
                    } else n.name ?: "Night early"
                    val desc = when {
                        running -> status?.scope?.let { s -> n.scopes.firstOrNull { it.option == s }?.title ?: s }
                        !allowed -> "The house is on ${state.globalSettings.houseSchedule} now."
                        else -> n.description
                    }
                    ActionRow(
                        title = title,
                        description = desc,
                        active = running,
                        accent = NightColor,
                        action = if (running) "CANCEL" else (n.actionLabel ?: "START"),
                        enabled = running || allowed,
                        tag = "hp_night_delay",
                        onAction = { if (running) viewModel.cancelNightDelay(n) else askDuration = true }
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }

    val night = cfg.nightDelay
    if (night != null && askDuration) {
        ChoiceDialog(
            title = night.durationQuestion ?: "Start Night in…",
            choices = night.durations.map { m -> (if (m % 60 == 0) "${m / 60} hour${if (m >= 120) "s" else ""}" else "$m minutes") to null },
            clock = clock,
            onDismiss = { askDuration = false },
            onChoose = { i -> chosenMinutes = night.durations[i]; askDuration = false }
        )
    }
    val minutes = chosenMinutes
    if (night != null && minutes != null) {
        ChoiceDialog(
            title = night.scopeQuestion ?: "What should go to Night?",
            choices = night.scopes.map { it.title to it.description },
            clock = clock,
            onDismiss = { chosenMinutes = null },
            onChoose = { i -> viewModel.startNightDelay(night, minutes, night.scopes[i]); chosenMinutes = null }
        )
    }
}

private fun minutesLeft(endsAt: Long?, nowMs: Long): Long =
    endsAt?.let { ((it - nowMs + 59_999L) / 60_000L).coerceAtLeast(0L) } ?: 0L

private fun clockTime(ms: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(ms))

private fun degrees(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun HeadChoiceRow(zone: ClimateZone, viewModel: HvacViewModel) {
    val choice = zone.headChoice ?: return
    SwitchRow(
        title = choice.switchOption,
        description = choice.description,
        on = zone.headChoiceSwitchOn,
        tag = "hp_head_choice_${zone.key}",
        onToggle = { viewModel.setHeadChoice(zone, it) }
    )
}

/** A whole row that flips a switch: the row is the tap target, the switch shows the state. */
@Composable
private fun SwitchRow(title: String, description: String?, on: Boolean, tag: String, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(themedShape(10.dp))
            .background(if (on) ConditionOnColor.copy(alpha = 0.08f) else Color.Transparent)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag)
    ) {
        RowText(title, description, on, ConditionOnColor, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        BigSwitch(on)
    }
}

/** Boost and anything else on a timer: a button to start it, CANCEL while it runs. */
@Composable
private fun TimedRow(c: HouseConditionStatus, state: HvacUiState.Success, houseOff: Boolean, nowMs: Long, viewModel: HvacViewModel) {
    val theme = LocalHvacTheme.current
    val cfg = c.config
    val minutes = cfg.durationMinutes ?: 20
    val deg = degrees(c.boostDegrees ?: cfg.boostDefault)
    val covered = state.zones.filter { cfg.covers(it.key) }
    // Nothing to boost while the house is off, or while every zone it covers is held by hand.
    val blockedBy = when {
        c.on -> null
        houseOff -> "Not while the house is Off."
        cfg.boostDegreesEntityId != null && state.globalSettings.globalHvacMode.lowercase() == "dry" -> "Not in Dry mode."
        covered.isNotEmpty() && covered.all { it.overrideOn || !it.autoOn } ->
            "Not while ${covered.joinToString(" and ") { it.name }} ${if (covered.size == 1) "is" else "are"} on hold or Override."
        else -> null
    }
    val title = if (c.on) "${cfg.name} · ${minutesLeft(c.endsAt, nowMs)} min left" else cfg.name.orEmpty()
    val desc = when {
        blockedBy != null -> blockedBy
        c.on && c.endsAt != null -> "$deg° ${if (state.globalSettings.globalHvacMode.lowercase() == "cool") "cooler" else "warmer"} until ${clockTime(c.endsAt)}, then back to the schedule."
        else -> cfg.description?.replace("{deg}", deg)?.replace("{min}", minutes.toString())
            ?.replace("{warmer}", if (state.globalSettings.globalHvacMode.lowercase() == "cool") "cooler" else "warmer")
    }
    ActionRow(
        title = title,
        description = desc,
        active = c.on,
        accent = theme.heatColor,
        action = if (c.on) "CANCEL" else (cfg.actionLabel ?: "START"),
        enabled = blockedBy == null,
        tag = "hp_cond_${cfg.entityId}",
        onAction = { viewModel.setTimedCondition(cfg, !c.on) }
    )
}

@Composable
private fun ActionRow(
    title: String,
    description: String?,
    active: Boolean,
    accent: Color,
    action: String,
    enabled: Boolean,
    tag: String,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(themedShape(10.dp))
            .background(if (active) ConditionOnColor.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        RowText(title, description, active, ConditionOnColor, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 96.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(themedShape(10.dp))
                .background(if (active) Color.White.copy(alpha = 0.06f) else accent.copy(alpha = 0.15f))
                .border(1.dp, if (active) Color.White.copy(alpha = 0.2f) else accent.copy(alpha = 0.5f), themedShape(10.dp))
                .clickable(enabled = enabled, onClick = onAction)
                .padding(horizontal = 14.dp)
                .testTag(tag)
        ) {
            Text(
                action,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = if (active) Color.White.copy(alpha = 0.7f) else accent
            )
        }
    }
}

@Composable
private fun RowText(title: String, description: String?, on: Boolean, onColor: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) onColor else Color.White)
        if (!description.isNullOrBlank()) {
            Text(description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun BigSwitch(on: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 30.dp)
            .background(if (on) ConditionOnColor else Color.White.copy(alpha = 0.18f), RoundedCornerShape(15.dp))
    ) {
        Box(
            modifier = Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(4.dp)
                .size(22.dp)
                .background(if (on) Color.White else Color(0xFFCBD5E1), CircleShape)
        )
    }
}

/** One question, a big button per answer. Cancel by tapping outside or CANCEL. */
@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Pair<String, String?>>,
    clock: InteractionClock,
    onDismiss: () -> Unit,
    onChoose: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .clip(themedShape(16.dp))
                .background(Color(0xFF16202C))
                .border(1.dp, Color.White.copy(alpha = 0.12f), themedShape(16.dp))
                .touchesClock(clock)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            choices.forEachIndexed { i, (label, desc) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(themedShape(12.dp))
                        .border(1.dp, NightColor.copy(alpha = 0.45f), themedShape(12.dp))
                        .clickable { onChoose(i) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("hp_choice_$i"),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (!desc.isNullOrBlank()) Text(desc, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            Text(
                "CANCEL",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(themedShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}
