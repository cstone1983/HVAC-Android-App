package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.SolarChart
import com.example.api.SolarTile
import com.example.api.SolarTileItem
import com.example.api.SolarTileLine
import com.example.model.SolarCalcColors
import kotlin.math.roundToInt

// -------------------------------------------------------------------------------------------
// Palette
// -------------------------------------------------------------------------------------------

/**
 * The Solar Calc site's colours, resolved once.
 *
 * Deliberately not the app's own theme. This section is a window onto another system, and it is
 * supposed to look like that system — so that a number here and the same number on the website
 * read as one thing rather than two that happen to agree.
 */
data class SolarPalette(
    val panel: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val solar: Color,
    val house: Color,
    val model: Color,
    val good: Color,
    val warn: Color
) {
    /** Maps the server's `tone` to a colour. An unknown tone falls back to plain text. */
    fun tone(name: String?): Color = when (name) {
        "solar" -> solar
        "house" -> house
        "good" -> good
        "warn" -> warn
        else -> text
    }

    /** `dot` is a narrower set: only the two series get one. */
    fun dot(name: String?): Color? = when (name) {
        "solar" -> solar
        "house" -> house
        else -> null
    }
}

@Composable
fun rememberSolarPalette(colors: SolarCalcColors?): SolarPalette = remember(colors) {
    SolarPalette(
        panel = parseHexColor(colors?.panel, Color(0xFF111823)),
        line = parseHexColor(colors?.line, Color(0xFF222D3D)),
        text = parseHexColor(colors?.text, Color(0xFFE6EDF7)),
        muted = parseHexColor(colors?.muted, Color(0xFF8B98AD)),
        soft = parseHexColor(colors?.soft, Color(0xFFB4C0D2)),
        solar = parseHexColor(colors?.solar, Color(0xFFF0A92A)),
        house = parseHexColor(colors?.house, Color(0xFF5B8FF9)),
        model = parseHexColor(colors?.model, Color(0xFF9AA7BD)),
        good = parseHexColor(colors?.good, Color(0xFF3ECF8E)),
        warn = parseHexColor(colors?.warn, Color(0xFFE8B23A))
    )
}

// -------------------------------------------------------------------------------------------
// Tiles
// -------------------------------------------------------------------------------------------

/**
 * One tile. The site calls this a DuoStat, and all three are the same composable.
 *
 * Everything shown is rendered as the server sent it — the label, the values, the wording of the
 * lines underneath. Nothing is recomputed here, not even a percentage. That is the same rule the
 * numbers follow, applied to text: the moment the app phrases something itself, it is one
 * rounding decision away from contradicting the website it is meant to mirror.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DuoStatTile(
    tile: SolarTile,
    palette: SolarPalette,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("solar_tile_${tile.id ?: "unknown"}"),
        colors = CardDefaults.cardColors(containerColor = palette.panel),
        border = BorderStroke(1.dp, palette.line),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 15.dp)) {
            Text(
                text = tile.label.orEmpty().uppercase(),
                fontSize = 11.sp,
                letterSpacing = 1.0.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))

            // Wrapping rather than a fixed row: By midnight carries three values, and on a narrow
            // phone three of them will not sit side by side.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tile.items.orEmpty().forEach { item -> TileItem(item, palette) }
            }

            val lines = tile.lines.orEmpty()
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    lines.forEach { line -> TileLine(line, palette) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TileItem(item: SolarTileItem, palette: SolarPalette) {
    val toneColor = palette.tone(item.tone)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            palette.dot(item.dot)?.let { dotColor ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = item.name.orEmpty(),
                fontSize = 12.5.sp,
                color = palette.soft,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        // Value, unit and the optional note share one baseline, as on the site. A flow row so
        // that where the tile is too narrow (the folded phone) the note drops whole onto its
        // own line under the value instead of wrapping inside itself.
        // Spacing between items rather than padding on them: a note that wraps then starts
        // flush left under the value, not indented.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.value.orEmpty(),
                // The By midnight tile shows three figures, so they are set smaller to keep the
                // three tiles the same height.
                fontSize = if (item.small == true) 16.sp else 23.sp,
                fontWeight = if (item.small == true) FontWeight.Medium else FontWeight.SemiBold,
                color = toneColor,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            if (!item.unit.isNullOrBlank()) {
                Text(
                    text = item.unit,
                    fontSize = 13.sp,
                    color = toneColor,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
            }
            item.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (item.noteTone) {
                        "good" -> palette.good
                        "warn" -> palette.warn
                        else -> palette.soft
                    },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 2.dp).alignByBaseline()
                )
            }
        }
    }
}

/**
 * A line of explanation under the values.
 *
 * `highlight` names a substring to pick out in the tone's colour — "92%" in "92% of today's
 * expected solar". The server sends the substring rather than an index, so the first occurrence
 * is the one emphasised; sending the whole sentence pre-split would be the alternative, and this
 * is the version that degrades into plain text if anything does not match.
 */
@Composable
private fun TileLine(line: SolarTileLine, palette: SolarPalette) {
    val text = line.text.orEmpty()
    if (text.isBlank()) return

    val lineColor = when (line.tone) {
        "warn" -> palette.warn
        "good" -> palette.good
        else -> palette.soft
    }
    val highlight = line.highlight
    val at = if (highlight.isNullOrEmpty()) -1 else text.indexOf(highlight)

    if (at < 0) {
        Text(text = text, fontSize = 13.sp, color = lineColor)
        return
    }

    Text(
        fontSize = 13.sp,
        color = lineColor,
        text = buildAnnotatedString {
            append(text.substring(0, at))
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = palette.tone(line.tone),
                    fontWeight = FontWeight.Bold
                )
            ) { append(highlight) }
            append(text.substring(at + highlight!!.length))
        }
    )
}

/**
 * The three tiles.
 *
 * Side by side on a wall panel, stacked on a phone. The threshold is about where three tiles stop
 * being readable rather than any particular device: a Fold crosses it when it is unfolded, which
 * is the behaviour you want and not something to tie to a model name.
 */
@Composable
fun SolarTodayTiles(
    tiles: List<SolarTile>,
    palette: SolarPalette,
    modifier: Modifier = Modifier
) {
    if (tiles.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wideEnough = maxWidth >= 620.dp
        if (wideEnough) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.forEach { tile ->
                    DuoStatTile(tile, palette, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.forEach { tile ->
                    DuoStatTile(tile, palette, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Today chart
// -------------------------------------------------------------------------------------------

/** One series resolved to screen-ready values, so the Canvas does no parsing. */
private data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Float, Float>>,  // x fraction 0..1, kW
    val dashed: Boolean,
    val strokeDp: Float
)

/**
 * Today's measured lines with the forecast drawn over them.
 *
 * The point of the overlay is watching the gold line fill in over the grey one as the day goes:
 * it answers "is today going the way it was supposed to" at a glance, which no pair of numbers
 * does. The model runs the whole day; generation stops at the last sample.
 */
@Composable
fun SolarTodayChart(
    chart: SolarChart,
    palette: SolarPalette,
    modifier: Modifier = Modifier
) {
    val startMs = remember(chart.start) { parseHaTimestamp(chart.start) }
    val stepMin = chart.stepMinutes ?: 5
    // Never assumed to be 288: a clock-change day is 276 or 300, and an off-by-an-hour chart on
    // two days a year is exactly the sort of thing nobody gets round to reporting.
    val slots = chart.slots
        ?: maxOf(chart.generationKw?.size ?: 0, chart.houseKw?.size ?: 0).takeIf { it > 0 }
        ?: 288
    val spanMs = slots.toLong() * stepMin * 60_000L

    if (startMs == null || spanMs <= 0L) return

    /** Absolute time to a 0..1 position across the day. */
    fun xOf(ms: Long): Float = ((ms - startMs).toFloat() / spanMs.toFloat())

    val series = remember(chart, palette) {
        buildList {
            fun slotSeries(values: List<Double?>?, label: String, color: Color, stroke: Float) {
                val pts = values.orEmpty().mapIndexedNotNull { i, v ->
                    v?.let { (i.toFloat() / slots.toFloat()) to it.toFloat() }
                }
                if (pts.isNotEmpty()) add(ChartSeries(label, color, pts, dashed = false, strokeDp = stroke))
            }
            fun hourlySeries(points: List<com.example.api.SolarChartPoint>?, label: String, color: Color) {
                val pts = points.orEmpty().mapNotNull { p ->
                    val t = parseHaTimestamp(p.t) ?: return@mapNotNull null
                    val kw = p.kw ?: return@mapNotNull null
                    xOf(t) to kw.toFloat()
                }
                if (pts.isNotEmpty()) add(ChartSeries(label, color, pts, dashed = true, strokeDp = 1.4f))
            }
            slotSeries(chart.houseKw, "House", palette.house, 1.4f)
            slotSeries(chart.generationKw, "Generation", palette.solar, 2f)
            hourlySeries(chart.model, "Model", palette.model.copy(alpha = 0.85f))
            hourlySeries(chart.housePlan, "House plan", palette.house.copy(alpha = 0.70f))
        }
    }

    val maxKw = remember(series) {
        (series.flatMap { s -> s.points.map { it.second } }.maxOrNull() ?: 1f)
            .coerceAtLeast(0.5f) * 1.12f
    }

    val shadeSpans = remember(chart.behindTrees) {
        chart.behindTrees.orEmpty().mapNotNull { span ->
            val from = parseHaTimestamp(span.from) ?: return@mapNotNull null
            val to = parseHaTimestamp(span.to) ?: return@mapNotNull null
            xOf(from) to xOf(to)
        }
    }

    val nowX = remember(chart.now) { parseHaTimestamp(chart.now)?.let { xOf(it) } }
    val textMeasurer = rememberTextMeasurer()
    var readoutX by remember { mutableStateOf<Float?>(null) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(series) {
                    detectTapGestures(onPress = { readoutX = it.x / size.width.toFloat() })
                }
                .pointerInput(series) {
                    detectDragGestures(
                        onDragStart = { readoutX = it.x / size.width.toFloat() },
                        onDrag = { change, _ -> readoutX = change.position.x / size.width.toFloat() },
                        onDragEnd = { readoutX = null },
                        onDragCancel = { readoutX = null }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().testTag("solar_today_chart")) {
                val leftPad = 8.dp.toPx()
                val rightPad = 40.dp.toPx()
                val topPad = 10.dp.toPx()
                val bottomPad = 20.dp.toPx()
                val plotW = size.width - leftPad - rightPad
                val plotH = size.height - topPad - bottomPad
                if (plotW <= 0f || plotH <= 0f) return@Canvas

                fun px(xFrac: Float) = leftPad + xFrac.coerceIn(0f, 1f) * plotW
                fun py(kw: Float) = topPad + plotH - (kw / maxKw).coerceIn(0f, 1f) * plotH

                // Treeline shade first, so everything else sits over it.
                shadeSpans.forEach { (from, to) ->
                    val x0 = px(from)
                    val x1 = px(to)
                    if (x1 > x0) {
                        drawRect(
                            color = palette.muted.copy(alpha = 0.11f),
                            topLeft = Offset(x0, topPad),
                            size = Size(x1 - x0, plotH)
                        )
                    }
                }

                // Three-hourly grid.
                val ticks = (slots * stepMin) / 180
                for (i in 0..ticks) {
                    val x = px(i.toFloat() / ticks.toFloat())
                    drawLine(
                        color = palette.line,
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + plotH),
                        strokeWidth = 1f
                    )
                }

                series.forEach { s ->
                    val path = Path()
                    s.points.forEachIndexed { i, (xf, kw) ->
                        val x = px(xf)
                        val y = py(kw)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // Fills belong to the two measured series only; a dashed forecast with a wash
                    // under it reads as something that happened.
                    if (!s.dashed && s.points.size > 1) {
                        val fill = Path().apply {
                            addPath(path)
                            lineTo(px(s.points.last().first), topPad + plotH)
                            lineTo(px(s.points.first().first), topPad + plotH)
                            close()
                        }
                        val brush = if (s.label == "Generation") {
                            Brush.verticalGradient(
                                colors = listOf(s.color.copy(alpha = 0.34f), s.color.copy(alpha = 0.02f)),
                                startY = topPad,
                                endY = topPad + plotH
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(s.color.copy(alpha = 0.12f), s.color.copy(alpha = 0.12f))
                            )
                        }
                        drawPath(fill, brush)
                    }

                    drawPath(
                        path = path,
                        color = s.color,
                        style = Stroke(
                            width = s.strokeDp.dp.toPx(),
                            pathEffect = if (s.dashed) {
                                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                            } else null
                        )
                    )
                }

                // Now.
                nowX?.let { nx ->
                    val x = px(nx)
                    drawLine(
                        color = palette.text.copy(alpha = 0.38f),
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + plotH),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawTextSafely(textMeasurer, "now", x + 4.dp.toPx(), topPad, palette.muted, 9f)
                }

                // Readout.
                readoutX?.let { frac ->
                    val x = px(frac)
                    drawLine(
                        color = palette.text.copy(alpha = 0.55f),
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + plotH),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // y axis, right hand side like the trends chart.
                drawTextSafely(
                    textMeasurer, formatKw(maxKw), leftPad + plotW + 4.dp.toPx(), topPad,
                    palette.muted, 9f
                )
                drawTextSafely(
                    textMeasurer, "0", leftPad + plotW + 4.dp.toPx(), topPad + plotH - 10.dp.toPx(),
                    palette.muted, 9f
                )
            }
        }

        readoutX?.let { frac ->
            val atMs = startMs + (frac.coerceIn(0f, 1f) * spanMs).toLong()
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(formatLocalTime(atMs))
                    series.forEach { s ->
                        val kw = s.points.minByOrNull { kotlin.math.abs(it.first - frac) }?.second
                        if (kw != null) append("   ${s.label} ${formatKw(kw)}")
                    }
                },
                fontSize = 11.sp,
                color = palette.soft,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(8.dp))
        ChartLegend(series, shadeSpans.isNotEmpty(), palette)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(series: List<ChartSeries>, hasShade: Boolean, palette: SolarPalette) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        series.forEach { s -> LegendChip(s.label, s.color, palette) }
        if (hasShade) LegendChip("Behind the trees", palette.muted.copy(alpha = 0.5f), palette)
    }
}

@Composable
private fun LegendChip(label: String, color: Color, palette: SolarPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, fontSize = 10.5.sp, color = palette.muted, maxLines = 1)
    }
}

// -------------------------------------------------------------------------------------------
// Small helpers
// -------------------------------------------------------------------------------------------

private fun formatKw(kw: Float): String = String.format(java.util.Locale.US, "%.2f", kw)

/** UTC in, Chris's clock out. The server speaks UTC; the wall panel should not. */
internal fun formatLocalTime(epochMillis: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("America/New_York")
    }.format(java.util.Date(epochMillis))

/**
 * Canvas text that cannot take the chart down with it.
 *
 * Text measurement throws on some layout passes when the width collapses to nothing, and a label
 * is never worth losing the whole graph over.
 */
internal fun DrawScope.drawTextSafely(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sizeSp: Float
) {
    try {
        val measured = measurer.measure(text, TextStyle(color = color, fontSize = sizeSp.sp))
        drawText(measured, topLeft = Offset(x, y))
    } catch (e: Exception) {
        // A missing axis label is survivable; an exception mid-draw is not.
    }
}

// -------------------------------------------------------------------------------------------
// The section as a whole
// -------------------------------------------------------------------------------------------

/**
 * Today, as Solar Calc sees it: three tiles and the chart.
 *
 * Every unhappy state here keeps whatever was last known on screen and explains itself above it,
 * rather than clearing the tab. On a wall panel that is glanced at rather than read, a number
 * that disappears raises more questions than a number labelled five minutes old.
 */
@Composable
fun SolarCalcTodaySection(
    state: com.example.model.SolarTodayState,
    palette: SolarPalette,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val response = state.response

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // A server that has moved on. Deliberately not treated as an error: nothing is wrong
        // with the network, and the fix is in the user's hands rather than time's.
        if (state.unsupportedVersion) {
            SolarNotice(
                text = "Solar Calc has been updated — update the app to read it",
                color = palette.warn,
                palette = palette
            )
        }

        // A failed poll, with something still worth showing underneath.
        if (state.error != null && state.hasData) {
            val age = state.fetchedAtMillis?.let { formatElapsed(it) }
            SolarNotice(
                text = "Data Sync Warning — showing offline cache" + (age?.let { " ($it)" } ?: ""),
                color = palette.warn,
                palette = palette
            )
        }

        when {
            // Nothing yet, and nothing cached: the first load, or a panel that has never reached
            // the site. Says so rather than drawing three empty tiles.
            response == null -> SolarNotice(
                text = if (state.error != null) "Cannot reach Solar Calc" else "Loading today…",
                color = palette.muted,
                palette = palette
            )

            // The small hours, before the first sample of the new day.
            state.notReadyYet -> SolarNotice(
                text = "No readings yet today",
                color = palette.muted,
                palette = palette
            )

            else -> {
                // The server's own staleness, which is a different thing from ours: it means the
                // array stopped reporting, not that the phone failed to ask.
                if (state.isStale) {
                    val last = parseHaTimestamp(response.lastSample)
                    SolarNotice(
                        text = "Last reading " + (last?.let { formatLocalTime(it) } ?: "unknown"),
                        color = palette.warn,
                        palette = palette
                    )
                }

                SolarTodayTiles(tiles = response.tiles.orEmpty(), palette = palette)

                response.chart?.let { chart ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.panel),
                        border = BorderStroke(1.dp, palette.line),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 15.dp)) {
                            Text(
                                text = "TODAY",
                                fontSize = 11.sp,
                                letterSpacing = 1.0.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.muted
                            )
                            Spacer(Modifier.height(10.dp))
                            SolarTodayChart(chart = chart, palette = palette)
                        }
                    }
                }

                // The week either side of today, below the chart as on the site. Hidden
                // entirely when the reply predates the field.
                SolarWeekSection(week = response.week, palette = palette)
            }
        }
    }
}

/** A one-line banner in the section's own palette, so it does not look like a system error. */
@Composable
private fun SolarNotice(text: String, color: Color, palette: SolarPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.panel, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text = text, fontSize = 12.sp, color = color, maxLines = 2)
    }
}
