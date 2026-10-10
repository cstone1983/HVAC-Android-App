package com.example.service

import com.example.api.EntityState
import com.example.ui.parseHaTimestamp

/**
 * A clock every panel agrees on, borrowed from Home Assistant.
 *
 * Android's own wall clock is not good enough to beep in time with another tablet. Both panels
 * have automatic date and time switched on, which sounds like it should settle the matter, but
 * network time on Android syncs rarely and lands within a second or so rather than within a few
 * milliseconds. A beep is 130 ms long: two devices a couple of hundred milliseconds apart do not
 * overlap at all, and you hear two alarms rather than one house.
 *
 * Measuring that skew directly turned out to be impossible from a laptop — an adb round trip is
 * around 320 ms, which swamps the thing being measured. So rather than chase the device clocks,
 * this sidesteps them: every state Home Assistant sends is stamped with *its* time, and there is
 * exactly one Home Assistant. If each panel works out how far its own clock sits from the
 * server's, they are all working from the same number.
 *
 * ### Why the minimum
 *
 * For any message, `local arrival − server stamp` is the clock offset plus however long that
 * message took to arrive. Latency is never negative and never zero, so every sample overstates
 * the offset, and the smallest sample seen is the closest to the truth. On a LAN the quickest
 * messages arrive in a few milliseconds, which puts the estimate comfortably inside the margin
 * where two beeps are heard as one.
 *
 * Over a recent window rather than all time, so that a clock correction — or one freakishly fast
 * sample — is eventually forgotten instead of poisoning the estimate until the app restarts.
 *
 * ### What it is not
 *
 * Not a general-purpose clock, and deliberately not used for anything a person reads. It exists
 * so that two tablets in different rooms land on the same instant. Until it has a sample it
 * reports the local clock unchanged, which is exactly as good as what came before it.
 */
object HaClock {

    /** How often to take a sample. Cheap enough to be frequent, rare enough to be free. */
    private const val SAMPLE_INTERVAL_MS = 20_000L

    /** Samples kept. At one per 20s this is a rolling ten minutes. */
    private const val WINDOW = 30

    /**
     * A sample further out than this is discarded rather than averaged in.
     *
     * A device whose clock is genuinely minutes out is not a latency measurement, it is a broken
     * clock, and quietly correcting for it here would hide a problem worth seeing.
     */
    private const val SANE_LIMIT_MS = 120_000L

    private val deltas = ArrayDeque<Long>()
    private var lastSampleMs = 0L

    /** serverNow ≈ localNow − this. Null until the first sample. */
    @Volatile
    private var offsetMs: Long? = null

    /** True once there is something better than the bare device clock. */
    val isSynced: Boolean get() = offsetMs != null

    /** The current best estimate of how far this device's clock sits ahead of the server's. */
    val offsetEstimateMs: Long get() = offsetMs ?: 0L

    /**
     * The shared instant, in milliseconds.
     *
     * Falls back to the device clock when nothing has been sampled yet. The fallback is not a
     * silent failure: it is the behaviour that existed before this class, and the only cost is
     * that the panels may be audibly apart for the first countdown after a restart.
     */
    fun nowMs(): Long = System.currentTimeMillis() - (offsetMs ?: 0L)

    /**
     * Offer the newest timestamp in a batch of states as a sample.
     *
     * Throttled internally, because `states` emits on every batch of Home Assistant events and
     * scanning every entity each time would put a parse of several hundred timestamps on the
     * service's hot path for no gain.
     *
     * The *newest* stamp in the batch is the one worth sampling: it belongs to whatever just
     * changed, so its latency is the message that has only just arrived rather than something
     * that has been sitting in the map since start-up.
     */
    fun sampleFrom(states: Map<String, EntityState>) {
        val localNow = System.currentTimeMillis()
        if (localNow - lastSampleMs < SAMPLE_INTERVAL_MS) return
        lastSampleMs = localNow

        var newest = Long.MIN_VALUE
        for (entity in states.values) {
            val stamped = parseHaTimestamp(entity.last_updated ?: entity.last_changed) ?: continue
            if (stamped > newest) newest = stamped
        }
        if (newest == Long.MIN_VALUE) return
        sample(serverMs = newest, localMs = localNow)
    }

    /** Visible for the sampler above and for tests; see the class note on why the minimum. */
    fun sample(serverMs: Long, localMs: Long) {
        val delta = localMs - serverMs
        if (delta > SANE_LIMIT_MS || delta < -SANE_LIMIT_MS) return

        deltas.addLast(delta)
        while (deltas.size > WINDOW) deltas.removeFirst()
        val previous = offsetMs
        val settled = deltas.min()
        offsetMs = settled
        // Logged on a real move only. Two panels printing their own figures is the only way to
        // see the skew between them: it cannot be measured over adb, where the round trip is
        // larger than the thing being measured.
        if (previous == null || kotlin.math.abs(settled - previous) > 25L) {
            android.util.Log.i(
                "HaClock",
                "Offset estimate " + settled + "ms behind Home Assistant, from " +
                    deltas.size + " samples"
            )
        }
    }

    /** For a fresh start after a reconnect, where the old window may describe a different path. */
    fun reset() {
        deltas.clear()
        offsetMs = null
        lastSampleMs = 0L
    }
}
