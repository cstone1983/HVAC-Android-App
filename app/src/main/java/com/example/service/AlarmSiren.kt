package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sounds the wall panels when the alarm goes off.
 *
 * Deliberately a wall-panel feature. A phone in a pocket does not need to become a siren, and a
 * phone that does so in a meeting is a reason to turn the whole thing off — see [isWallPanel].
 *
 * Three things have to be true for this to carry to another room, and all three are easy to get
 * wrong:
 *
 *  - **The alarm stream, not the music or notification stream.** `USAGE_ALARM` is the only one
 *    that keeps sounding through silent mode and Do Not Disturb. A siren on the media stream
 *    means a muted tablet stays silent during a break-in.
 *  - **Volume forced to maximum, and put back afterwards.** The panel's alarm volume is whatever
 *    it was last left at, which on a device nobody plays audio on is usually nothing. The previous
 *    level is restored on stop so this cannot quietly become permanent.
 *  - **A sound that reads as an alarm.** See [buildSirenWave] — the first version borrowed the
 *    device's alarm-clock tone and was, accurately, described as sounding like waking up in the
 *    morning.
 */
object AlarmSiren {

    private const val TAG = "AlarmSiren"

    private const val SAMPLE_RATE = 44_100

    /** Seconds for one full wail, down and back up. Two-ish is the classic civil-siren rate. */
    private const val SWEEP_SECONDS = 1.6

    /**
     * The sweep range, in Hz.
     *
     * Human hearing peaks around 2–4 kHz, which is why smoke alarms live there. This sweeps
     * across the bottom of that band: high enough to be piercing and to cut through a closed
     * door, low enough not to be lost by someone older whose top end has gone.
     */
    private const val FREQ_LOW = 700.0
    private const val FREQ_HIGH = 1_900.0

    private var track: AudioTrack? = null
    private var volumeBeforeSiren: Int? = null

    /**
     * The devices that may sound by default: the two wall-mounted Galaxy Tabs.
     *
     * An explicit model list, and deliberately so. The obvious approach — Android's own 600dp
     * tablet threshold — is wrong on a foldable: the Z Fold 7 reports about 411dp closed and
     * about 750dp open, so the phone in Chris's pocket would classify as a wall panel and start
     * sounding the moment it was unfolded. That is precisely the behaviour this is meant to
     * avoid, and it would have been intermittent, which is worse.
     *
     * This only sets the default. The setting is per device and overridable either way, so a new
     * panel is one toggle rather than a release — and an unlisted device stays silent until
     * somebody asks for it, which is the right direction to fail in.
     */
    private val WALL_PANEL_MODELS = setOf("SM-X200", "SM-X110")

    fun isWallPanel(context: Context): Boolean =
        Build.MODEL?.trim()?.uppercase() in WALL_PANEL_MODELS

    /** Already sounding? Used to avoid restarting the tone on every state push. */
    val isSounding: Boolean
        get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun start(context: Context) {
        if (isSounding) return
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio != null && volumeBeforeSiren == null) {
                volumeBeforeSiren = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                try {
                    audio.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                        0
                    )
                } catch (e: SecurityException) {
                    // Raising the alarm stream is blocked while a Do Not Disturb policy is active
                    // and the app has no policy access. Carry on at whatever the panel is set to —
                    // quieter than intended beats silence.
                    Log.w(TAG, "Could not raise alarm volume; sounding at the current level", e)
                }
            }

            val wave = buildSirenWave()
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
            // The buffer holds exactly one whole sweep, so looping the whole thing is seamless —
            // it ends on the same frequency and phase it began on.
            t.setLoopPoints(0, wave.size, -1)
            t.play()
            track = t

            vibrate(context)
            Log.i(TAG, "Alarm siren started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start the alarm siren", e)
            stop(context)
        }
    }

    fun stop(context: Context) {
        try {
            track?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping the siren", e)
        }
        track = null

        // Put the panel back the way it was found.
        volumeBeforeSiren?.let { previous ->
            try {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore the previous alarm volume", e)
            }
        }
        volumeBeforeSiren = null

        try {
            vibrator(context)?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling vibration", e)
        }
    }

    /**
     * One full wail of a two-tone siren, as 16-bit PCM.
     *
     * Generated rather than shipped as an audio file: no asset to bundle, and the pitch and rate
     * are a constant away rather than a re-export.
     *
     * Square rather than sine. A sine at these frequencies is a pleasant flute-like tone; the odd
     * harmonics of a square wave are what make it abrasive, and abrasive is the entire point. The
     * edges are softened very slightly to stop the hard transitions sounding like clipping on a
     * small speaker.
     */
    private fun buildSirenWave(): ShortArray {
        val totalSamples = (SAMPLE_RATE * SWEEP_SECONDS).toInt()
        val wave = ShortArray(totalSamples)
        val amplitude = (Short.MAX_VALUE * 0.82).toInt()

        var phase = 0.0
        for (i in 0 until totalSamples) {
            // Triangle between 0 and 1 and back, so the sweep rises then falls and meets itself
            // cleanly at the loop point.
            val position = i.toDouble() / totalSamples
            val ramp = if (position < 0.5) position * 2.0 else (1.0 - position) * 2.0
            val frequency = FREQ_LOW + (FREQ_HIGH - FREQ_LOW) * ramp

            phase += 2.0 * PI * frequency / SAMPLE_RATE
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            // Square wave, with the sine's own value used near the zero crossings to take the
            // hardest edge off.
            val s = sin(phase)
            val shaped = when {
                s > 0.12 -> 1.0
                s < -0.12 -> -1.0
                else -> s / 0.12
            }
            wave[i] = (shaped * amplitude).toInt().toShort()
        }
        return wave
    }

    /** A long repeating pattern, so a panel face-down or across the room still registers. */
    private fun vibrate(context: Context) {
        try {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            val pattern = longArrayOf(0, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration unavailable", e)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
