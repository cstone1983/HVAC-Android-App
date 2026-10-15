package com.example.model

import com.example.api.SolarTodayResponse

/**
 * What the app knows about Solar Calc's view of today.
 *
 * The last good response is kept and kept *showing* through a failure, with its age, rather than
 * being cleared. A solar dashboard that blanks because one poll timed out is less useful than one
 * admitting it is five minutes behind — and on a wall panel, which is glanced at rather than
 * read, a number that vanishes is more alarming than a number that is slightly old.
 */
data class SolarTodayState(
    /** The last response that parsed and was a version this build understands. */
    val response: SolarTodayResponse? = null,
    val isLoading: Boolean = false,
    /** Set when the most recent attempt failed. [response] may still be worth showing. */
    val error: String? = null,
    /** When [response] arrived, for saying how stale it is. */
    val fetchedAtMillis: Long? = null,
    /**
     * The server has moved to a major version this build does not understand.
     *
     * Kept separate from [error] because the remedy is different and the user can act on it:
     * a failed fetch fixes itself, this one needs the app updating. Reading a response whose
     * shape has changed would produce plausible-looking wrong numbers, which on this screen is
     * worse than showing nothing new.
     */
    val unsupportedVersion: Boolean = false
) {
    /** True when there is something worth drawing, whatever the last attempt did. */
    val hasData: Boolean get() = response != null

    /** True when the server says it has nothing for today yet — the small hours. */
    val notReadyYet: Boolean get() = response?.ready == false

    /** True when the server's own last sample is more than fifteen minutes old. */
    val isStale: Boolean get() = response?.stale == true

    fun ageMillis(nowMillis: Long = System.currentTimeMillis()): Long? =
        fetchedAtMillis?.let { (nowMillis - it).coerceAtLeast(0L) }
}
