package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun AlarmSection(viewModel: HvacViewModel, modifier: Modifier = Modifier) {
    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val accent = phaseColor(alarm.phase)

    Column(modifier = modifier.fillMaxWidth()) {
        AlarmStatusCard(alarm = alarm, accent = accent, onShowKeypad = { viewModel.openAlarmKeypad() })

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
            border = BorderStroke(1.dp, hvacBorderAlphaColor()),
            shape = hvacCardShape(14)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SectionHeader("SENSORS")
                Spacer(Modifier.height(9.dp))

                val sensors = layoutConfig.alarm?.sensors.orEmpty()
                if (sensors.isEmpty()) {
                    Text(
                        "No sensors configured yet. They are added in layout_config.json once " +
                            "the alarm is set up in Home Assistant.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    sensors.forEach { sensor ->
                        val node = entityStates[sensor.entityId]
                        val kind = com.example.model.alarmSensorKind(
                            deviceClass = node?.getStringAttribute("device_class"),
                            configuredType = sensor.type
                        )
                        val label = com.example.model.alarmSensorStatusLabel(kind, node?.state)
                        AlarmSensorRow(
                            name = sensor.name,
                            kind = kind,
                            statusText = label,
                            statusColor = when (label) {
                                "UNKNOWN" -> Color.White.copy(alpha = 0.35f)
                                // Amber means "something is happening here", not "something is
                                // wrong" — an occupied room is perfectly normal while disarmed.
                                "OPEN", "DETECTED" -> Color(0xFFF59E0B)
                                else -> LocalHvacTheme.current.ecoColor
                            }
                        )
                    }
                }

                if (alarm.openSensors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Blocking arming: " + alarm.openSensors.joinToString(", "),
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmStatusCard(alarm: AlarmState, accent: Color, onShowKeypad: () -> Unit) {
    val remaining = rememberSecondsRemaining(alarm)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("alarm_status_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = hvacCardShape(14)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("ALARM STATUS")
            Spacer(Modifier.height(10.dp))

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
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        alarm.headline,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    if (remaining != null) {
                        Text(
                            "$remaining seconds remaining",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else if (alarm.phase == AlarmPhase.UNAVAILABLE) {
                        Text(
                            if (alarm.entityId.isEmpty()) "No alarm entity configured"
                            else "Cannot reach ${alarm.entityId}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    } else if (!alarm.changedBy.isNullOrBlank()) {
                        Text(
                            "Last changed by ${alarm.changedBy}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // The keypad is the only place a code is ever entered, so every action here simply
            // opens it rather than arming directly. That also keeps one code path to test.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedControlButton(
                    label = "KEYPAD",
                    icon = Icons.Default.Lock,
                    color = accent,
                    isSelected = true,
                    contentDescription = "Open the alarm keypad",
                    testTagId = "alarm_show_keypad_btn",
                    onClick = onShowKeypad
                )
            }
        }
    }
}

@Composable
private fun AlarmSensorRow(
    name: String,
    kind: com.example.model.AlarmSensorKind,
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (kind) {
                com.example.model.AlarmSensorKind.MOTION -> Icons.Default.DirectionsRun
                com.example.model.AlarmSensorKind.OCCUPANCY -> Icons.Default.Person
                com.example.model.AlarmSensorKind.CONTACT -> Icons.Default.DoorFront
            },
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            name.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )
        Text(
            statusText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            color = statusColor
        )
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
@Composable
fun AlarmKeypadDialog(viewModel: HvacViewModel) {
    val visible by viewModel.showAlarmKeypad.collectAsStateWithLifecycle()
    if (!visible) return

    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val feedback by viewModel.alarmFeedback.collectAsStateWithLifecycle()
    val accent = phaseColor(alarm.phase)
    val remaining = rememberSecondsRemaining(alarm)

    var code by remember { mutableStateOf("") }

    // The keypad closes itself after a minute of nobody touching it, rather than sitting open on
    // a wall panel indefinitely with a half-typed code on screen. Every key press restarts the
    // minute. Closing it leaves you on the alarm tab, and the dashboard's own idle timer then
    // takes over from there (see the alarm handling in HvacDashboard).
    var lastKeyAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastKeyAt) {
        kotlinx.coroutines.delay(60_000)
        viewModel.dismissAlarmKeypad()
    }

    Dialog(
        onDismissRequest = { viewModel.dismissAlarmKeypad() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp).testTag("alarm_keypad_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12161C)),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            shape = hvacCardShape(18)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("ALARM", modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close the keypad",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { viewModel.dismissAlarmKeypad() }
                            .testTag("alarm_keypad_close")
                    )
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
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        color = accent
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Masked. The digits are never echoed back, on a screen that hangs on a wall.
                Text(
                    text = if (code.isEmpty()) "• • • •" else "•".repeat(code.length),
                    fontSize = 24.sp,
                    letterSpacing = 6.sp,
                    color = if (code.isEmpty()) Color.White.copy(alpha = 0.2f) else Color.White,
                    modifier = Modifier.testTag("alarm_code_display")
                )

                Spacer(Modifier.height(14.dp))

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
                                onPress = {
                                    lastKeyAt = System.currentTimeMillis()
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

                if (!feedback.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        feedback!!,
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )
                }

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
                                        lastKeyAt = System.currentTimeMillis()
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
                            label = "DISARM",
                            icon = Icons.Default.LockOpen,
                            color = LocalHvacTheme.current.ecoColor,
                            isSelected = false,
                            contentDescription = "Disarm the alarm",
                            testTagId = "alarm_disarm_btn",
                            onClick = {
                                lastKeyAt = System.currentTimeMillis()
                                    viewModel.submitAlarmCommand("alarm_disarm", code)
                                code = ""
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier = Modifier, onPress: () -> Unit) {
    val isDigit = key.length == 1 && key[0].isDigit()
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(CircleShapeIfDigit(isDigit))
            .background(Color.White.copy(alpha = if (isDigit) 0.05f else 0.02f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
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
                modifier = Modifier.size(18.dp)
            )
            "clear" -> Text(
                "CLR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            else -> Text(
                key,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/** Digits read better as circles; the two action keys as rounded rectangles. */
private fun CircleShapeIfDigit(isDigit: Boolean) =
    if (isDigit) CircleShape else RoundedCornerShape(14.dp)
