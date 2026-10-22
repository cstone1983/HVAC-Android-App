package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PoolHistoryPoint
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.themedShape
import com.example.viewmodel.HvacViewModel
import kotlin.math.abs

fun formatFriendlyTime(rawInput: String?): String {
    if (rawInput.isNullOrBlank()) return "--"
    val ms = parseHaTimestamp(rawInput)
        ?: rawInput.toDoubleOrNull()?.let { (it * 1000).toLong() }
        ?: return rawInput
    val diffMinutes = (System.currentTimeMillis() - ms) / 60_000
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60} h ago"
        else -> "${diffMinutes / (24 * 60)} days ago"
    }
}

/** A reading older than this means the monitor has stopped reporting; it normally sends every 20 minutes. */
private const val POOL_STALE_MS = 3 * 60 * 60 * 1000L

private val WarnColor = Color(0xFFF59E0B)
private val OkColor = Color(0xFF10B981)

private enum class PoolMetric(val label: String) { WATER("Water"), PH("pH"), CHLORINE("Chlorine") }

private enum class PoolWindow(val label: String, val ms: Long) {
    SIX_HOURS("6 h", 6 * 3_600_000L),
    DAY("24 h", 24 * 3_600_000L),
    WEEK("7 days", 7 * 24 * 3_600_000L)
}

/**
 * The Pool tab, rebuilt 2026-10-21 (review step 4, option A).
 *
 * The old tab could not tell a live pool from a dead monitor. The view model started from a
 * built-in demo reading — 79.0 °F, pH 7.35, ORP 561, Wi-Fi -57 dBm, two serial numbers — and the
 * tab showed it as current whenever Home Assistant had nothing, under a small "3 DAY AGO". When
 * the monitor went quiet on 17 October (its real last readings were 64.7 °F, pH 6.35, 224 mV) the
 * tab went on showing a warm, balanced pool. Its 24-hour button also fell back to the whole month
 * whenever the last 24 hours were empty, and the chart's time axis was three fixed labels.
 *
 * Now every number is a real reading, and when the newest is more than [POOL_STALE_MS] old the
 * tab says so and greys the readings. Each reading sits against the target range from the
 * Home Assistant helpers, the chart is drawn against real time (so the gap after the monitor
 * stopped is visible), and the target ranges and monitor details are pop-ups.
 */
@Composable
fun PoolDashboardView(
    viewModel: HvacViewModel,
    modifier: Modifier = Modifier
) {
    // History downloads only while this tab is on screen.
    DisposableEffect(Unit) {
        viewModel.setPoolHistoryVisible(true)
        onDispose { viewModel.setPoolHistoryVisible(false) }
    }
    val theme = LocalHvacTheme.current
    val poolState by viewModel.poolState.collectAsStateWithLifecycle()
    val poolHistory by viewModel.poolHistory.collectAsStateWithLifecycle()
    val entityStates by viewModel.entityStates.collectAsStateWithLifecycle()
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val tempMin by viewModel.poolTempMin.collectAsStateWithLifecycle()
    val tempMax by viewModel.poolTempMax.collectAsStateWithLifecycle()
    val phMin by viewModel.poolPhMin.collectAsStateWithLifecycle()
    val phMax by viewModel.poolPhMax.collectAsStateWithLifecycle()
    val orpMin by viewModel.poolOrpMin.collectAsStateWithLifecycle()
    val orpMax by viewModel.poolOrpMax.collectAsStateWithLifecycle()

    var metric by remember { mutableStateOf(PoolMetric.WATER) }
    var window by remember { mutableStateOf(PoolWindow.DAY) }
    var showRanges by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    // Live values from Home Assistant; when the monitor is unavailable, its last recorded reading.
    val lastPoint = poolHistory.lastOrNull()
    val water = poolState.waterTemperature ?: lastPoint?.temp?.toDouble()
    val ph = poolState.ph ?: lastPoint?.ph?.toDouble()
    val orp = poolState.orp ?: lastPoint?.orp?.toDouble()
    val liveMissing = poolState.waterTemperature == null && poolState.ph == null && poolState.orp == null
    val readingTime = if (liveMissing) lastPoint?.epochMillis
        else parseHaTimestamp(poolState.lastUpdated) ?: lastPoint?.epochMillis
    val now = System.currentTimeMillis()
    val stale = liveMissing || readingTime == null || now - readingTime > POOL_STALE_MS

    val pumpId = layoutConfig.poolSensors?.pumpSwitchEntityId ?: "switch.pool_pump"
    val pumpState = entityStates[pumpId]

    if (showRanges) {
        PoolRangesDialog(viewModel, onDismiss = { showRanges = false })
    }
    if (showDetails) {
        PoolDetailsDialog(poolState, readingTime, onDismiss = { showDetails = false })
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("pool_tab")
    ) {
        if (stale) {
            StatusBanner(
                text = when {
                    readingTime == null -> "The pool monitor isn't reporting, and there are no recent readings."
                    else -> "Pool monitor offline since ${friendlyWhen(readingTime)}. Showing its last readings."
                },
                trailing = poolState.actionsPending?.takeIf { it > 0 }?.let { "$it actions pending" }
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth < 560.dp) 2 else 4
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> ReadingTile("Water", water?.let { "%.1f°".format(it) }, water, tempMin, tempMax, "%.0f–%.0f°", stale, theme.coolColor, m) },
                { m -> ReadingTile("pH", ph?.let { "%.2f".format(it) }, ph, phMin, phMax, "%.1f–%.1f", stale, theme.ecoColor, m) },
                { m -> ReadingTile("Chlorine (ORP)", orp?.let { "%.0f mV".format(it) }, orp, orpMin, orpMax, "%.0f–%.0f mV", stale, theme.heatColor, m) },
                { m ->
                    PumpTile(
                        on = pumpState?.state == "on",
                        known = pumpState != null && pumpState.state != "unavailable",
                        since = parseHaTimestamp(pumpState?.last_changed),
                        modifier = m
                    ) { viewModel.toggleSwitch(pumpId, pumpState?.state == "on", "Pool pump") }
                }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }

        val (lo, hi, color) = when (metric) {
            PoolMetric.WATER -> Triple(tempMin, tempMax, theme.coolColor)
            PoolMetric.PH -> Triple(phMin, phMax, theme.ecoColor)
            PoolMetric.CHLORINE -> Triple(orpMin, orpMax, theme.heatColor)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(themedShape(14.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), themedShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PoolMetric.values().forEach { m ->
                    Chip(m.label, metric == m, Modifier.testTag("pool_metric_${m.name.lowercase()}")) { metric = m }
                }
                Spacer(Modifier.weight(1f))
                PoolWindow.values().forEach { w ->
                    Chip(w.label, window == w, Modifier.testTag("pool_window_${w.name.lowercase()}")) { window = w }
                }
            }
            PoolChart(
                history = poolHistory,
                metric = metric,
                window = window,
                lo = lo,
                hi = hi,
                color = color,
                modifier = Modifier.fillMaxWidth().height(170.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FooterButton("Target ranges", Icons.Default.Tune, Modifier.testTag("pool_ranges")) { showRanges = true }
            FooterButton("Monitor details", Icons.Default.Info, Modifier.testTag("pool_details")) { showDetails = true }
        }
    }
}

/** "4:29 PM" today, "Sat 5:19 PM" this week, "Oct 3, 5:19 PM" before that. */
internal fun friendlyWhen(epochMs: Long): String {
    val ageMs = System.currentTimeMillis() - epochMs
    val today = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameDay = today.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        today.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "h:mm a"
        ageMs < 6 * 24 * 3_600_000L -> "EEE h:mm a"
        else -> "MMM d, h:mm a"
    }
    return java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(java.util.Date(epochMs))
}

@Composable
private fun StatusBanner(text: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(themedShape(12.dp))
            .background(WarnColor.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, WarnColor.copy(alpha = 0.4f)), themedShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("pool_offline_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = WarnColor, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 14.sp, color = WarnColor, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 13.sp, color = WarnColor.copy(alpha = 0.75f)) }
    }
}

/**
 * One reading against its target range: the number, a bar with the range shaded and the reading
 * marked, and a word for where it sits. Grey when the reading is old.
 */
@Composable
private fun ReadingTile(
    label: String,
    valueText: String?,
    value: Double?,
    lo: Float,
    hi: Float,
    rangeFormat: String,
    stale: Boolean,
    accent: Color,
    modifier: Modifier
) {
    val verdict = when {
        value == null -> null
        value < lo -> "low"
        value > hi -> "high"
        else -> "in range"
    }
    val outOfRange = verdict == "low" || verdict == "high"
    val valueColor = when {
        value == null -> Color.White.copy(alpha = 0.35f)
        stale -> Color.White.copy(alpha = 0.6f)
        outOfRange -> WarnColor
        else -> Color.White
    }
    Column(
        modifier = modifier
            .clip(themedShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), themedShape(12.dp))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
        Text(valueText ?: "--", fontSize = 28.sp, fontWeight = FontWeight.Light, color = valueColor)
        RangeBar(value, lo, hi, marker = if (outOfRange && !stale) WarnColor else accent)
        Spacer(Modifier.height(6.dp))
        val range = rangeFormat.format(lo, hi)
        Text(
            when (verdict) {
                null -> "target $range"
                "in range" -> "in range · $range"
                else -> "$verdict · target $range"
            },
            fontSize = 12.sp,
            color = when {
                verdict == null || stale -> Color.White.copy(alpha = 0.45f)
                outOfRange -> WarnColor
                else -> OkColor
            }
        )
    }
}

/** The target range shaded in the middle third of the bar; a reading outside it pins to the end. */
@Composable
private fun RangeBar(value: Double?, lo: Float, hi: Float, marker: Color) {
    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 6.dp)) {
        val h = 6.dp.toPx()
        val y = (size.height - h) / 2
        val span = (hi - lo).coerceAtLeast(0.0001f)
        val domLo = lo - span
        val domHi = hi + span
        fun x(v: Float) = ((v - domLo) / (domHi - domLo)).coerceIn(0f, 1f) * size.width
        drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawRoundRect(OkColor.copy(alpha = 0.35f), Offset(x(lo), y), Size(x(hi) - x(lo), h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        value?.let {
            val mx = x(it.toFloat()).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
            drawLine(marker, Offset(mx, 0f), Offset(mx, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PumpTile(on: Boolean, known: Boolean, since: Long?, modifier: Modifier, onToggle: () -> Unit) {
    Column(
        modifier = modifier
            .clip(themedShape(12.dp))
            .background(if (on) OkColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f))
            .border(BorderStroke(1.dp, if (on) OkColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.07f)), themedShape(12.dp))
            .padding(12.dp)
    ) {
        Text("Pump", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
        Text(
            if (!known) "Unknown" else if (on) "On" else "Off",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = if (on) OkColor else Color.White
        )
        Text(
            since?.let { "since ${friendlyWhen(it)}" } ?: " ",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.45f)
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(themedShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), themedShape(10.dp))
                .clickable(enabled = known, onClick = onToggle)
                .testTag("pool_pump_toggle"),
            contentAlignment = Alignment.Center
        ) {
            Text(if (on) "Turn off" else "Turn on", fontSize = 15.sp, color = Color.White)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(themedShape(10.dp))
            .background(if (selected) Color(0xFF3B82F6).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, if (selected) Color(0xFF3B82F6).copy(alpha = 0.7f) else Color.Transparent), themedShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) Color.White else Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun FooterButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(themedShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), themedShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

/**
 * The selected reading over the selected window, drawn against real time: the window always ends
 * now, so a monitor that stopped reporting shows as a line that ends early, marked where it
 * stopped. The target range is shaded. Touch the chart to read a point.
 */
@Composable
private fun PoolChart(
    history: List<PoolHistoryPoint>,
    metric: PoolMetric,
    window: PoolWindow,
    lo: Float,
    hi: Float,
    color: Color,
    modifier: Modifier
) {
    val end = remember(history, window) { System.currentTimeMillis() }
    val start = end - window.ms
    val points = remember(history, window, end) {
        val inWindow = history.filter { it.epochMillis in start..end }
        // Seven days of 20-minute readings is ~500 points; a few hundred is plenty to draw.
        if (inWindow.size > 300) inWindow.filterIndexed { i, _ -> i % (inWindow.size / 300 + 1) == 0 } else inWindow
    }
    fun valueOf(p: PoolHistoryPoint) = when (metric) {
        PoolMetric.WATER -> p.temp
        PoolMetric.PH -> p.ph
        PoolMetric.CHLORINE -> p.orp
    }
    fun fmt(v: Float) = when (metric) {
        PoolMetric.WATER -> "%.1f°".format(v)
        PoolMetric.PH -> "%.2f".format(v)
        PoolMetric.CHLORINE -> "%.0f mV".format(v)
    }
    val values = points.map { valueOf(it) }
    val rawLo = minOf(values.minOrNull() ?: lo, lo)
    val rawHi = maxOf(values.maxOrNull() ?: hi, hi)
    val pad = ((rawHi - rawLo) * 0.08f).coerceAtLeast(0.01f)
    val yLo = rawLo - pad
    val yHi = rawHi + pad

    var touched by remember(points, metric) { mutableStateOf<PoolHistoryPoint?>(null) }
    val lastAll = history.lastOrNull()

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (points.isNotEmpty()) Text(fmt(yHi), fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
            Spacer(Modifier.weight(1f))
            touched?.let {
                Text("${fmt(valueOf(it))} · ${friendlyWhen(it.epochMillis)}", fontSize = 13.sp, color = Color.White)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        fun pick(x: Float) {
                            if (points.isEmpty() || size.width == 0) return
                            val t = start + (x / size.width).coerceIn(0f, 1f) * (end - start)
                            touched = points.minByOrNull { abs(it.epochMillis - t) }
                        }
                        detectTapGestures(onPress = { pick(it.x); tryAwaitRelease(); touched = null })
                    }
                    .pointerInput(points) {
                        fun pick(x: Float) {
                            if (points.isEmpty() || size.width == 0) return
                            val t = start + (x / size.width).coerceIn(0f, 1f) * (end - start)
                            touched = points.minByOrNull { abs(it.epochMillis - t) }
                        }
                        detectDragGestures(
                            onDragStart = { pick(it.x) },
                            onDrag = { change, _ -> pick(change.position.x) },
                            onDragEnd = { touched = null },
                            onDragCancel = { touched = null }
                        )
                    }
            ) {
                fun x(t: Long) = (t - start).toFloat() / (end - start) * size.width
                fun y(v: Float) = size.height - (v - yLo) / (yHi - yLo) * size.height
                drawRect(OkColor.copy(alpha = 0.10f), Offset(0f, y(hi)), Size(size.width, y(lo) - y(hi)))
                drawLine(OkColor.copy(alpha = 0.35f), Offset(0f, y(hi)), Offset(size.width, y(hi)), 1f)
                drawLine(OkColor.copy(alpha = 0.35f), Offset(0f, y(lo)), Offset(size.width, y(lo)), 1f)
                if (points.size >= 2) {
                    val path = Path()
                    points.forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(x(p.epochMillis), y(valueOf(p))) else path.lineTo(x(p.epochMillis), y(valueOf(p)))
                    }
                    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else if (points.size == 1) {
                    drawCircle(color, 4.dp.toPx(), Offset(x(points[0].epochMillis), y(valueOf(points[0]))))
                }
                // Where the monitor stopped, if it stopped inside this window.
                val last = points.lastOrNull()
                if (last != null && end - last.epochMillis > POOL_STALE_MS) {
                    val lx = x(last.epochMillis)
                    drawLine(
                        WarnColor.copy(alpha = 0.7f), Offset(lx, 0f), Offset(lx, size.height), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }
                touched?.let { p ->
                    val tx = x(p.epochMillis)
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(tx, 0f), Offset(tx, size.height), 1f)
                    drawCircle(Color.White, 4.dp.toPx(), Offset(tx, y(valueOf(p))))
                }
            }
            if (points.isNotEmpty()) {
                Text(fmt(yLo), fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.align(Alignment.BottomStart))
            }
            if (points.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No readings in the last ${window.label}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                    lastAll?.let {
                        Text("Last reading ${friendlyWhen(it.epochMillis)}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val axis = if (window == PoolWindow.WEEK) "EEE" else "h a"
            val f = java.text.SimpleDateFormat(axis, java.util.Locale.US)
            Text(f.format(java.util.Date(start)), fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
            Spacer(Modifier.weight(1f))
            Text("now", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

/** The target ranges, which live in Home Assistant helpers and drive the pool automations. */
@Composable
private fun PoolRangesDialog(viewModel: HvacViewModel, onDismiss: () -> Unit) {
    val tempMin by viewModel.poolTempMin.collectAsStateWithLifecycle()
    val tempMax by viewModel.poolTempMax.collectAsStateWithLifecycle()
    val phMin by viewModel.poolPhMin.collectAsStateWithLifecycle()
    val phMax by viewModel.poolPhMax.collectAsStateWithLifecycle()
    val orpMin by viewModel.poolOrpMin.collectAsStateWithLifecycle()
    val orpMax by viewModel.poolOrpMax.collectAsStateWithLifecycle()
    val battMin by viewModel.poolBatteryMin.collectAsStateWithLifecycle()
    val battMax by viewModel.poolBatteryMax.collectAsStateWithLifecycle()

    val fields = remember {
        mutableStateListOf(
            "%.0f".format(tempMin), "%.0f".format(tempMax),
            "%.2f".format(phMin), "%.2f".format(phMax),
            "%.0f".format(orpMin), "%.0f".format(orpMax),
            "%.0f".format(battMin), "%.0f".format(battMax)
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    PoolDialog("Target ranges", onDismiss) {
        listOf("Water (°F)", "pH", "Chlorine (ORP, mV)", "Monitor battery (mV)").forEachIndexed { i, label ->
            Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("From", "To").forEachIndexed { j, hint ->
                    OutlinedTextField(
                        value = fields[i * 2 + j],
                        onValueChange = { fields[i * 2 + j] = it; error = null },
                        label = { Text(hint) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        error?.let { Text(it, fontSize = 13.sp, color = Color(0xFFEF4444)) }
        Button(
            onClick = {
                val v = fields.map { it.trim().toFloatOrNull() }
                if (v.any { it == null }) {
                    error = "Enter a number in every box."
                } else if ((0 until 4).any { v[it * 2]!! > v[it * 2 + 1]!! }) {
                    error = "Each \"From\" has to be lower than its \"To\"."
                } else {
                    viewModel.setPoolTempRange(v[0]!!, v[1]!!)
                    viewModel.setPoolPhRange(v[2]!!, v[3]!!)
                    viewModel.setPoolOrpRange(v[4]!!, v[5]!!)
                    viewModel.setPoolBatteryRange(v[6]!!, v[7]!!)
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pool_ranges_save"),
            shape = themedShape(12.dp)
        ) { Text("Save", fontSize = 15.sp) }
    }
}

@Composable
private fun PoolDetailsDialog(state: com.example.model.PoolState, readingTime: Long?, onDismiss: () -> Unit) {
    PoolDialog("Monitor details", onDismiss) {
        val rows = listOf(
            "Last reading" to (readingTime?.let { friendlyWhen(it) } ?: "--"),
            "Last checked in" to formatFriendlyTime(state.lastSynced),
            "Battery" to (state.battery?.let { "%.0f mV".format(it) } ?: "--"),
            "Wi-Fi signal" to (state.wifiSignal?.let { "$it dBm" } ?: "--"),
            "Water status" to (state.waterStatus?.takeUnless { it.equals("unknown", true) } ?: "--"),
            "Actions pending" to (state.actionsPending?.toString() ?: "--"),
            "Monitor serial" to (state.monitorSerial ?: "--"),
            "Sensor serial" to (state.sensorSerial ?: "--")
        )
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                Text(v, fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun PoolDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = themedShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(themedShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.85f)) }
                }
                content()
            }
        }
    }
}
