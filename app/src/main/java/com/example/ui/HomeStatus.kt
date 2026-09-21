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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.EntityState
import com.example.model.HomeStatusConfig
import com.example.model.HumiditySensorConfig
import com.example.model.QuickActionConfig
import com.example.model.RoomSensor
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.hvacBorderAlphaColor
import com.example.ui.theme.hvacCardBgColor
import com.example.ui.theme.hvacCardShape
import com.example.viewmodel.HvacViewModel

private val AlertRed = Color(0xFFEF4444)

/**
 * Compose Dialogs render in their own window, so touches inside them never reach the root
 * pointerInput that drives the kiosk idle timers. Applying this to a dialog's root keeps the
 * dashboard awake while someone is actually using the popup.
 */
fun Modifier.dialogInteractionReporter(onInteraction: () -> Unit): Modifier =
    this.pointerInput(onInteraction) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onInteraction()
            }
        }
    }

/** Reads an entity's state, or one of its attributes when `attribute` is set. */
internal fun readEntityValue(
    states: Map<String, EntityState>,
    entityId: String?,
    attribute: String? = null
): String? {
    if (entityId.isNullOrBlank()) return null
    val entity = states[entityId] ?: return null
    val raw = if (attribute.isNullOrBlank()) entity.state else entity.getStringAttribute(attribute)
    if (raw.isNullOrBlank()) return null
    if (raw.equals("unknown", true) || raw.equals("unavailable", true) || raw.equals("none", true)) return null
    return raw
}

private fun formatRounded(raw: String?): String? =
    raw?.toDoubleOrNull()?.let { "${it.toInt()}" } ?: raw

/**
 * Parses a Home Assistant timestamp to epoch millis.
 *
 * java.time needs API 26 and this module has no core library desugaring, so SimpleDateFormat
 * it is — but HA emits microseconds ("...:06.787957+00:00") and SimpleDateFormat would read
 * those six digits as milliseconds and land ~13 minutes late. Fractional seconds are
 * therefore truncated to three digits and the offset normalised before parsing.
 */
internal fun parseHaTimestamp(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        val match = Regex("^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d+))?\\s*(Z|[+-]\\d{2}:?\\d{2})?$")
            .find(raw.trim()) ?: return null
        val base = match.groupValues[1].replace(' ', 'T')
        val millis = match.groupValues[2].take(3).padEnd(3, '0')
        val offsetRaw = match.groupValues[3]
        val offset = when {
            offsetRaw.isEmpty() || offsetRaw == "Z" -> "+0000"
            else -> offsetRaw.replace(":", "")
        }
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
            .parse("$base.$millis$offset")?.time
    } catch (e: Exception) {
        null
    }
}

/** "4d 3h", "5h 12m", "42m", "just now" — coarse on purpose. */
internal fun formatElapsed(sinceMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val delta = nowMillis - sinceMillis
    if (delta < 60_000L) return "just now"
    val minutes = delta / 60_000L
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> "${days}d ${hours % 24}h"
        hours >= 1 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

/** Clock time, with the date added once the event is no longer today. */
private fun formatClock(millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "h:mm a" else "EEE h:mm a"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(java.util.Date(millis))
}

/**
 * The one line on the home screen that is normally absent. It surfaces, in priority order:
 * a door that is actually open, loss of connection, a failed or pending command. Everything
 * here was previously only visible inside the zone popup, or not at all.
 */
@Composable
fun HomeAlertStrip(
    viewModel: HvacViewModel,
    homeStatus: HomeStatusConfig?,
    modifier: Modifier = Modifier
) {
    val states by viewModel.entityStates.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val feedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val isDebouncing by viewModel.isDebouncing.collectAsStateWithLifecycle()

    val openDoors = homeStatus?.doorAlerts.orEmpty().filter { door ->
        val value = states[door.stateEntityId]?.state?.lowercase()
        value != null && door.openStates.orEmpty().any { it.equals(value, true) }
    }

    // The strip shows one alert, so the order decides what gets hidden — and it used to be wrong
    // in two ways. `openDoors` came first and is derived from the last-known state map, so a
    // disconnected panel went on asserting "GARAGE SOUTH OPEN" in red, at full confidence, from
    // cached data — and that assertion suppressed the OFFLINE banner that would have told you the
    // data was stale. It also outranked command feedback, so a failed write showed nothing at all
    // while the control sat lit on a mode that never applied.
    val feedbackText = feedback
    val feedbackFailed = !feedbackText.isNullOrBlank() &&
        (feedbackText.contains("fail", true) || feedbackText.contains("error", true))

    val alert: Triple<String, Color, androidx.compose.ui.graphics.vector.ImageVector>? = when {
        // Offline outranks every entity-derived claim. While the socket is down we cannot know
        // whether a door is still open, so report it as the last thing seen rather than as fact.
        isOffline -> Triple(
            if (openDoors.isEmpty()) "OFFLINE — SHOWING LAST KNOWN VALUES"
            else "OFFLINE — LAST SEEN " + openDoors.joinToString(" · ") { "${it.name.uppercase()} OPEN" },
            AlertRed,
            Icons.Default.CloudOff
        )
        // A failure the user just caused outranks a standing condition they can already see.
        feedbackFailed -> Triple(feedbackText!!.uppercase(), AlertRed, Icons.Default.Info)
        openDoors.isNotEmpty() -> Triple(
            openDoors.joinToString(" · ") { "${it.name.uppercase()} OPEN" },
            AlertRed,
            Icons.Default.Garage
        )
        !feedbackText.isNullOrBlank() -> Triple(
            feedbackText.uppercase(),
            Color(0xFF10B981),
            Icons.Default.Sync
        )
        isDebouncing -> Triple("APPLYING CHANGES…", Color(0xFFF59E0B), Icons.Default.Sync)
        else -> null
    }

    AnimatedVisibility(
        visible = alert != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val (text, tint, icon) = alert ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.11f))
                .border(BorderStroke(1.dp, tint.copy(alpha = 0.40f)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .testTag("home_alert_strip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Humidity for each configured room, side by side and separated by a slash — dining and
 * upstairs are different rooms and are never averaged into one number.
 */
@Composable
fun HumidityCard(
    viewModel: HvacViewModel,
    sensors: List<HumiditySensorConfig>,
    modifier: Modifier = Modifier
) {
    if (sensors.isEmpty()) return
    val states by viewModel.entityStates.collectAsStateWithLifecycle()
    val readings = sensors.map { it to formatRounded(readEntityValue(states, it.entityId, it.attribute)) }

    Card(
        modifier = modifier.testTag("humidity_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(12)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = Color(0xFF0EA5E9),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "HUMIDITY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.3.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                readings.forEachIndexed { index, (_, value) ->
                    if (index > 0) {
                        Text(
                            " / ",
                            fontSize = 17.sp,
                            color = Color.White.copy(alpha = 0.30f),
                            fontWeight = FontWeight.Light
                        )
                    }
                    Text(
                        text = value?.let { "$it%" } ?: "--%",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = readings.joinToString(" / ") { it.first.name.uppercase() },
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Who is home, as initials. Makes the Away automation legible instead of mysterious. */
@Composable
fun PresenceCard(
    viewModel: HvacViewModel,
    presenceEntityIds: List<String>,
    modifier: Modifier = Modifier,
    // Matches Home Assistant's recorder purge_keep_days; looking further back than the
    // recorder retains just returns "no change recorded" for everyone.
    historyWindowDays: Int = 30,
    lastInteractionTime: Long = 0L,
    popupTimeoutMillis: Long = 20_000L,
    onInteraction: () -> Unit = {}
) {
    if (presenceEntityIds.isEmpty()) return
    val states by viewModel.entityStates.collectAsStateWithLifecycle()
    val people = presenceEntityIds.mapNotNull { id ->
        val entity = states[id] ?: return@mapNotNull null
        val name = entity.getStringAttribute("friendly_name")
            ?: id.substringAfter('.').replace('_', ' ')
        PersonPresence(
            entityId = id,
            name = name,
            isHome = entity.state.equals("home", true),
            sinceMillis = parseHaTimestamp(entity.last_changed)
        )
    }
    if (people.isEmpty()) return
    val homeCount = people.count { it.isHome }

    var showDetails by remember { mutableStateOf(false) }

    // The kiosk idle timer has to reach this popup too, otherwise it is the one thing left
    // open on the wall after everything else has returned to the dashboard.
    LaunchedEffect(lastInteractionTime, showDetails) {
        if (showDetails && lastInteractionTime > 0) {
            kotlinx.coroutines.delay(popupTimeoutMillis)
            showDetails = false
        }
    }

    if (showDetails) {
        // last_changed resets on a Home Assistant restart, so the real transition times come
        // from recorder history, fetched once when the popup opens.
        var historySince by remember {
            mutableStateOf<Map<String, HvacViewModel.PresenceSince>>(emptyMap())
        }
        var historyLoaded by remember { mutableStateOf(false) }
        // Distinct from "loaded but empty": the query itself did not come back.
        var historyFailed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val fetched = viewModel.fetchPresenceSince(presenceEntityIds, historyWindowDays)
            historyFailed = fetched == null
            historySince = fetched.orEmpty()
            historyLoaded = true
        }
        PresenceDetailDialog(
            people = people.map { person ->
                val fromHistory = historySince[person.entityId]
                when {
                    // History is authoritative. last_changed only survives as a fallback when
                    // the history call itself failed, because a restart resets it and would
                    // otherwise report everyone as having moved at the same instant.
                    fromHistory != null -> person.copy(
                        sinceMillis = fromHistory.millis,
                        unknownDuration = fromHistory.millis == null
                    )
                    // Only claim "nothing changed in the window" when the window was actually
                    // read. If the query failed we know nothing about duration at all.
                    historyLoaded && !historyFailed ->
                        person.copy(sinceMillis = null, unknownDuration = true)
                    else -> person
                }
            },
            resolved = historyLoaded,
            historyUnavailable = historyFailed,
            historyWindowDays = historyWindowDays,
            onDismiss = { showDetails = false },
            onInteraction = onInteraction
        )
    }

    Card(
        modifier = modifier
            .clickable { showDetails = true }
            .testTag("presence_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(12)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                "HOME",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.3.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                people.take(6).forEach { person ->
                    val name = person.name
                    val isHome = person.isHome
                    val tint = if (isHome) Color(0xFF10B981) else Color.White.copy(alpha = 0.25f)
                    Box(
                        modifier = Modifier
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(if (isHome) tint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.03f))
                            .border(BorderStroke(1.5.dp, tint.copy(alpha = if (isHome) 0.75f else 0.20f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isHome) tint else Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$homeCount HOME · ${people.size - homeCount} AWAY",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    }
}

internal data class PersonPresence(
    val entityId: String,
    val name: String,
    val isHome: Boolean,
    /** When they arrived or left. Null when nothing reliable could be determined. */
    val sinceMillis: Long?,
    /** Recorder history held no transition, so the change predates what we can see. */
    val unknownDuration: Boolean = false
)

/**
 * Who is home, when each of them arrived or left, and how long it has been. This is the
 * answer to "why did the house go into Away mode" — presence drives that automation, and
 * until now nothing on the panel showed it.
 */
@Composable
private fun PresenceDetailDialog(
    people: List<PersonPresence>,
    resolved: Boolean,
    historyUnavailable: Boolean = false,
    historyWindowDays: Int = 30,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit = {}
) {
    val home = people.filter { it.isHome }.sortedByDescending { it.sinceMillis ?: 0L }
    val away = people.filterNot { it.isHome }.sortedByDescending { it.sinceMillis ?: 0L }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.85f)
                .padding(vertical = 12.dp)
                .dialogInteractionReporter(onInteraction)
                .testTag("presence_detail_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "WHO'S HOME",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        historyUnavailable -> "${home.size} HOME · ${away.size} AWAY · HISTORY UNAVAILABLE"
                        resolved -> "${home.size} HOME · ${away.size} AWAY"
                        else -> "${home.size} HOME · ${away.size} AWAY · CHECKING HISTORY…"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
                Spacer(Modifier.height(14.dp))

                listOf(true to home, false to away).forEach { (isHomeGroup, group) ->
                    if (group.isEmpty()) return@forEach
                    Text(
                        text = if (isHomeGroup) "HOME" else "AWAY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.3.sp,
                        color = if (isHomeGroup) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(7.dp))
                    group.forEach { person -> PresenceRow(person, historyWindowDays) }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun PresenceRow(person: PersonPresence, historyWindowDays: Int = 30) {
    val tint = if (person.isHome) Color(0xFF10B981) else Color.White.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (person.isHome) tint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                .border(BorderStroke(1.5.dp, tint.copy(alpha = if (person.isHome) 0.7f else 0.2f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = person.name.take(1).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (person.isHome) tint else Color.White.copy(alpha = 0.4f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val since = person.sinceMillis
            Text(
                text = when {
                    since != null && person.isHome -> "Arrived ${formatClock(since)}"
                    since != null -> "Left ${formatClock(since)}"
                    person.unknownDuration -> "No change in recorded history"
                    person.isHome -> "Home"
                    else -> "Away"
                },
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    person.sinceMillis != null -> formatElapsed(person.sinceMillis)
                    person.unknownDuration -> "${historyWindowDays}d+"
                    else -> "--"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    person.sinceMillis == null -> Color.White.copy(alpha = 0.35f)
                    person.isHome -> tint
                    else -> Color.White.copy(alpha = 0.6f)
                }
            )
            Text(
                text = if (person.isHome) "HOME FOR" else "GONE FOR",
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.35f)
            )
        }
    }
}

/**
 * Shown when a requested zone mode cannot be served alongside what is already running.
 *
 * It refuses by default and names the blocker, because the common case is a mis-tap rather
 * than a considered decision. The override is offered as a second, explicit button: it switches
 * the whole house, and sends a Telegram message so a bulk change is never silent.
 */
@Composable
fun ModeConflictDialog(
    conflict: com.example.model.ModeConflict,
    zoneName: String,
    onDismiss: () -> Unit,
    onOverride: () -> Unit,
    onInteraction: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .dialogInteractionReporter(onInteraction)
                .testTag("mode_conflict_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = "CAN'T SET ${zoneName.uppercase()} TO ${conflict.requestedMode.uppercase()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${conflict.blockedBy} is running ${conflict.blockingMode.uppercase()}. " +
                        "The system serves one mode at a time, so these can't run together.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )

                if (conflict.affectedZones.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "SWITCHING EVERYTHING WOULD CHANGE: ${conflict.affectedZones.joinToString(", ")}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        lineHeight = 13.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onDismiss)
                            .testTag("mode_conflict_cancel"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "CANCEL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(AlertRed.copy(alpha = 0.22f))
                            .border(BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)), RoundedCornerShape(11.dp))
                            .clickable(onClick = onOverride)
                            .testTag("mode_conflict_override"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SWITCH EVERYTHING TO ${conflict.requestedMode.uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

/** One-tap scripts that already exist in Home Assistant (goodnight, all-off, and so on). */
@Composable
fun QuickActionsCard(
    viewModel: HvacViewModel,
    actions: List<QuickActionConfig>,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return
    Card(
        modifier = modifier.testTag("quick_actions_card"),
        colors = CardDefaults.cardColors(containerColor = hvacCardBgColor()),
        border = BorderStroke(1.dp, hvacBorderAlphaColor()),
        shape = hvacCardShape(12)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                "QUICK",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.3.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(Modifier.height(7.dp))
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.callDynamicEntityService(
                                domain = action.domain,
                                service = action.service,
                                entityId = action.entityId ?: "",
                                feedbackMessage = action.label
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("quick_action_${action.label.lowercase().replace(' ', '_')}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        imageVector = action.icon?.let { getIconByName(it) } ?: Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = action.label.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The rooms that do not already have a zone card of their own, as compact chips. Rooms that
 * are zones are filtered out upstream so one room can never show two temperatures.
 */
@Composable
fun SecondaryRoomChips(
    rooms: List<RoomSensor>,
    modifier: Modifier = Modifier
) {
    if (rooms.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().testTag("secondary_room_chips"),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        rooms.forEach { room ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = room.name.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = room.temp?.let { "${it.toInt()}°" } ?: "--°",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A full-width segmented button with its label spelled out. Replaces the 30 dp icon-only
 * squares, which were both the most-used control in the app and the smallest.
 */
@Composable
fun RowScope.SegmentedControlButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    contentDescription: String,
    testTagId: String,
    weight: Float = 1f,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(weight)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (isSelected) color.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f))
            .border(
                BorderStroke(1.dp, if (isSelected) color.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.05f)),
                RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 7.dp)
            .testTag(testTagId),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) color else color.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.9.sp,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Outdoor temperature, read from Home Assistant rather than a forecast API. */
@Composable
fun OutdoorReadingChip(
    viewModel: HvacViewModel,
    entityId: String?,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (entityId.isNullOrBlank()) return
    val states by viewModel.entityStates.collectAsStateWithLifecycle()
    val value = formatRounded(readEntityValue(states, entityId))
    val theme = LocalHvacTheme.current

    if (compact) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(999.dp))
                .background(hvacCardBgColor())
                .border(BorderStroke(1.dp, hvacBorderAlphaColor()), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("outdoor_chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value?.let { "$it°" } ?: "--°",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "OUT",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    } else {
        Column(
            modifier = modifier.testTag("outdoor_chip"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value?.let { "$it°" } ?: "--°",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "OUTSIDE",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = theme.coolColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}
