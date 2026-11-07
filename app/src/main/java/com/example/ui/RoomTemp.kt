package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * How room sensor readings are drawn, from layout_config.json, provided at the dashboard root.
 *
 * - [decimals]: `roomTempDecimals` (Chris, 2026-11-06: "68.4°", not "68°"). Head readings stay
 *   whole degrees: the heads only report whole degrees anyway.
 * - [changeSeconds], [riseColor], [fallColor]: `roomTempChangeSeconds` and the two colour hexes.
 *   When a reading changes, a green "+0.2" or red "−0.3" sits beside it for that long. 0 turns it off.
 */
data class RoomTempStyle(
    val decimals: Int = 1,
    val changeSeconds: Int = 20,
    val riseColor: Color = Color(0xFF10B981),
    val fallColor: Color = Color(0xFFEF4444)
)

val LocalRoomTempStyle = staticCompositionLocalOf { RoomTempStyle() }

private fun rounded(value: Double, decimals: Int): Double {
    val scale = 10.0.pow(decimals.coerceIn(0, 2))
    return (value * scale).roundToLong() / scale
}

/** "68.4°", rounded rather than cut off as `toInt()` did, or "--°". */
fun roomTempText(value: Double?, decimals: Int, unit: String = "°"): String =
    value?.let { String.format(Locale.US, "%.${decimals.coerceIn(0, 2)}f", it) + unit } ?: "--$unit"

@Composable
fun roomTemp(value: Double?, unit: String = "°"): String = roomTempText(value, LocalRoomTempStyle.current.decimals, unit)

/**
 * The big reading on a card or pop-up, with the decimal drawn smaller so the whole degrees stay the
 * thing you read from across the room, and the number still fits where "68°" did.
 */
@Composable
fun roomTempHero(value: Double): AnnotatedString {
    val text = roomTemp(value)
    val dot = text.indexOf('.')
    if (dot < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, dot))
        withStyle(SpanStyle(fontSize = 0.6.em)) { append(text.substring(dot)) }
    }
}

/**
 * The last change in a room's reading, as shown (so 68.31 to 68.34 is no change at one decimal),
 * for [RoomTempStyle.changeSeconds] after it happens; null otherwise. A newer change replaces it
 * and starts the time again. A sensor dropping out and coming back is not a change, and nothing
 * shows when a screen first opens.
 */
@Composable
fun rememberRoomTempChange(key: String, value: Double?): Double? {
    val style = LocalRoomTempStyle.current
    val shown = value?.let { rounded(it, style.decimals) }
    var last by remember(key, style.decimals) { mutableStateOf(shown) }
    var change by remember(key, style.decimals) { mutableStateOf<Double?>(null) }
    LaunchedEffect(key, style.decimals, shown) {
        val previous = last
        if (shown == null) return@LaunchedEffect
        last = shown
        if (previous == null || previous == shown || style.changeSeconds <= 0) return@LaunchedEffect
        change = rounded(shown - previous, style.decimals)
        delay(style.changeSeconds * 1000L)
        change = null
    }
    return change
}

/** "+0.2" in green or "−0.3" in red, fading in and out. Draws nothing while [change] is null. */
@Composable
fun RoomTempChange(change: Double?, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val style = LocalRoomTempStyle.current
    // Holds the last change through the fade-out, after the caller's has gone back to null.
    var lastChange by remember { mutableStateOf(0.0) }
    if (change != null) lastChange = change
    AnimatedVisibility(visible = change != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val amount = String.format(Locale.US, "%.${style.decimals.coerceIn(0, 2)}f", abs(lastChange))
        Text(
            text = (if (lastChange > 0) "+" else "−") + amount,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = if (lastChange > 0) style.riseColor else style.fallColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
