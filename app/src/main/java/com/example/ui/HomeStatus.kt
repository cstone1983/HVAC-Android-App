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
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    val alert: Triple<String, Color, androidx.compose.ui.graphics.vector.ImageVector>? = when {
        openDoors.isNotEmpty() -> Triple(
            openDoors.joinToString(" · ") { "${it.name.uppercase()} OPEN" },
            AlertRed,
            Icons.Default.Garage
        )
        isOffline -> Triple("OFFLINE — SHOWING LAST KNOWN VALUES", AlertRed, Icons.Default.CloudOff)
        !feedback.isNullOrBlank() -> {
            val failed = feedback!!.contains("fail", true) || feedback!!.contains("error", true)
            Triple(
                feedback!!.uppercase(),
                if (failed) AlertRed else Color(0xFF10B981),
                if (failed) Icons.Default.Info else Icons.Default.Sync
            )
        }
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
    modifier: Modifier = Modifier
) {
    if (presenceEntityIds.isEmpty()) return
    val states by viewModel.entityStates.collectAsStateWithLifecycle()
    val people = presenceEntityIds.mapNotNull { id ->
        val entity = states[id] ?: return@mapNotNull null
        val name = entity.getStringAttribute("friendly_name")
            ?: id.substringAfter('.').replace('_', ' ')
        name to entity.state.equals("home", true)
    }
    if (people.isEmpty()) return
    val homeCount = people.count { it.second }

    Card(
        modifier = modifier.testTag("presence_card"),
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
                people.take(6).forEach { (name, isHome) ->
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
