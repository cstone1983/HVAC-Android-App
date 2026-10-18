package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * The Solar Calc app endpoint: `GET {baseUrl}/api/app/v1/today`.
 *
 * Solar Calc is a separate system — n8n, Postgres and a web dashboard at solar.stoneyshome.com —
 * that forecasts the array with a fitted physics model, re-issued every fifteen minutes and
 * corrected against what the array has actually made so far today. It reads Home Assistant and
 * controls nothing.
 *
 * ### Why this app does no arithmetic on any of it
 *
 * The app and the site used to disagree: 98.0 kWh here against 95.7 kWh there, for the same day.
 * The site was right. This app was adding up every power reading since midnight and treating each
 * as exactly ten minutes, which they are not — that counts the gaps as flat and runs about 2%
 * high, always upwards, every day.
 *
 * The site never integrates power at all. Made and used are the meters' own daily energy
 * counters, which agree with Home Assistant's hourly statistics to 0.01%.
 *
 * So everything here is taken as given. No integrating power into kWh, no forecast logic, no sun
 * or treeline maths. The server does it once, and the app and the website cannot drift apart
 * again — which is the whole point, and worth defending against the temptation to "just compute
 * that one little number locally".
 *
 * The tiles even arrive pre-worded. That looks odd for an API until you notice it is the same
 * guarantee applied to text: if the site says "92% of today's expected solar" and the app works
 * out its own percentage, the two eventually disagree over a rounding rule.
 */
interface SolarCalcApi {

    /**
     * Full URL rather than a path, so the base URL can come from `layout_config.json` and change
     * over the air without rebuilding Retrofit.
     */
    @GET
    suspend fun today(@Url url: String): SolarTodayResponse
}

/**
 * Every field is nullable or defaulted, deliberately.
 *
 * The server is free to add fields, and a response that gains one must not stop parsing on a
 * panel nobody has updated. The same goes the other way: a field that disappears leaves a null
 * rather than an exception.
 */
@JsonClass(generateAdapter = true)
data class SolarTodayResponse(
    /** Major version. An unknown one means do not trust the shape — show the cached copy instead. */
    val version: Int? = null,
    /** False just after midnight, before anything has been measured. */
    val ready: Boolean? = null,
    @Json(name = "generated_at") val generatedAt: String? = null,
    val tz: String? = null,
    /** The local day being described, e.g. "2026-10-15". */
    val day: String? = null,
    @Json(name = "last_sample") val lastSample: String? = null,
    /** True when the last sample is more than fifteen minutes old. */
    val stale: Boolean? = null,
    val tiles: List<SolarTile>? = null,
    val chart: SolarChart? = null,
    /**
     * The week either side of today. Added after version 1 shipped, which is why it is
     * optional rather than a version bump: a build that predates it simply never looks, and a
     * build that expects it hides both cards when the field is absent.
     */
    val week: SolarWeek? = null
) {
    /**
     * The contract this app was written against.
     *
     * A higher major version means the server has changed shape in a way this build cannot be
     * trusted to read. Refusing is the safe failure: a half-understood response on a solar
     * dashboard produces numbers that look plausible and are wrong, which is worse than a
     * message saying the app needs updating.
     */
    val isSupported: Boolean get() = version == null || version <= SUPPORTED_VERSION

    companion object {
        const val SUPPORTED_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class SolarTile(
    /** "right_now", "so_far" or "by_midnight". */
    val id: String? = null,
    val label: String? = null,
    val items: List<SolarTileItem>? = null,
    val lines: List<SolarTileLine>? = null,
    /**
     * The raw values behind the formatted strings, for anything that needs to compare rather
     * than display.
     *
     * Typed as Any? rather than Double? because it is not all numbers: By midnight carries
     * generation_done as a boolean. Typing it as numbers made the whole response fail to parse,
     * which is the failure this class is otherwise built to avoid — one unexpected value and
     * the tab has nothing at all. Read it through [number] and [flag].
     */
    val data: Map<String, Any?>? = null
) {
    /** A numeric field from [data], whatever JSON number type it arrived as. */
    fun number(key: String): Double? = when (val v = data?.get(key)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    /** A boolean field from [data], e.g. generation_done. */
    fun flag(key: String): Boolean? = data?.get(key) as? Boolean
}

@JsonClass(generateAdapter = true)
data class SolarTileItem(
    val name: String? = null,
    /** Already rounded and formatted by the server, minus signs included. Shown as given. */
    val value: String? = null,
    val unit: String? = null,
    /** One of solar, house, good, warn, plain. */
    val tone: String? = null,
    /** "solar" or "house" draws a coloured dot beside the name. */
    val dot: String? = null,
    /** The By midnight tile carries three values, so they are set smaller. */
    val small: Boolean? = null,
    /**
     * A short aside drawn after the value, e.g. "(+12.9 vs last night)" on By midnight's Bank:
     * how far the bank is heading above or below what last night's forecast planned. Optional —
     * absent before any forecast exists, and then nothing is drawn.
     */
    val note: String? = null,
    /** The note's colour: "good", "warn" or "plain" (the soft text colour). */
    @Json(name = "note_tone") val noteTone: String? = null
)

@JsonClass(generateAdapter = true)
data class SolarTileLine(
    val text: String? = null,
    /** A substring of [text] drawn bold in the tone colour. */
    val highlight: String? = null,
    val tone: String? = null
)

@JsonClass(generateAdapter = true)
data class SolarChart(
    /** Local midnight, expressed in UTC. */
    val start: String? = null,
    @Json(name = "step_minutes") val stepMinutes: Int? = null,
    /**
     * How many slots the day has. Never assumed: a clock-change day is 276 or 300, and an
     * off-by-an-hour chart on those two days a year is exactly the kind of bug nobody reports.
     */
    val slots: Int? = null,
    /** Measured generation. Length = [slots]; null entries are the future, or a gap. */
    @Json(name = "generation_kw") val generationKw: List<Double?>? = null,
    @Json(name = "house_kw") val houseKw: List<Double?>? = null,
    /** The forecast, hourly, at each hour's midpoint, for the whole day. */
    val model: List<SolarChartPoint>? = null,
    /** The expected house draw, hourly. May be empty. */
    @Json(name = "house_plan") val housePlan: List<SolarChartPoint>? = null,
    /** Spans where the array is shaded by the treeline. */
    @Json(name = "behind_trees") val behindTrees: List<SolarShadeSpan>? = null,
    val now: String? = null
)

@JsonClass(generateAdapter = true)
data class SolarChartPoint(
    val t: String? = null,
    val kw: Double? = null
)

@JsonClass(generateAdapter = true)
data class SolarShadeSpan(
    val from: String? = null,
    val to: String? = null
)

// -------------------------------------------------------------------------------------------
// The week either side of today
// -------------------------------------------------------------------------------------------

/**
 * Seven finished days, today, and six forecast days — the same window the website opens on.
 *
 * The site offers 7d / 30d / 90d / 1y buttons; the app deliberately does not. The reply always
 * carries this one window, and adding range buttons would mean either new endpoints or the app
 * re-slicing data it did not compute, which is the thing this whole contract exists to prevent.
 */
@JsonClass(generateAdapter = true)
data class SolarWeek(
    val title: String? = null,
    /** The small line at the card's top right, e.g. "6 days forecast · last 7 days measured". */
    val tag: String? = null,
    /** Oldest first: finished days, then today, then the forecast. */
    val days: List<SolarWeekDay>? = null,
    val costs: SolarWeekCosts? = null
)

@JsonClass(generateAdapter = true)
data class SolarWeekDay(
    val day: String? = null,
    val weekday: String? = null,
    /** "Oct 15". The narrow layout shows the day number alone instead. */
    val date: String? = null,
    /** "past", "today" or "future". */
    val kind: String? = null,
    /** Finished days only — the solid gold line. */
    @Json(name = "actual_kwh") val actualKwh: Double? = null,
    /** Today only — the hollow circle, not joined to either line. */
    @Json(name = "so_far_kwh") val soFarKwh: Double? = null,
    @Json(name = "so_far_label") val soFarLabel: String? = null,
    /** The dashed line. Null leaves a genuine gap rather than a line through nothing. */
    @Json(name = "forecast_kwh") val forecastKwh: Double? = null,
    @Json(name = "p10_kwh") val p10Kwh: Double? = null,
    @Json(name = "p90_kwh") val p90Kwh: Double? = null,
    val weather: SolarWeekWeather? = null,
    /** What a tap on this column shows, in order. */
    val readout: List<SolarTileLine>? = null
) {
    val isToday: Boolean get() = kind == "today"
    val isFuture: Boolean get() = kind == "future"

    /** True when both ends of the band are known; one end alone cannot be drawn. */
    val hasBand: Boolean get() = p10Kwh != null && p90Kwh != null
}

@JsonClass(generateAdapter = true)
data class SolarWeekWeather(
    /** sunny, partly-sunny, mostly-cloudy, overcast, fog, drizzle, rain, showers, snow, storm. */
    val kind: String? = null,
    val label: String? = null,
    /** e.g. "78% cloud". */
    val detail: String? = null
)

@JsonClass(generateAdapter = true)
data class SolarWeekCosts(
    val title: String? = null,
    val tag: String? = null,
    /** Header labels, in order. The narrow layout substitutes its own shorter ones. */
    val columns: List<String>? = null,
    val rows: List<SolarWeekCostRow>? = null,
    /** The sentence under the table. May be absent. */
    val summary: String? = null
)

@JsonClass(generateAdapter = true)
data class SolarWeekCostRow(
    val day: String? = null,
    val solar: String? = null,
    val usage: String? = null,
    @Json(name = "heat_pumps") val heatPumps: String? = null,
    @Json(name = "to_bank") val toBank: String? = null,
    /** good, warn or plain — the only coloured cell in the table. */
    @Json(name = "to_bank_tone") val toBankTone: String? = null
)
