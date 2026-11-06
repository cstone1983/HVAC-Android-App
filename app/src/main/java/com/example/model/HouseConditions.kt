package com.example.model

import com.squareup.moshi.JsonClass

/**
 * The Heat pump settings page (2026-11-06): switches that change what the heat pumps aim for,
 * opened from the gear on the home screen's heat pump section.
 *
 * Each condition is a Home Assistant helper. n8n does the work — the HVAC Controller, Watchdog and
 * Bot all read the same CONDITIONS table — and the app reads the same rules from here so the card's
 * SET, the pop-up's highlighted block and the "Setting to" check agree with what n8n will write.
 * Keep [conditions] in the same order and with the same rules as that table; later entries win.
 */
@JsonClass(generateAdapter = true)
data class HvacSettingsConfig(
    val title: String? = "HEAT PUMP SETTINGS",
    val stayOnHeading: String? = "STAYS ON UNTIL YOU TURN IT OFF",
    val timedHeading: String? = "TURNS ITSELF OFF",
    val conditions: List<HouseConditionConfig> = emptyList(),
    val nightDelay: NightDelayConfig? = null
)

/**
 * One house condition.
 *
 * A helper whose id starts with `timer.` is a timed row (a button that starts it for
 * [durationMinutes], then CANCEL while it runs); anything else is an on/off switch. A condition
 * with no [name] is not shown as a row — Night early is switched on by [NightDelayConfig] — but
 * still counts towards the zones' targets and the home screen's chips.
 */
@JsonClass(generateAdapter = true)
data class HouseConditionConfig(
    val entityId: String,
    /** The helper's state while the condition is on: "on" for a switch, "active" for a timer. */
    val onState: String = "on",
    /** Zone keys it covers; "*" covers every zone. */
    val zones: List<String> = emptyList(),
    /** Only applies while the house schedule is this block (lower case), if set. */
    val fromSchedule: String? = null,
    /** The schedule block (lower case) the covered zones follow instead of the house's. */
    val schedule: String? = null,
    /** Degrees added while heating and taken off while cooling, read from this input_number. */
    val boostDegreesEntityId: String? = null,
    val boostDefault: Double = 3.0,
    /** How long a timed condition runs when started from the page. */
    val durationMinutes: Int? = null,
    val name: String? = null,
    /** The row's second line. "{deg}" and "{min}" are filled in. */
    val description: String? = null,
    /** The button that starts a timed condition. */
    val actionLabel: String? = null,
    /** Shown after the card's SET while the condition changes that zone's target. */
    val setLabel: String? = null,
    /** The home screen's chip while it is on. A timed one adds the minutes left. */
    val chip: String? = null
) {
    val timed: Boolean get() = entityId.startsWith("timer.")
    fun covers(zoneKey: String): Boolean = zones.contains("*") || zones.contains(zoneKey)
}

/** "Night in N minutes": a duration, then what goes to Night. See automation "HVAC - Night delay finished". */
@JsonClass(generateAdapter = true)
data class NightDelayConfig(
    val timerEntityId: String,
    val scopeEntityId: String,
    val name: String? = "Night early",
    val description: String? = null,
    val actionLabel: String? = "START",
    val durations: List<Int> = listOf(5, 10, 30, 60),
    val durationQuestion: String? = "Start Night in…",
    val scopeQuestion: String? = "What should go to Night?",
    val scopes: List<NightDelayScope> = emptyList(),
    /** Only offered while the house schedule is this block (lower case). */
    val onlyFromSchedule: String? = "day",
    /** The switch "Heat pumps only" turns on when the countdown ends; its row offers END while it is on. */
    val earlyNightEntityId: String? = null,
    val earlyNightTitle: String? = "Heat pumps on Night",
    val earlyNightDescription: String? = null,
    val chip: String? = "NIGHT IN"
)

@JsonClass(generateAdapter = true)
data class NightDelayScope(
    /** The input_select option the automation reads. */
    val option: String,
    val title: String,
    val description: String? = null
)

/** What the conditions do to one zone: the schedule block it follows instead, and its boost. */
data class ZoneConditionEffect(
    /** "Day", "Night" or "Away" when a condition changes the zone's block; null when it follows the house. */
    val schedule: String? = null,
    val boostDegrees: Double = 0.0,
    /** The SET label of the last condition that changed the zone, if it has one. */
    val label: String? = null
)

/** A boost never takes a heating target past this. Same as BOOST_HEAT_MAX in n8n. */
const val BOOST_HEAT_MAX_F = 80.0

/**
 * The same rules as n8n's zoneSched() and zoneBoost(): a schedule condition replaces the house's
 * block (only while the house is on [HouseConditionConfig.fromSchedule], if set), later ones win,
 * and boosts add up. [state] returns a helper's state.
 */
fun zoneConditionEffect(
    zoneKey: String,
    houseSchedule: String,
    conditions: List<HouseConditionConfig>,
    state: (String) -> String?
): ZoneConditionEffect {
    val house = houseSchedule.lowercase()
    var schedule = house
    var boost = 0.0
    var label: String? = null
    for (c in conditions) {
        if (!c.covers(zoneKey) || state(c.entityId) != c.onState) continue
        if (c.schedule != null) {
            if (c.fromSchedule != null && c.fromSchedule.lowercase() != house) continue
            schedule = c.schedule.lowercase()
            label = c.setLabel ?: label
        }
        if (c.boostDegreesEntityId != null) {
            val d = state(c.boostDegreesEntityId)?.toDoubleOrNull()
            boost += if (d != null && d > 0 && d <= 10) d else c.boostDefault
            label = c.setLabel ?: label
        }
    }
    return ZoneConditionEffect(
        schedule = if (schedule != house) schedule.replaceFirstChar { it.uppercase() } else null,
        boostDegrees = boost,
        label = label
    )
}

/** A schedule target with a boost applied, as n8n's zoneTarget() does: up when heating, down when cooling. */
fun applyBoost(value: Double, boost: Double, targetMode: String): Double {
    if (boost == 0.0) return value
    return when (targetMode.lowercase()) {
        "heat" -> minOf(value + boost, maxOf(value, BOOST_HEAT_MAX_F))
        "cool" -> value - boost
        else -> value
    }
}

/** The page's live view of each condition. */
data class HouseConditionStatus(
    val config: HouseConditionConfig,
    val on: Boolean,
    /** When a running timer ends, epoch millis. */
    val endsAt: Long? = null,
    /** The boost size now in effect, for the row's wording. */
    val boostDegrees: Double? = null
)

data class NightDelayStatus(
    val config: NightDelayConfig,
    val running: Boolean,
    val endsAt: Long? = null,
    /** The scope chosen for the running countdown. */
    val scope: String? = null
)

data class HouseConditionsState(
    val conditions: List<HouseConditionStatus> = emptyList(),
    val nightDelay: NightDelayStatus? = null
)
