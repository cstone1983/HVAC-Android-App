package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.SolarWeek
import com.example.api.SolarWeekCosts
import com.example.api.SolarWeekDay

// -------------------------------------------------------------------------------------------
// Layout
// -------------------------------------------------------------------------------------------

/**
 * How much room there is, which is not the same question as which device this is.
 *
 * A Fold is two devices by width and one by model number, so anything keyed to the model gets it
 * wrong half the time. Keyed to width, the cover screen and a phone get the same treatment, the
 * inner screen and a small tablet get the same treatment, and a wall panel in landscape gets the
 * side-by-side layout without anyone having to name it.
 */
private enum class WeekLayout { NARROW, WIDE, SIDE_BY_SIDE }

private fun layoutFor(widthDp: Int): WeekLayout = when {
    widthDp >= 840 -> WeekLayout.SIDE_BY_SIDE
    widthDp >= 600 -> WeekLayout.WIDE
    else -> WeekLayout.NARROW
}

/**
 * The week ahead and what it costs, below the Today chart.
 *
 * Both cards are hidden when the reply carries no `week`. That field arrived after the rest of
 * the contract shipped, so its absence is an older server rather than a fault, and an empty card
 * saying so would be noise on a screen that is glanced at.
 */
@Composable
fun SolarWeekSection(
    week: SolarWeek?,
    palette: SolarPalette,
    modifier: Modifier = Modifier
) {
    if (week == null) return
    val days = week.days.orEmpty()
    if (days.isEmpty() && week.costs?.rows.orEmpty().isEmpty()) return

    // Survives a fold, an unfold and a rotation. Losing the day you had just tapped because you
    // opened the phone is the kind of thing that makes a screen feel broken.
    var selectedDay by rememberSaveable { mutableStateOf(-1) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val layout = layoutFor(maxWidth.value.toInt())
        val narrow = layout == WeekLayout.NARROW

        if (layout == WeekLayout.SIDE_BY_SIDE) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1.25f)) {
                    WeekAheadCard(week, days, palette, narrow, selectedDay) { selectedDay = it }
                }
                Box(Modifier.weight(1f)) {
                    WeekCostsCard(week.costs, palette, narrow)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekAheadCard(week, days, palette, narrow, selectedDay) { selectedDay = it }
                WeekCostsCard(week.costs, palette, narrow)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// The week ahead
// -------------------------------------------------------------------------------------------

@Composable
private fun WeekAheadCard(
    week: SolarWeek,
    days: List<SolarWeekDay>,
    palette: SolarPalette,
    narrow: Boolean,
    selectedDay: Int,
    onSelect: (Int) -> Unit
) {
    if (days.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.panel),
        border = BorderStroke(1.dp, palette.line),
        shape = com.example.ui.theme.themedShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("solar_week_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 15.dp)) {
            CardHeader(week.title, week.tag, palette)
            Spacer(Modifier.height(12.dp))

            // Icons and labels are real composables in Rows of equal weight rather than text
            // drawn on the Canvas, so they line up with the columns for free and the icons are
            // the app's own vector set rather than something hand-drawn.
            WeatherRow(days, palette, iconSize = if (narrow) 16.dp else 20.dp)
            Spacer(Modifier.height(4.dp))

            WeekPlot(
                days = days,
                palette = palette,
                heightDp = if (narrow) 240.dp else 320.dp,
                selectedDay = selectedDay,
                onSelect = onSelect
            )

            Spacer(Modifier.height(4.dp))
            DayLabelRow(days, palette, narrow)

            Spacer(Modifier.height(10.dp))
            WeekLegend(palette)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Shaded: where the day is likely to land (about 8 days in 10).",
                fontSize = 11.sp,
                color = palette.muted,
                lineHeight = 15.sp
            )

            // The tapped day's own words, straight from the reply.
            days.getOrNull(selectedDay)?.let { day ->
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.line.copy(alpha = 0.45f), com.example.ui.theme.themedShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    day.readout.orEmpty().forEach { line ->
                        Text(
                            text = line.text.orEmpty(),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = when (line.tone) {
                                "muted" -> palette.muted
                                "good" -> palette.good
                                "warn" -> palette.warn
                                else -> palette.text
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRow(days: List<SolarWeekDay>, palette: SolarPalette, iconSize: androidx.compose.ui.unit.Dp) {
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val icon = weatherIcon(day.weather?.kind)
                if (icon == null) {
                    // No forecast for that day: a dot, rather than guessing at a symbol.
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(palette.muted.copy(alpha = 0.5f))
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = day.weather?.label,
                        tint = palette.muted,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayLabelRow(days: List<SolarWeekDay>, palette: SolarPalette, narrow: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.weekday.orEmpty(),
                    fontSize = if (narrow) 9.sp else 11.sp,
                    color = if (day.isToday) palette.text else palette.muted,
                    fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    // Narrow columns are about 26dp: "Oct 15" does not fit, so the day number
                    // carries it and the weekday above gives the context.
                    text = if (narrow) day.date.orEmpty().substringAfterLast(' ') else day.date.orEmpty(),
                    fontSize = if (narrow) 9.sp else 10.sp,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WeekPlot(
    days: List<SolarWeekDay>,
    palette: SolarPalette,
    heightDp: androidx.compose.ui.unit.Dp,
    selectedDay: Int,
    onSelect: (Int) -> Unit
) {
    val n = days.size
    val textMeasurer = rememberTextMeasurer()
    val maxY = (
        days.flatMap {
            listOfNotNull(it.p90Kwh, it.forecastKwh, it.actualKwh, it.soFarKwh)
        }.maxOrNull() ?: 1.0
        ).toFloat().coerceAtLeast(1f) * 1.10f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .testTag("solar_week_plot")
            .pointerInput(n) {
                detectTapGestures { offset ->
                    // Nearest column to the tap. On the cover screen the columns are narrower
                    // than a fingertip, so "nearest" is the only workable rule — an exact hit
                    // test would make half the week untappable.
                    if (n > 0 && size.width > 0) {
                        val idx = ((offset.x / size.width) * n).toInt().coerceIn(0, n - 1)
                        onSelect(if (idx == selectedDay) -1 else idx)
                    }
                }
            }
    ) {
        if (n == 0) return@Canvas
        val topPad = 14.dp.toPx()
        val bottomPad = 4.dp.toPx()
        val plotH = size.height - topPad - bottomPad
        if (plotH <= 0f) return@Canvas
        val colW = size.width / n
        fun cx(i: Int) = colW * (i + 0.5f)
        fun cy(v: Double) = topPad + plotH - (v.toFloat() / maxY).coerceIn(0f, 1f) * plotH

        val todayIndex = days.indexOfFirst { it.isToday }

        // The forecast half of the chart, washed very faintly so the eye knows where measurement
        // stops without a hard edge through the middle of the week.
        if (todayIndex >= 0) {
            drawRect(
                color = Color.White.copy(alpha = 0.025f),
                topLeft = Offset(cx(todayIndex), 0f),
                size = Size(size.width - cx(todayIndex), size.height)
            )
        }

        // The band, drawn as one shape per run of consecutive days that have one. A null breaks
        // the run rather than being bridged, because a bridge would assert a forecast that was
        // never issued.
        var run = mutableListOf<Int>()
        fun flushBand() {
            if (run.size >= 2) {
                val path = Path()
                run.forEachIndexed { k, i ->
                    val x = cx(i); val y = cy(days[i].p90Kwh!!)
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                run.reversed().forEach { i -> path.lineTo(cx(i), cy(days[i].p10Kwh!!)) }
                path.close()
                drawPath(path, palette.solar.copy(alpha = 0.14f))
            }
            run = mutableListOf()
        }
        days.forEachIndexed { i, d -> if (d.hasBand) run.add(i) else flushBand() }
        flushBand()

        fun strokeSeries(
            value: (SolarWeekDay) -> Double?,
            color: Color,
            widthDp: Float,
            dashed: Boolean,
            dotRadiusDp: Float
        ) {
            var started = false
            val path = Path()
            days.forEachIndexed { i, d ->
                val v = value(d)
                if (v == null) { started = false; return@forEachIndexed }
                val x = cx(i); val y = cy(v)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = widthDp.dp.toPx(),
                    pathEffect = if (dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                    } else null
                )
            )
            days.forEachIndexed { i, d ->
                value(d)?.let { drawCircle(color, dotRadiusDp.dp.toPx(), Offset(cx(i), cy(it))) }
            }
        }

        strokeSeries({ it.forecastKwh }, palette.model.copy(alpha = 0.9f), 1.4f, dashed = true, dotRadiusDp = 2f)
        strokeSeries({ it.actualKwh }, palette.solar, 2f, dashed = false, dotRadiusDp = 2.6f)

        // Today: a hollow ring, joined to nothing. It is a partial figure and should not look
        // like a point on either line.
        if (todayIndex >= 0) {
            days[todayIndex].soFarKwh?.let { soFar ->
                drawCircle(
                    color = palette.solar,
                    radius = 4.dp.toPx(),
                    center = Offset(cx(todayIndex), cy(soFar)),
                    style = Stroke(width = 2.dp.toPx())
                )
                days[todayIndex].soFarLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    drawTextSafely(
                        textMeasurer,
                        label,
                        cx(todayIndex) - 14.dp.toPx(),
                        cy(soFar) - 18.dp.toPx(),
                        palette.solar,
                        10f
                    )
                }
            }
            drawLine(
                color = palette.text.copy(alpha = 0.3f),
                start = Offset(cx(todayIndex), 0f),
                end = Offset(cx(todayIndex), size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (selectedDay in days.indices) {
            drawLine(
                color = palette.text.copy(alpha = 0.5f),
                start = Offset(cx(selectedDay), 0f),
                end = Offset(cx(selectedDay), size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun WeekLegend(palette: SolarPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot("Actual", palette.solar, filled = true, palette = palette)
        LegendDot("So far today", palette.solar, filled = false, palette = palette)
        LegendDot("Forecast", palette.model, filled = true, palette = palette)
    }
}

@Composable
private fun LegendDot(label: String, color: Color, filled: Boolean, palette: SolarPalette) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (filled) color else Color.Transparent)
                .then(
                    if (filled) Modifier
                    else Modifier.background(color.copy(alpha = 0.35f))
                )
        )
        Text(label, fontSize = 11.sp, color = palette.muted, maxLines = 1)
    }
}

// -------------------------------------------------------------------------------------------
// What the week costs you
// -------------------------------------------------------------------------------------------

@Composable
private fun WeekCostsCard(costs: SolarWeekCosts?, palette: SolarPalette, narrow: Boolean) {
    if (costs == null) return
    val rows = costs.rows.orEmpty()
    if (rows.isEmpty()) return

    // On the cover screen five full headings and five "kWh" suffixes will not fit, so the units
    // move into the tag. Dropping them without saying so anywhere would leave a table of bare
    // numbers, which is worse than a cramped one.
    val headers = if (narrow) {
        listOf("Day", "Solar", "Use", "Heat", "Bank")
    } else {
        costs.columns.orEmpty().ifEmpty {
            listOf("day", "solar", "usage", "heat pumps", "to the bank")
        }
    }
    val tag = if (narrow) listOfNotNull(costs.tag, "All in kWh").joinToString(" · ") else costs.tag

    fun cell(value: String?): String =
        if (narrow) value.orEmpty().replace(" kWh", "") else value.orEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.panel),
        border = BorderStroke(1.dp, palette.line),
        shape = com.example.ui.theme.themedShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("solar_week_costs_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 15.dp)) {
            CardHeader(costs.title, tag, palette)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                headers.forEachIndexed { i, h ->
                    Text(
                        text = h.uppercase(),
                        fontSize = if (narrow) 9.sp else 10.sp,
                        letterSpacing = 0.6.sp,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                        modifier = Modifier.weight(if (i == 0) 1.1f else 1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.day.orEmpty(),
                        fontSize = if (narrow) 11.sp else 12.5.sp,
                        color = palette.soft,
                        maxLines = 1,
                        modifier = Modifier.weight(1.1f)
                    )
                    listOf(row.solar, row.usage, row.heatPumps).forEach { v ->
                        Text(
                            text = cell(v),
                            fontSize = if (narrow) 11.sp else 12.5.sp,
                            color = palette.text,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = cell(row.toBank),
                        fontSize = if (narrow) 11.sp else 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (row.toBankTone) {
                            "good" -> palette.good
                            "warn" -> palette.warn
                            else -> palette.text
                        },
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            costs.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(10.dp))
                Text(text = summary, fontSize = 12.sp, color = palette.soft, lineHeight = 16.sp)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Shared bits
// -------------------------------------------------------------------------------------------

/** Title on the left, tag on the right, as on the site's cards. */
@Composable
private fun CardHeader(title: String?, tag: String?, palette: SolarPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.orEmpty().uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.0.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.muted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!tag.isNullOrBlank()) {
            Text(
                text = tag,
                fontSize = 10.sp,
                color = palette.muted.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * The server's weather words, mapped to the app's own icon set.
 *
 * An unknown kind returns null and draws a dot, which is also what a day with no forecast gets.
 * That is deliberate: a new kind added on the server should look like "nothing to say" rather
 * than be quietly rendered as whichever symbol happened to be the fallback.
 */
private fun weatherIcon(kind: String?): ImageVector? = when (kind) {
    "sunny" -> Icons.Default.WbSunny
    "partly-sunny" -> Icons.Default.WbCloudy
    "mostly-cloudy" -> Icons.Default.Cloud
    "overcast" -> Icons.Default.FilterDrama
    "fog" -> Icons.Default.BlurOn
    "drizzle" -> Icons.Default.Grain
    "rain" -> Icons.Default.WaterDrop
    "showers" -> Icons.Default.Umbrella
    "snow" -> Icons.Default.AcUnit
    "storm" -> Icons.Default.Bolt
    else -> null
}
