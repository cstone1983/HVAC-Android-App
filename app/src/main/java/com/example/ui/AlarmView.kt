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

@Composable
fun AlarmSection(viewModel: HvacViewModel, modifier: Modifier = Modifier) {
    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val accent = phaseColor(alarm.phase)

    Column(modifier = modifier.fillMaxWidth()) {
        AlarmStatusCard(
            alarm = alarm,
            accent = accent,
            onShowKeypad = { viewModel.openAlarmKeypad() },
            // Only reachable when the panel wants no code; submitAlarmCommand omits the code
            // field entirely when it is blank, which is what Alarmo expects.
            onDirectCommand = { service -> viewModel.submitAlarmCommand(service, "") }
        )

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
private fun AlarmStatusCard(
    alarm: AlarmState,
    accent: Color,
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

            // Arming and disarming are treated separately, because this panel treats them
            // separately: arming needs no code, disarming needs the numeric one.
            //
            // So arming is offered here as one tap per supported mode, and disarming always goes
            // to the keypad — there is nowhere else to type a code, and routing it here would
            // only produce a call the panel rejects. If the panel is ever reconfigured to demand
            // a code for arming too, requiresCodeToArm flips and arming joins it on the keypad.
            val canArmDirectly = alarm.isPresent &&
                alarm.phase == AlarmPhase.DISARMED &&
                !alarm.requiresCodeToArm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canArmDirectly) {
                    // Exactly the modes the panel reports it supports — Alarmo reports
                    // supported_features 14 here, which is away and night only.
                    alarm.armOptions.forEach { option ->
                        SegmentedControlButton(
                            label = option.label,
                            icon = Icons.Default.Lock,
                            color = accent,
                            isSelected = false,
                            contentDescription = "Arm ${option.label.lowercase()}",
                            testTagId = "alarm_arm_${option.label.lowercase()}_btn",
                            onClick = { onDirectCommand(option.service) }
                        )
                    }
                }
                // Cancelling a countdown *is* disarming, so it goes to the keypad like any
                // other disarm and the code is still required. Only the wording changes:
                // "DISARM" is what you want on an armed house, "CANCEL" is what you are
                // looking for when the clock is running and you have ten seconds to find it.
                val counting = alarm.phase == AlarmPhase.ARMING ||
                    alarm.phase == AlarmPhase.PENDING
                SegmentedControlButton(
                    label = when {
                        counting -> "CANCEL"
                        canArmDirectly -> "KEYPAD"
                        else -> "DISARM"
                    },
                    icon = if (canArmDirectly) Icons.Default.Lock else Icons.Default.LockOpen,
                    color = accent,
                    isSelected = !canArmDirectly,
                    contentDescription = when {
                        counting -> "Open the keypad to cancel the countdown"
                        canArmDirectly -> "Open the alarm keypad"
                        else -> "Open the keypad to disarm"
                    },
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
                                label = "DISARM",
                                icon = Icons.Default.LockOpen,
                                color = LocalHvacTheme.current.ecoColor,
                                isSelected = false,
                                contentDescription = "Disarm the alarm",
                                testTagId = "alarm_disarm_btn",
                                onClick = {
                                    lastKeyAt.set(System.currentTimeMillis())
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
    if (isDigit) CircleShape else RoundedCornerShape(14.dp)

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
 */
@Composable
fun HomeAlarmCard(viewModel: HvacViewModel, modifier: Modifier = Modifier) {
    val alarm by viewModel.alarmState.collectAsStateWithLifecycle()
    val theme = LocalHvacTheme.current
    val accent = phaseColor(alarm.phase)
    val secondsLeft = rememberSecondsRemaining(alarm)
    val counting = alarm.phase == AlarmPhase.ARMING || alarm.phase == AlarmPhase.PENDING

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
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
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
