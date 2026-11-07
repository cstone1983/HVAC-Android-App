package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import java.util.Locale

/**
 * How many decimals a room sensor reading shows (Chris, 2026-11-06: "68.4°", not "68°").
 * `roomTempDecimals` in layout_config.json, provided at the dashboard root. Head readings stay
 * whole degrees: the heads only report whole degrees anyway.
 */
val LocalRoomTempDecimals = staticCompositionLocalOf { 1 }

/** "68.4°", rounded rather than cut off as `toInt()` did, or "--°". */
fun roomTempText(value: Double?, decimals: Int, unit: String = "°"): String =
    value?.let { String.format(Locale.US, "%.${decimals.coerceIn(0, 2)}f", it) + unit } ?: "--$unit"

@Composable
fun roomTemp(value: Double?, unit: String = "°"): String = roomTempText(value, LocalRoomTempDecimals.current, unit)

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
