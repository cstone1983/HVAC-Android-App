package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ClimateZone
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.colorForMode
import com.example.ui.theme.hvacActiveBorderAlphaColor
import com.example.ui.theme.hvacActiveCardBgColor
import com.example.ui.theme.hvacBorderAlphaColor
import com.example.ui.theme.hvacCardBgColor
import com.example.ui.theme.hvacCardShape
import com.example.ui.theme.tintForMode
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel

/**
 * The wall-panel home screen: a command center for the house rather than a thermostat page.
 *
 * Built around one question someone walking past should be able to answer from across the room:
 * is the house okay? The heat pumps stay the main subject and get most of the screen; the alarm
 * and what the house is doing sit beside them; the controls that are changed a few times a day
 * move to a strip along the bottom.
 *
 * Agreed with Chris through eight mockups on 2026-10-17. The decisions that look arbitrary are
 * deliberate, and each was argued for:
 *
 *  - **Nothing is said twice.** The top bar carries only time and weather, because people, doors,
 *    alarm and energy each have a card of their own. An earlier draft repeated them up there and
 *    was rejected for it.
 *  - **The room sensor is the big number,** with the head's own reading beneath it. The heads
 *    measure their return air and read 1–4° warm against the room; showing both, labelled, means
 *    nobody has to guess which one they are looking at.
 *  - **No per-sensor alarm list.** Status and the two arm buttons only; the alarm tab has the rest.
 *  - **No Goodnight / House Off buttons** here.
 *
 * Used only on wide landscape screens — see [useCommandCenter]. Everything else keeps the
 * previous layout, and `homeStatus.commandCenter` turns this off over the air.
 */
@Composable
fun CommandCenterHome(
    state: HvacUiState.Success,
    viewModel: HvacViewModel,
    height: Dp,
    onZoneClick: (ClimateZone) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("command_center"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommandTopBar(viewModel)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ZonesHeader()
                Spacer(Modifier.height(6.dp))
                ZoneGrid(
                    zones = state.zones,
                    onZoneClick = onZoneClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OtherRoomsLine(state, viewModel)
            }

            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The existing card, unchanged: it already carries the exit and entry
                // countdowns, CANCEL, and DISARM through the keypad.
                HomeAlarmCard(viewModel = viewModel, modifier = Modifier.fillMaxWidth())
                EnergyAndPeopleCard(
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

        ControlStrip(state = state, viewModel = viewModel)
    }
}

/**
 * Whether this screen gets the command center.
 *
 * By window size, never by device model: a wide landscape window, tall enough that six zones
 * stay readable. The two wall tablets pass; so does the Fold's inner screen held sideways. A
 * phone on its side does not — it is wide but nowhere near tall enough.
 */
fun useCommandCenter(enabled: Boolean?, isLandscape: Boolean, widthDp: Int, heightDp: Int): Boolean =
    enabled != false && isLandscape && widthDp >= 840 && heightDp >= 520

// -------------------------------------------------------------------------------------------
// Top bar
// -------------------------------------------------------------------------------------------

@Composable
private fun CommandTopBar(viewModel: HvacViewModel) {
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()

    // The forecast was only ever fetched by the portrait weather card, which the wall panels
    // never draw. Ask for it here too, once, if nothing has arrived yet.
    LaunchedEffect(Unit) {
        if (weather.days.isEmpty()) viewModel.fetchWeather()
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            // Wake on the minute rather than every second: the clock shows minutes, and a
            // wall panel that never sleeps has no business recomposing sixty times a minute.
            kotlinx.coroutines.delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val outdoor = layoutConfig.homeStatus?.outdoorTempEntityId
        ?.let { entityStates[it]?.state?.toDoubleOrNull() }
    val today = weather.days.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(hvacCardBgColor(), hvacCardShape(10))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.US).format(java.util.Date(nowMs)),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Box(Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.1f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = weatherIcon(today?.iconName),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = buildString {
                    append(outdoor?.let { "${it.toInt()}° outside" } ?: "--° outside")
                    today?.condition?.takeIf { it.isNotBlank() }?.let { append(" · ${it.lowercase()}") }
                },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        Spacer(Modifier.weight(1f))
        weather.days.drop(1).take(3).forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = shortWeekday(day.dateString, day.dayLabel),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Icon(
                    imageVector = weatherIcon(day.iconName),
                    contentDescription = day.condition,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${day.avgHighTemp.toInt()}°",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** "Sun" from "2026-10-18". The forecast labels tomorrow "TOMORROW", which truncated to "Tom". */
private fun shortWeekday(date: String, fallback: String): String = try {
    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date)
    java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(parsed!!)
} catch (e: Exception) {
    fallback.take(3).lowercase().replaceFirstChar { it.uppercase() }
}

private fun weatherIcon(name: String?): ImageVector = when (name?.lowercase()) {
    "sunny", "clear" -> Icons.Default.WbSunny
    "rainy", "rain", "showers" -> Icons.Default.Umbrella
    "stormy", "storm" -> Icons.Default.Thunderstorm
    "snowy", "snow" -> Icons.Default.AcUnit
    else -> Icons.Default.Cloud
}

// -------------------------------------------------------------------------------------------
// Heat pumps
// -------------------------------------------------------------------------------------------

private val RoomColor = Color(0xFF7FD1C7)
private val HumidityColor = Color(0xFF6AA8E0)

@Composable
private fun ZonesHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "HEAT PUMPS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.weight(1f))
        LegendItem(Icons.Default.Home, RoomColor, "room")
        Spacer(Modifier.width(12.dp))
        LegendItem(Icons.Default.Air, Color.White.copy(alpha = 0.55f), "head")
        Spacer(Modifier.width(12.dp))
        LegendItem(Icons.Default.WaterDrop, HumidityColor, "humidity")
    }
}

@Composable
private fun LegendItem(icon: ImageVector, tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
    }
}

/** Two rows of three, filling whatever height is left so every zone is visible without scrolling. */
@Composable
private fun ZoneGrid(
    zones: List<ClimateZone>,
    onZoneClick: (ClimateZone) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = zones.chunked(3)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { zone ->
                    CommandZoneCard(
                        zone = zone,
                        onClick = { onZoneClick(zone) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One zone.
 *
 * The colour language is the existing card's, kept on purpose because the household already reads
 * it: the body tints by mode (orange heat, blue cool, purple dry, plain off), the border turns red
 * when the zone is not following its schedule (HOLD or an override), and the card only glows and
 * pulses while the head is actually running. Mode alone never glows.
 */
@Composable
private fun CommandZoneCard(
    zone: ClimateZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalHvacTheme.current
    val activeColor = theme.colorForMode(zone.currentHvacMode)
    val modeTint = theme.tintForMode(zone.currentHvacMode)

    val pulse = rememberInfiniteTransition(label = "cc_zone")
    val glow by pulse.animateFloat(
        initialValue = 0.05f,
        targetValue = if (zone.isCalling) 0.22f else 0.05f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cc_glow"
    )

    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag("cc_zone_${zone.key}")
            .drawBehind {
                if (zone.isCalling) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(activeColor.copy(alpha = glow), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.55f),
                            radius = size.width * 0.6f
                        )
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = modeTint?.let { hvacActiveCardBgColor(it) } ?: hvacCardBgColor()
        ),
        border = BorderStroke(
            1.dp,
            when {
                zone.overrideOn || !zone.autoOn -> hvacActiveBorderAlphaColor(theme.boostColor)
                modeTint != null -> hvacActiveBorderAlphaColor(modeTint)
                else -> hvacBorderAlphaColor()
            }
        ),
        shape = hvacCardShape(12)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The hero numeral scales with the card, so the A9's shorter cards still fit.
            val heroSize = (maxHeight.value * 0.26f).coerceIn(26f, 50f).sp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Name and what it is doing.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (zone.key) {
                            "main_level" -> Icons.Default.Weekend
                            "bedroom_1", "bedroom_2" -> Icons.Default.Bed
                            "basement" -> Icons.Default.AcUnit
                            else -> Icons.Default.Person
                        },
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                val s = if (zone.isCalling) 0.92f + glow else 1f
                                scaleX = s
                                scaleY = s
                            }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        // 10% smaller than the old card's 13sp.
                        text = zone.name,
                        fontSize = 11.7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zone.statusLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        color = if (zone.isCalling) activeColor else Color.White.copy(alpha = 0.45f),
                        maxLines = 1
                    )
                }

                // Flags: fan, schedule, override. Their own row, so they are never squeezed
                // off by a long zone name, and small, because they are detail.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlagChip(zone.fanMode.uppercase(), theme.coolColor)
                    FlagChip(
                        if (zone.autoOn) "AUTO" else "HOLD",
                        if (zone.autoOn) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                    if (zone.overrideOn) {
                        Box(
                            modifier = Modifier
                                .background(theme.boostColor.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Override active",
                                tint = theme.boostColor,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }

                // The room, big. Falls back to the head with its own icon where a zone has no
                // room sensor, so the two can never be mistaken for each other.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val room = zone.roomTemp
                    Icon(
                        imageVector = when {
                            room == null -> Icons.Default.Air
                            zone.roomIsThermostat -> Icons.Default.Thermostat
                            else -> Icons.Default.Home
                        },
                        contentDescription = when {
                            room == null -> "Head reading"
                            zone.roomIsThermostat -> "Thermostat reading"
                            else -> "Room sensor"
                        },
                        tint = if (room == null) Color.White.copy(alpha = 0.55f) else RoomColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = (room ?: zone.headTemp)?.let { "${it.toInt()}°" } ?: "--°",
                        fontSize = heroSize,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1.2).sp,
                        lineHeight = heroSize,
                        color = Color.White,
                        maxLines = 1
                    )
                    zone.roomHumidity?.let { humidity ->
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.WaterDrop,
                            contentDescription = "Humidity",
                            tint = HumidityColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${humidity.toInt()}%",
                            fontSize = 13.sp,
                            color = HumidityColor
                        )
                    }
                }

                // The head and the target.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (zone.roomTemp != null) {
                        Icon(
                            Icons.Default.Air,
                            contentDescription = "Head reading",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "head ${zone.headTemp?.toInt()?.let { "$it°" } ?: "--"}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "SET ${zone.targetTemp?.toInt() ?: "--"}°",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        color = if (zone.isCalling) activeColor else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = text, fontSize = 7.5.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = 0.2.sp)
    }
}

/** The room sensors that do not belong to a zone — Dining and the stairway. */
@Composable
private fun OtherRoomsLine(state: HvacUiState.Success, viewModel: HvacViewModel) {
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val ids = layoutConfig.homeStatus?.otherRoomIds ?: listOf("dining_room", "upstairs")
    val rooms = state.roomSensors.filter { it.id in ids }
    if (rooms.isEmpty()) return
    Row(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Other", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
        rooms.forEach { room ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Home, contentDescription = null, tint = RoomColor, modifier = Modifier.size(12.dp))
                Text(
                    text = room.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = room.temp?.let { "${it.toInt()}°" } ?: "--°",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Energy and people
// -------------------------------------------------------------------------------------------

/**
 * Energy today and who is home, together, as agreed.
 *
 * The energy figures are Solar Calc's own strings, not anything worked out here. "Made so far"
 * and "used so far" are the meters' counters; the forecast sits on its own line so nobody mistakes
 * a prediction for a reading.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnergyAndPeopleCard(viewModel: HvacViewModel, modifier: Modifier = Modifier) {
    val solar by viewModel.solarToday.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val solarCfg = layoutConfig.solarCalc

    // The solar tab polls only while it is open. The home screen now shows energy too, so it
    // keeps the figures fresh while it is the one on screen.
    LaunchedEffect(solarCfg?.enabled, solarCfg?.pollSeconds) {
        if (solarCfg?.enabled == false) return@LaunchedEffect
        val everyMs = ((solarCfg?.pollSeconds ?: 60).coerceAtLeast(15)) * 1000L
        while (true) {
            viewModel.fetchSolarToday()
            kotlinx.coroutines.delay(everyMs)
        }
    }

    val tiles = solar.response?.tiles.orEmpty()
    fun item(tileId: String, name: String): String? =
        tiles.firstOrNull { it.id == tileId }?.items?.firstOrNull { it.name == name }?.value
    val made = item("so_far", "Made")
    val used = item("so_far", "Used")
    val forecast = item("by_midnight", "Made")
    val bank = item("by_midnight", "Bank")

    val theme = LocalHvacTheme.current
    val solarColor = Color(0xFFF0A92A)
    val houseColor = Color(0xFF5B8FF9)

    Column(
        modifier = modifier
            .background(hvacCardBgColor(), hvacCardShape(12))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text("ENERGY TODAY", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(8.dp))
        EnergyLine("Made so far", made, solarColor)
        Spacer(Modifier.height(4.dp))
        EnergyLine("Used so far", used, houseColor)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
        Spacer(Modifier.height(6.dp))
        Text(
            // One short line. "Forecast by midnight" wrapped on the A9 and pushed Who's Home
            // off the bottom of the card.
            text = buildString {
                append("Forecast ")
                append(forecast ?: "--")
                bank?.let { append(" · bank $it") }
            },
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Takes only what is spare. On the A9 there is none, and a plain weight here let the
        // energy lines crowd the people out of the card entirely.
        Spacer(Modifier.height(10.dp))

        Text("WHO'S HOME", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(6.dp))
        val people = layoutConfig.homeStatus?.presenceEntityIds.orEmpty().mapNotNull { id ->
            val node = entityStates[id] ?: return@mapNotNull null
            val name = node.getStringAttribute("friendly_name") ?: id.substringAfter('.')
            name to (node.state == "home")
        }.sortedByDescending { it.second }
        // Everyone where there is room for everyone (the A8). Where there is not (the A9), the
        // people at home keep their names and everyone away folds into one chip — a name cut
        // off halfway reads as a fault, and who is away matters less than who is in.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val roomForAll = maxHeight >= 48.dp
            val home = people.filter { it.second }
            val away = people.filterNot { it.second }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                home.forEach { (name, _) -> PersonChip(name, true, theme.ecoColor) }
                if (roomForAll) {
                    away.forEach { (name, _) -> PersonChip(name, false, theme.ecoColor) }
                } else if (away.isNotEmpty()) {
                    // Just "+3": "+3 away" wrapped onto a line the A9 does not have.
                    PersonChip("+${away.size}", false, theme.ecoColor)
                }
            }
        }
    }
}

@Composable
private fun PersonChip(name: String, home: Boolean, homeColor: Color) {
    Box(
        modifier = Modifier
            .background(
                if (home) homeColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            fontWeight = if (home) FontWeight.SemiBold else FontWeight.Normal,
            color = if (home) homeColor else Color.White.copy(alpha = 0.4f),
            maxLines = 1
        )
    }
}

@Composable
private fun EnergyLine(label: String, value: String?, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(value ?: "--", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(" kWh", fontSize = 11.sp, color = color, modifier = Modifier.padding(bottom = 2.dp))
    }
}

// -------------------------------------------------------------------------------------------
// Controls
// -------------------------------------------------------------------------------------------

/**
 * Schedule, mode and hot water along the bottom — the same three groups as before, with the same
 * behaviour, moved out of the top of the screen. They are changed a few times a day; the
 * temperatures above them are read constantly.
 */
@Composable
private fun ControlStrip(state: HvacUiState.Success, viewModel: HvacViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(hvacCardBgColor(), hvacCardShape(12))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Shorter than everywhere else, at Chris's request: the height goes to the zones.
        androidx.compose.runtime.CompositionLocalProvider(LocalCompactControls provides true) {
            ScheduleGroup(state, viewModel, 10.sp, Modifier.weight(1f))
            HouseModeGroup(state, viewModel, 10.sp, Modifier.weight(1.4f))
            HotWaterGroup(state, viewModel, 10.sp, Modifier.weight(1.4f))
        }
    }
}
