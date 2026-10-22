package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AlarmPhase
import com.example.model.AlarmState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.hvacBorderAlphaColor
import com.example.ui.theme.hvacCardBgColor
import com.example.ui.theme.hvacCardShape
import com.example.viewmodel.HvacViewModel

/**
 * Alarm tab and keypad.
 *
 * FRAMEWORK ONLY at the time of writing. The Alarmo integration did not exist in Home Assistant
 * when this was built, so none of it has been exercised against a real panel. Everything reads
 * from a configurable entity id and falls back to "no panel found" rather than rendering a
 * plausible DISARMED from nothing — on an alarm screen, "I cannot see it" and "it is off" must
 * never look the same.
 *
 * Styling deliberately reuses the dashboard's own primitives (hvacCardBgColor, hvacCardShape,
 * SegmentedControlButton, the 10sp letter-spaced section headers) so it reads as part of the app
 * rather than a bolted-on screen.
 */

/** Colour for a phase, taken from the app's theme rather than invented per-screen. */
@Composable
private fun phaseColor(phase: AlarmPhase): Color {
    val theme = LocalHvacTheme.current
    return when (phase) {
        AlarmPhase.DISARMED -> theme.ecoColor
        AlarmPhase.ARMING -> Color(0xFFF59E0B)
        AlarmPhase.PENDING -> Color(0xFFEF4444)
        AlarmPhase.TRIGGERED -> Color(0xFFEF4444)
        AlarmPhase.ARMED -> theme.coolColor
        AlarmPhase.UNAVAILABLE -> Color.White.copy(alpha = 0.35f)
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = Color.White.copy(alpha = 0.5f),
        letterSpacing = 1.3.sp,
        modifier = modifier
    )
}

/**
 * A live seconds counter for the entry/exit delay.
 *
 * Ticks locally rather than waiting for Home Assistant to push a new state every second, because
 * the panel does not do that — it reports the delay once and the countdown is ours to render.
 */
@Composable
private fun rememberSecondsRemaining(alarm: AlarmState): Int? {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alarm.phase, alarm.phaseSinceEpochMs) {
        while (alarm.phase == AlarmPhase.PENDING || alarm.phase == AlarmPhase.ARMING) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(250)
        }
    }
    return alarm.secondsRemaining(nowMs)
}

// -------------------------------------------------------------------------------------------
// Header strip
// -------------------------------------------------------------------------------------------

/**
 * Replaces the dashboard's usual header (humidity, presence, room temperatures, weather) while
 * the alarm tab is open.
 *
 * None of that says anything about the alarm, and it was pushing the thing you actually came to
 * this tab for below the fold. This answers the two questions instead: is it armed, and is
 * anything currently open or detecting.
 */
@Composable
fun AlarmSummaryStrip(viewModel: HvacViewModel, modifier: Modifier = Modifier) {
    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val accent = phaseColor(alarm.phase)

    var clear = 0
    var active = 0
    var unknown = 0
    layoutConfig.alarm?.sensors.orEmpty().forEach { sensor ->
        val node = entityStates[sensor.entityId]
        val kind = com.example.model.alarmSensorKind(
            deviceClass = node?.getStringAttribute("device_class"),
            configuredType = sensor.type
        )
        when (com.example.model.alarmSensorStatusLabel(kind, node?.state)) {
            "OPEN", "DETECTED" -> active++
            "UNKNOWN" -> unknown++
            else -> clear++
        }
    }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(
            label = "ALARM",
            value = when (alarm.phase) {
                AlarmPhase.DISARMED -> "DISARMED"
                AlarmPhase.ARMED -> "ARMED"
                AlarmPhase.ARMING -> "ARMING"
                AlarmPhase.PENDING -> "ENTRY"
                AlarmPhase.TRIGGERED -> "TRIGGERED"
                AlarmPhase.UNAVAILABLE -> "--"
            },
            valueColor = accent,
            sub = when (alarm.phase) {
                AlarmPhase.ARMED -> alarm.armMode?.removePrefix("armed_")?.uppercase() ?: ""
                AlarmPhase.UNAVAILABLE -> "no panel found"
                else -> ""
            },
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "SENSORS",
            value = if (active > 0) "$active" else "ALL CLEAR",
            valueColor = if (active > 0) Color(0xFFF59E0B) else LocalHvacTheme.current.ecoColor,
            sub = buildString {
                if (active > 0) append("open or detected · ")
                append("$clear clear")
                // Only mentioned when there are any, so the common case stays quiet — but never
                // folded into "clear", which would overstate what is actually known.
                if (unknown > 0) append(" · $unknown unknown")
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    valueColor: Color,
    sub: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(14)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionHeader(label)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Tab content
// -------------------------------------------------------------------------------------------

/**
 * The alarm tab, rebuilt 2026-10-22 (review step 4: option A with B's activity list).
 *
 * The status says itself once, in words, with who changed it and when. Disarmed, the two arming
 * modes are the buttons — the KEYPAD button beside them opened a dialog offering the same two
 * modes again. Armed or counting down, one large button opens the keypad. Sensors are tiles with
 * when each last changed, anything open or detecting first. Underneath, the last week of
 * changes: who armed and disarmed it, entry delays with what opened, and triggers.
 */
@Composable
fun AlarmSection(viewModel: HvacViewModel, modifier: Modifier = Modifier) {
    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val history by viewModel.alarmHistory.collectAsStateWithLifecycle()
    val accent = phaseColor(alarm.phase)
    val sensors = layoutConfig.alarm?.sensors.orEmpty()
    val names = sensors.associate { it.entityId to it.name }

    // Re-read the activity whenever the alarm moves, so the list never lags the status above it.
    LaunchedEffect(alarm.rawState) { viewModel.refreshAlarmHistory() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AlarmStatusCard(
            alarm = alarm,
            accent = accent,
            changedAt = com.example.ui.parseHaTimestamp(entityStates[alarm.entityId]?.last_changed),
            onShowKeypad = { viewModel.openAlarmKeypad() },
            // Only reachable when the panel wants no code; submitAlarmCommand omits the code
            // field entirely when it is blank, which is what Alarmo expects.
            onDirectCommand = { service -> viewModel.submitAlarmCommand(service, "") }
        )

        if (sensors.isEmpty()) {
            Text(
                "No sensors configured yet. They are added in layout_config.json once the alarm " +
                    "is set up in Home Assistant.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        } else {
            val tiles = sensors.map { sensor ->
                val node = entityStates[sensor.entityId]
                val kind = com.example.model.alarmSensorKind(
                    deviceClass = node?.getStringAttribute("device_class"),
                    configuredType = sensor.type
                )
                SensorTileState(
                    name = sensor.name,
                    kind = kind,
                    label = com.example.model.alarmSensorStatusLabel(kind, node?.state),
                    since = com.example.ui.parseHaTimestamp(node?.last_changed)
                )
            }
            val active = tiles.count { it.label == "OPEN" || it.label == "DETECTED" }
            Text(
                if (active == 0) "Sensors · all closed or clear" else "Sensors · $active open or detecting",
                fontSize = 13.sp,
                color = if (active == 0) Color.White.copy(alpha = 0.55f) else Color(0xFFF59E0B),
                modifier = Modifier.padding(start = 2.dp)
            )
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val perRow = if (maxWidth < 560.dp) 2 else 3
                // Open doors and detected motion first; otherwise the configured order.
                val ordered = tiles.sortedBy { if (it.label == "OPEN" || it.label == "DETECTED") 0 else 1 }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.chunked(perRow).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { SensorTile(it, Modifier.weight(1f)) }
                            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        if (alarm.openSensors.isNotEmpty()) {
            Text(
                "Blocking arming: " + alarm.openSensors.joinToString(", "),
                fontSize = 13.sp,
                color = Color(0xFFF59E0B)
            )
        }

        AlarmActivityCard(
            events = history,
            sensorNames = names,
            lastTriggered = entityStates[alarm.entityId]?.getStringAttribute("last_triggered")
        )
    }
}

private data class SensorTileState(
    val name: String,
    val kind: com.example.model.AlarmSensorKind,
    val label: String,
    val since: Long?
)

@Composable
private fun AlarmStatusCard(
    alarm: AlarmState,
    accent: Color,
    changedAt: Long?,
    onShowKeypad: () -> Unit,
    onDirectCommand: (String) -> Unit
) {
    val remaining = rememberSecondsRemaining(alarm)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("alarm_status_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = hvacCardShape(14)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (alarm.phase) {
                        AlarmPhase.DISARMED -> Icons.Default.LockOpen
                        AlarmPhase.TRIGGERED, AlarmPhase.PENDING -> Icons.Default.Warning
                        AlarmPhase.UNAVAILABLE -> Icons.Default.Shield
                        else -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        alarmHeadline(alarm),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    val sub = when {
                        remaining != null -> "$remaining seconds left"
                        alarm.phase == AlarmPhase.UNAVAILABLE ->
                            if (alarm.entityId.isEmpty()) "No alarm entity configured" else "Can't reach ${alarm.entityId}"
                        else -> listOfNotNull(
                            alarm.changedBy?.takeIf { it.isNotBlank() }?.let { "by ${friendlyName(it)}" },
                            changedAt?.let { "at ${com.example.ui.friendlyWhen(it)}" }
                        ).joinToString(" ").ifBlank { null }
                    }
                    sub?.let { Text(it, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f)) }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Arming needs no code on this panel and disarming needs the numeric one, so arming is
            // one tap per supported mode and disarming always goes to the keypad. If the panel is
            // ever set to demand a code for arming too, requiresCodeToArm flips and arming moves
            // onto the keypad with it.
            val canArmDirectly = alarm.isPresent && alarm.phase == AlarmPhase.DISARMED && !alarm.requiresCodeToArm
            val counting = alarm.phase == AlarmPhase.ARMING || alarm.phase == AlarmPhase.PENDING
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    canArmDirectly -> alarm.armOptions.forEach { option ->
                        AlarmBigButton(
                            label = "Arm ${option.label.lowercase()}",
                            icon = if (option.label.equals("night", true)) Icons.Default.NightsStay else Icons.Default.Shield,
                            color = phaseColor(AlarmPhase.ARMED),
                            tag = "alarm_arm_${option.label.lowercase()}_btn",
                            modifier = Modifier.weight(1f)
                        ) { onDirectCommand(option.service) }
                    }
                    alarm.isPresent -> AlarmBigButton(
                        // "Cancel" is what you're looking for with ten seconds on the clock; it
                        // is still a disarm, so it still goes through the keypad and the code.
                        label = when {
                            counting -> "Cancel"
                            alarm.phase == AlarmPhase.DISARMED -> "Open keypad"
                            else -> "Disarm"
                        },
                        icon = Icons.Default.LockOpen,
                        color = accent,
                        tag = "alarm_show_keypad_btn",
                        modifier = Modifier.weight(1f)
                    ) { onShowKeypad() }
                }
            }
        }
    }
}

/** "Disarmed", "Armed night", "Entry delay"… in words rather than the panel's capitals. */
private fun alarmHeadline(alarm: AlarmState): String = when (alarm.phase) {
    AlarmPhase.DISARMED -> "Disarmed"
    AlarmPhase.ARMED -> alarm.armMode?.removePrefix("armed_")?.replace('_', ' ')
        ?.let { "Armed $it" } ?: "Armed"
    AlarmPhase.ARMING -> "Arming"
    AlarmPhase.PENDING -> "Entry delay"
    AlarmPhase.TRIGGERED -> "Alarm triggered"
    AlarmPhase.UNAVAILABLE -> "Not available"
}

/** Home Assistant reports the n8n user as "N8n". */
private fun friendlyName(who: String): String = if (who.equals("n8n", ignoreCase = true)) "n8n" else who

@Composable
private fun AlarmBigButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(com.example.ui.theme.themedShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), com.example.ui.theme.themedShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun SensorTile(s: SensorTileState, modifier: Modifier) {
    val active = s.label == "OPEN" || s.label == "DETECTED"
    val statusColor = when (s.label) {
        "UNKNOWN" -> Color.White.copy(alpha = 0.4f)
        // Amber means "something is happening here", not "something is wrong" — an occupied
        // room is normal while disarmed.
        "OPEN", "DETECTED" -> Color(0xFFF59E0B)
        else -> LocalHvacTheme.current.ecoColor
    }
    val motion = s.kind != com.example.model.AlarmSensorKind.CONTACT
    val whenText = s.since?.let { t ->
        val at = com.example.ui.friendlyWhen(t)
        when {
            s.label == "OPEN" -> "open since $at"
            s.label == "DETECTED" -> "since $at"
            motion -> "last motion $at"
            s.label == "CLOSED" -> "closed at $at"
            else -> at
        }
    }
    Column(
        modifier = modifier
            .clip(com.example.ui.theme.themedShape(12.dp))
            .background(if (active) Color(0xFFF59E0B).copy(alpha = 0.08f) else hvacCardBgColor())
            .border(
                BorderStroke(1.dp, if (active) Color(0xFFF59E0B).copy(alpha = 0.5f) else hvacBorderAlphaColor()),
                com.example.ui.theme.themedShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (s.kind) {
                    com.example.model.AlarmSensorKind.MOTION -> Icons.Default.DirectionsRun
                    com.example.model.AlarmSensorKind.OCCUPANCY -> Icons.Default.Person
                    com.example.model.AlarmSensorKind.CONTACT -> Icons.Default.DoorFront
                },
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(s.name, fontSize = 15.sp, color = Color.White, maxLines = 1)
        }
        Text(
            when (s.label) {
                "OPEN" -> "Open"
                "CLOSED" -> "Closed"
                "DETECTED" -> "Motion"
                "CLEAR" -> "Clear"
                else -> "Unknown"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor
        )
        whenText?.let { Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1) }
    }
}

/**
 * The last week of changes. "Arming" is left out — it is always followed a minute later by the
 * armed state it was heading for, and listing both doubled every entry.
 */
@Composable
private fun AlarmActivityCard(events: List<com.example.model.AlarmEvent>, sensorNames: Map<String, String>, lastTriggered: String?) {
    val theme = LocalHvacTheme.current
    val shown = events.filter { it.state != "arming" && it.state != "unavailable" && it.state != "unknown" }.take(8)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("alarm_activity"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(14)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Recent activity", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
            if (shown.isEmpty()) {
                Text("Nothing in the last week.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f))
            }
            shown.forEach { e ->
                val by = e.changedBy?.takeIf { it.isNotBlank() }?.let { " by ${friendlyName(it)}" } ?: ""
                val opened = e.openSensors.joinToString(", ") { sensorNames[it] ?: it }
                val (text, dot) = when {
                    e.state == "disarmed" -> "Disarmed$by" to theme.ecoColor
                    e.state.startsWith("armed_") -> "Armed ${e.state.removePrefix("armed_").replace('_', ' ')}$by" to theme.coolColor
                    e.state == "pending" -> (if (opened.isNotEmpty()) "Entry delay: $opened opened" else "Entry delay") to Color(0xFFF59E0B)
                    e.state == "triggered" -> (if (opened.isNotEmpty()) "Alarm went off: $opened" else "Alarm went off") to Color(0xFFEF4444)
                    else -> e.state.replaceFirstChar { it.uppercase() } to Color.White.copy(alpha = 0.4f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(text, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                    Text(com.example.ui.friendlyWhen(e.epochMs), fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
                }
            }
            // Alarmo's own record of the last trigger, which can be older than the week above.
            lastTriggered?.takeIf { it.isNotBlank() }?.let { raw ->
                val t = try {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(raw)?.time
                } catch (e: Exception) { null }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last went off: ${t?.let { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(it)) } ?: raw}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Keypad popup
// -------------------------------------------------------------------------------------------

/**
 * The keypad, shown over whatever tab is open.
 *
 * Raised automatically on entry delay and on trigger (see HvacViewModel.updateAlarmState), and
 * openable by hand from the tab for testing. The code typed here is passed straight to the
 * service call and is never stored, cached or logged anywhere.
 */
/**
 * Room left below the keypad for a dock that does not declare itself.
 *
 * Sized against the One UI taskbar, which is the one actually in the way here. Only the
 * shortfall is added: where the system reports a real inset of its own, this tops it up to
 * this figure rather than stacking on top of it.
 */
private val TASKBAR_CLEARANCE = 72.dp

@Composable
fun AlarmKeypadDialog(viewModel: HvacViewModel) {
    val visible by viewModel.showAlarmKeypad.collectAsStateWithLifecycle()
    if (!visible) return

    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val feedback by viewModel.alarmFeedback.collectAsStateWithLifecycle()
    val busy by viewModel.alarmBusy.collectAsStateWithLifecycle()
    val accent = phaseColor(alarm.phase)
    val remaining = rememberSecondsRemaining(alarm)

    var code by remember { mutableStateOf("") }

    // Disarming needs the PIN; arming, on this panel, does not. Showing a dead keypad on a
    // disarmed house puts twelve keys between you and the only two things you came here to
    // do, so in that state the arm buttons become the whole dialog and get the room to be
    // pressed without looking.
    //
    // Declared out here because the keypad scrolls and the action row does not, so the two
    // live in different columns and both need to know.
    val needsPin = alarm.phase != AlarmPhase.DISARMED || alarm.requiresCodeToArm

    // The keypad closes itself after a minute of nobody touching it, rather than sitting open on
    // a wall panel indefinitely with a half-typed code on screen. Every key press restarts the
    // minute. Closing it leaves you on the alarm tab, and the dashboard's own idle timer then
    // takes over from there (see the alarm handling in HvacDashboard).
    // Deliberately not Compose state, and not keyed into the effect.
    //
    // It used to be both, which meant every single keypress wrote state, cancelled a
    // coroutine, started another one to sleep for a minute, and recomposed the whole
    // dialog — twelve keys, each with its own clip, background and border — purely to
    // restart an idle timer. On the wall panels, which are budget tablets, that is what
    // made typing a PIN quickly feel like it was dragging behind your finger.
    //
    // Now a keypress writes a plain long and composition never hears about it. One effect
    // wakes every few seconds and asks how long it has been. A timeout that fires up to
    // five seconds late costs nothing; a keypad that lags costs the thing it is for.
    val lastKeyAt = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            if (System.currentTimeMillis() - lastKeyAt.get() >= 60_000) {
                viewModel.dismissAlarmKeypad()
                break
            }
        }
    }

    Dialog(
        onDismissRequest = { viewModel.dismissAlarmKeypad() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Kept clear of the system bars — and of Samsung’s taskbar, which is not one.
        //
        // The keys growing to 65dp pushed the bottom of this dialog under the taskbar, where
        // it covered part of the disarm key. safeDrawingPadding alone did not fix it: the
        // One UI taskbar is a separate overlay window and does not always report itself as a
        // navigation-bar inset, so as far as the dialog was concerned that space was free.
        //
        // Hence the floor. Whatever the system admits to, at least a taskbar’s worth of room
        // is left at the bottom; where the insets are honest and larger, they win. Costing a
        // little space on devices that did not need it is the right way round, because the
        // other failure is invisible until someone is standing at the panel with a countdown
        // running and the one key they need is behind the dock.
        //
        // The scroll underneath is the backstop for whatever is left: the layout has to
        // survive being given less room than it wants, rather than putting a key out of
        // reach.
        val density = LocalDensity.current
        val systemBottom = with(density) {
            WindowInsets.safeDrawing.getBottom(density).toDp()
        }

        // And an explicit ceiling on the card, because the padding above cannot be trusted
        // to bound it.
        //
        // fillMaxSize inside a Dialog measures the dialog’s own window, not the screen, and
        // that window sizes itself to its content. So when the content is tall enough, the
        // Box grows with it and the padding ends up insetting a window that is already
        // bigger than the display — the card runs off the bottom and the padding changes
        // nothing.
        //
        // That is why this survived the first fix. The keypad I measured was the armed-house
        // one; a real alarm adds the headline and the countdown above the keys, roughly 70dp
        // more, which on the A9’s 601dp of height is exactly enough to tip it over. The
        // screen height is the one figure here that does not depend on how the Dialog
        // decided to size itself.
        val configuration = LocalConfiguration.current
        val maxCardHeight = (
            configuration.screenHeightDp.dp - TASKBAR_CLEARANCE - 24.dp
            ).coerceAtLeast(220.dp)

        // Keys sized to the screen rather than fixed.
        //
        // 65dp keys plus the chrome around them want about 586dp, and the A9 has 505dp to
        // give. Pinning the disarm key outside the scroll meant it was always reachable,
        // but the bottom row of the keypad then went below the fold instead — clear and
        // backspace, which is a keypad you cannot correct a typo on, during the one minute
        // you have to type a code correctly.
        //
        // So the keys give way instead. CHROME is measured, not guessed: on the A9 the
        // header, headline, counter, masked display, spacers, padding and the pinned action
        // came to 294dp of the card, leaving the rest for four rows. Shrinking the counter
        // on a short screen buys back enough to keep the keys above a fingertip.
        val compact = maxCardHeight < 560.dp
        val chromeHeight = if (compact) 268.dp else 294.dp
        val keyHeight = ((maxCardHeight - chromeHeight) / 4 - 8.dp)
            .coerceIn(46.dp, 65.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = (TASKBAR_CLEARANCE - systemBottom).coerceAtLeast(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .heightIn(max = maxCardHeight)
                    .testTag("alarm_keypad_dialog"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12161C)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                shape = hvacCardShape(18)
            ) {
                // Two columns, not one, and the split is the whole point.
                //
                // Everything used to sit in a single scrolling column with the disarm key last.
                // When the content outgrew the card — which it does during a real alarm, where
                // the headline and the counter add about 70dp above the keys — the overflow came
                // off the bottom, and the bottom is the one key that has to be reachable. Capping
                // the card’s height stopped it running under the taskbar and handed the same
                // problem to the scrollbar: the key was no longer off-screen, it was just below
                // the fold, which on a wall panel is the same thing.
                //
                // So the action row is pinned outside the scroll. The keypad above it can scroll
                // if a screen is too short; the button you came to press cannot be scrolled away.
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("ALARM", modifier = Modifier.weight(1f))
                            // 20 dp used to be the whole target, in the corner of a dialog
                            // people reach for in a hurry. The icon stays small; the box is 48.
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(com.example.ui.theme.themedShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { viewModel.dismissAlarmKeypad() }
                                    .testTag("alarm_keypad_close"),
                                contentAlignment = Alignment.Center
                            ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close the keypad",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(22.dp)
                            )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            alarm.headline,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            textAlign = TextAlign.Center
                        )
                        if (remaining != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "$remaining",
                                fontSize = if (compact) 30.sp else 44.sp,
                                fontWeight = FontWeight.Light,
                                color = accent
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // The digits only appear when there is actually a code to type.
                        //
                        if (needsPin) {
                            // Masked. The digits are never echoed back, on a screen that hangs on a wall.
                            MaskedCode(code = { code })

                            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

                            listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("clear", "0", "back")
                            ).forEach { rowKeys ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowKeys.forEach { key ->
                                        KeypadKey(
                                            key = key,
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight,
                                            onPress = {
                                                lastKeyAt.set(System.currentTimeMillis())
                                                when (key) {
                                                    "clear" -> code = ""
                                                    "back" -> code = code.dropLast(1)
                                                    // A sane upper bound so a stuck key cannot build a
                                                    // megabyte-long string.
                                                    else -> if (code.length < 12) code += key
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (!feedback.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                feedback!!,
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                textAlign = TextAlign.Center
                            )
                        }

                    }  // end of the scrolling area

                    Spacer(Modifier.height(12.dp))

                    // Disarm is offered whenever the panel is anything other than disarmed, because
                    // that is the action someone standing at a beeping panel wants. Arm options only
                    // appear when it is actually disarmed.
                    if (alarm.phase == AlarmPhase.DISARMED) {
                        val options = alarm.armOptions
                        if (options.isEmpty()) {
                            Text(
                                "This panel reports no arming modes.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        } else if (!needsPin) {
                            // No keypad above, so these are the dialog. Full width and tall enough to
                            // hit without aiming.
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                options.forEach { option ->
                                    Button(
                                        onClick = {
                                            lastKeyAt.set(System.currentTimeMillis())
                                            viewModel.submitAlarmCommand(option.service, code)
                                            code = ""
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .testTag("alarm_arm_${option.label.lowercase()}"),
                                        shape = hvacCardShape(14),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalHvacTheme.current.coolColor
                                                .copy(alpha = 0.18f),
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            LocalHvacTheme.current.coolColor.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (option.label == "NIGHT")
                                                Icons.Default.NightsStay else Icons.Default.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = LocalHvacTheme.current.coolColor
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "ARM ${option.label}",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                options.forEach { option ->
                                    SegmentedControlButton(
                                        label = option.label,
                                        icon = if (option.label == "NIGHT") Icons.Default.NightsStay
                                        else Icons.Default.Shield,
                                        color = LocalHvacTheme.current.coolColor,
                                        isSelected = false,
                                        contentDescription = "Arm ${option.label}",
                                        testTagId = "alarm_arm_${option.label.lowercase()}",
                                        onClick = {
                                            lastKeyAt.set(System.currentTimeMillis())
                                            viewModel.submitAlarmCommand(option.service, code)
                                            code = ""
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            SegmentedControlButton(
                                label = if (busy) "CHECKING…" else "DISARM",
                                icon = Icons.Default.LockOpen,
                                color = LocalHvacTheme.current.ecoColor,
                                isSelected = busy,
                                contentDescription = if (busy) "Checking the code"
                                else "Disarm the alarm",
                                testTagId = "alarm_disarm_btn",
                                onClick = {
                                    lastKeyAt.set(System.currentTimeMillis())
                                    // Ignored while one is already in flight. Pressing it
                                    // twice during the pause would send a second disarm
                                    // with an empty code, which Alarmo counts as a failed
                                    // attempt against you.
                                    if (!busy) {
                                        viewModel.submitAlarmCommand("alarm_disarm", code)
                                        code = ""
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The masked code.
 *
 * Takes a lambda rather than a String, which looks fussy and is the entire point. A String
 * parameter is read where the caller builds it, so every keystroke would subscribe the whole
 * dialog and recompose twelve keys in order to change four dots. Deferring the read moves
 * that subscription in here, where the only thing that actually changes lives.
 *
 * The digits are never echoed back — this is a screen that hangs on a wall.
 */
@Composable
private fun MaskedCode(code: () -> String) {
    val current = code()
    Text(
        text = if (current.isEmpty()) "• • • •" else "•".repeat(current.length),
        fontSize = 24.sp,
        letterSpacing = 6.sp,
        color = if (current.isEmpty()) Color.White.copy(alpha = 0.2f) else Color.White,
        modifier = Modifier.testTag("alarm_code_display")
    )
}

@Composable
/**
 * One key.
 *
 * Sized for a wall panel rather than a phone. 65dp against the 48dp minimum touch target,
 * because this gets used in the dark, in a hurry, by someone who has sixty seconds and is
 * still holding the shopping — and a mistyped digit on a keypad costs an entry delay, not a
 * backspace.
 *
 * The border carries most of that. It was 1dp at 7% white, which on a dark panel at arm’s
 * length is close to invisible: the keys read as a vague grid rather than as distinct
 * targets. At 2dp and 32% each key has an actual edge, which is what lets you aim without
 * looking carefully.
 */
private fun KeypadKey(
    key: String,
    modifier: Modifier = Modifier,
    height: Dp = 65.dp,
    onPress: () -> Unit
) {
    val isDigit = key.length == 1 && key[0].isDigit()
    Box(
        modifier = modifier
            .heightIn(min = height)
            .clip(CircleShapeIfDigit(isDigit))
            .background(Color.White.copy(alpha = if (isDigit) 0.05f else 0.02f))
            .border(
                BorderStroke(2.dp, Color.White.copy(alpha = if (isDigit) 0.32f else 0.20f)),
                CircleShapeIfDigit(isDigit)
            )
            .clickable(onClick = onPress)
            .testTag("alarm_key_$key"),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "back" -> Icon(
                Icons.Default.Backspace,
                contentDescription = "Delete last digit",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(22.dp)
            )
            "clear" -> Text(
                "CLR",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            else -> Text(
                key,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/** Digits read better as circles; the two action keys as rounded rectangles. */
private fun CircleShapeIfDigit(isDigit: Boolean) =
    if (isDigit) CircleShape else com.example.ui.theme.themedShape(14.dp)

// -------------------------------------------------------------------------------------------
// Home screen card
// -------------------------------------------------------------------------------------------

/**
 * The alarm, in the slot on the home screen where humidity used to sit.
 *
 * Humidity was a number nobody ever acted on. This is the one piece of state worth a glance on
 * the way out of the door, and arming is now two taps from the home screen rather than a trip
 * to another tab.
 *
 * It **reformats in place** rather than growing. While an exit or entry delay runs, the
 * countdown takes the headline and a single CANCEL key takes the arm buttons’ slot, so the
 * card never changes height. Swapping rather than hiding matters both ways: pressing arm again
 * mid-countdown does nothing useful, and a dead button during the one moment that counts
 * invites jabbing at it — but an empty space would waste the only moment the panel is worth
 * touching from the home screen at all.
 *
 * Everything shown is derived, never configured: the available modes come from the panel's own
 * `supported_features`, so a mode added or removed in Alarmo changes these buttons without an
 * app release.
 *
 * [slim] is the phone cover screen's one-line version: the same states and the same buttons,
 * laid out in a row — status on the left, keys on the right.
 */
@Composable
fun HomeAlarmCard(viewModel: HvacViewModel, modifier: Modifier = Modifier, slim: Boolean = false) {
    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val theme = LocalHvacTheme.current
    val accent = phaseColor(alarm.phase)
    val secondsLeft = rememberSecondsRemaining(alarm)
    val counting = alarm.phase == AlarmPhase.ARMING || alarm.phase == AlarmPhase.PENDING

    if (slim) {
        Card(
            modifier = modifier.testTag("home_alarm_card"),
            colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
            border = BorderStroke(1.dp, hvacBorderAlphaColor()),
            shape = hvacCardShape(12)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (alarm.phase == AlarmPhase.DISARMED) Icons.Default.LockOpen
                    else Icons.Default.Shield,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when {
                        counting -> "${secondsLeft ?: "--"}s " +
                            if (alarm.phase == AlarmPhase.ARMING) "EXIT NOW" else "DISARM NOW"
                        alarm.phase == AlarmPhase.DISARMED -> "DISARMED"
                        alarm.phase == AlarmPhase.ARMED ->
                            alarm.armMode?.removePrefix("armed_")?.uppercase() ?: "ARMED"
                        alarm.phase == AlarmPhase.TRIGGERED -> "TRIGGERED"
                        else -> "--"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                when {
                    // Same rules as the full card below: CANCEL during a countdown, the arm
                    // modes while disarmed, DISARM otherwise — and the code always typed.
                    counting -> AlarmChipButton(
                        label = "CANCEL",
                        accent = Color(0xFFEF4444),
                        modifier = Modifier.width(84.dp)
                    ) { viewModel.openAlarmKeypad() }
                    alarm.phase == AlarmPhase.DISARMED && alarm.armOptions.isNotEmpty() ->
                        alarm.armOptions.forEach { option ->
                            AlarmChipButton(
                                label = option.label,
                                accent = theme.coolColor,
                                modifier = Modifier.width(64.dp)
                            ) {
                                if (alarm.requiresCodeToArm) viewModel.openAlarmKeypad()
                                else viewModel.submitAlarmCommand(option.service, "")
                            }
                        }
                    alarm.isPresent -> AlarmChipButton(
                        label = "DISARM",
                        accent = Color(0xFFEF4444),
                        modifier = Modifier.width(84.dp)
                    ) { viewModel.openAlarmKeypad() }
                }
            }
        }
        return
    }

    Card(
        modifier = modifier.testTag("home_alarm_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(12)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (alarm.phase == AlarmPhase.DISARMED) Icons.Default.LockOpen
                    else Icons.Default.Shield,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "ALARM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.3.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Spacer(Modifier.height(6.dp))

            if (counting) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = secondsLeft?.toString() ?: "--",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        "s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 1.dp, bottom = 3.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (alarm.phase == AlarmPhase.ARMING) "ARMING — EXIT NOW"
                           else "ENTRY — DISARM NOW",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                // Takes the slot the arm buttons occupy the rest of the time, so the card
                // still does not change height — but the slot is no longer empty, because
                // this is the one thing worth pressing while the clock runs. It opens the
                // keypad like any other disarm: the code is still required.
                AlarmChipButton(
                    label = "CANCEL",
                    accent = Color(0xFFEF4444),
                    modifier = Modifier.fillMaxWidth()
                ) { viewModel.openAlarmKeypad() }
            } else {
                Text(
                    text = when (alarm.phase) {
                        AlarmPhase.DISARMED -> "DISARMED"
                        AlarmPhase.ARMED ->
                            alarm.armMode?.removePrefix("armed_")?.uppercase() ?: "ARMED"
                        AlarmPhase.TRIGGERED -> "TRIGGERED"
                        else -> "--"
                    },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                when {
                    alarm.phase == AlarmPhase.DISARMED && alarm.armOptions.isNotEmpty() -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            alarm.armOptions.forEach { option ->
                                AlarmChipButton(
                                    label = option.label,
                                    accent = theme.coolColor,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Arming may or may not need the code — that is an Alarmo
                                    // setting, separate from the one for disarming. Ask for the
                                    // keypad when it does rather than firing a call that will be
                                    // refused silently.
                                    if (alarm.requiresCodeToArm) viewModel.openAlarmKeypad()
                                    else viewModel.submitAlarmCommand(option.service, "")
                                }
                            }
                        }
                    }
                    // Disarming always goes through the keypad: the code is typed and passed
                    // straight to the service call, never held anywhere.
                    alarm.isPresent -> AlarmChipButton(
                        label = "DISARM",
                        accent = Color(0xFFEF4444),
                        modifier = Modifier.fillMaxWidth()
                    ) { viewModel.openAlarmKeypad() }
                }
            }
        }
    }
}

/** A compact arm/disarm key, sized to sit inside the home screen's narrow status column. */
@Composable
private fun AlarmChipButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(com.example.ui.theme.themedShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), com.example.ui.theme.themedShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            color = accent,
            maxLines = 1
        )
    }
}
