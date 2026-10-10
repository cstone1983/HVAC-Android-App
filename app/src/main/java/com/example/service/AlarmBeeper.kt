package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * The entry/exit countdown beep on the wall panels.
 *
 * What a real alarm panel does when you walk in the door: a tick while it waits for a code, and
 * the tick gets faster as the time runs out. That acceleration is the whole point — it tells you
 * how long you have left without making you find a screen, which is the moment you are least
 * likely to want to go looking for one.
 *
 * Deliberately mild. This is not [AlarmSiren] and must not be mistaken for it: a soft sine pip,
 * not the square-wave wail, because the house has not been broken into yet and the person
 * hearing it is almost always whoever lives there. It also does not force the volume up the way
 * the siren does, for the same reason — if a panel has been turned down, an informational tick
 * is not grounds for overriding that.
 *
 * It does play on the alarm stream (`USAGE_ALARM`), so it is still audible on a panel sitting in
 * silent mode or Do Not Disturb. Nobody sets a tablet's alarm volume to zero by accident, and if
 * they do, the siren is the thing that still needs to be heard, not this.
 *
 * The caller owns the rhythm; this object owns one pip. See `HvacForegroundService`, which runs
 * the loop and decides the interval from the time remaining.
 */
object AlarmBeeper {

    private const val TAG = "AlarmBeeper"

    private const val SAMPLE_RATE = 44_100
    private const val BEEP_MS = 130
    private const val FREQ_HZ = 1_000.0

    /** Fade in and out, in samples. Without it the pip starts and ends on a click. */
    private const val ENVELOPE_SAMPLES = 220

    private var track: AudioTrack? = null

    /**
     * One pip.
     *
     * The track is built once and rewound for each beep rather than rebuilt. At roughly one beep
     * a second for up to a minute that is the difference between one allocation and sixty, and
     * `AudioTrack` construction is not cheap.
     */
    fun beep(context: Context) {
        try {
            val t = track ?: build().also { track = it }
            if (t.state != AudioTrack.STATE_INITIALIZED) {
                // A track that failed to initialise never recovers; drop it and try again fresh
                // on the next pip rather than silently ticking nothing for the whole countdown.
                release()
                return
            }
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
            Log.w(TAG, "Could not sound the countdown beep", e)
            release()
        }
    }

    fun release() {
        try {
            track?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing the beeper", e)
        }
        track = null
    }

    private fun build(): AudioTrack {
        val wave = buildPip()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(wave.size * 2)
            .build()
        t.write(wave, 0, wave.size)
        return t
    }

    /** A plain sine pip with a short fade at each end. */
    private fun buildPip(): ShortArray {
        val total = SAMPLE_RATE * BEEP_MS / 1000
        val wave = ShortArray(total)
        val amplitude = (Short.MAX_VALUE * 0.55).toInt()
        for (i in 0 until total) {
            var sample = sin(2.0 * PI * FREQ_HZ * i / SAMPLE_RATE)
            if (i < ENVELOPE_SAMPLES) {
                sample *= i.toDouble() / ENVELOPE_SAMPLES
            } else if (i > total - ENVELOPE_SAMPLES) {
                sample *= (total - i).toDouble() / ENVELOPE_SAMPLES
            }
            wave[i] = (sample * amplitude).toInt().toShort()
        }
        return wave
    }

    /**
     * How long to wait before the next pip, given the seconds left on the countdown.
     *
     * Tiers rather than a smooth curve, because a gradual change is not perceptible as a change —
     * you only hear that it is getting faster when it steps. Each step is roughly a halving.
     *
     * A null countdown means the panel did not report a delay. Falling back to the slow rate is
     * the right failure: a steady tick that says "it is waiting for you" is still useful, and it
     * never implies more urgency than is actually known.
     */
    fun intervalMsFor(secondsRemaining: Int?): Long = when {
        secondsRemaining == null -> 1_200L
        secondsRemaining <= 5 -> 250L
        secondsRemaining <= 10 -> 450L
        secondsRemaining <= 20 -> 800L
        else -> 1_200L
    }
}
