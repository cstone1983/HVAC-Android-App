package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Sounds the wall panels when the alarm goes off.
 *
 * Deliberately a wall-panel feature. A phone in a pocket does not need to become a siren, and a
 * phone that does so in a meeting is a reason to turn the whole thing off — see [isWallPanel].
 *
 * Three things have to be true for this to be heard from another room, and all three are easy to
 * get wrong:
 *
 *  - **The alarm stream, not the music or notification stream.** `USAGE_ALARM` is the only one
 *    that keeps sounding through silent mode and Do Not Disturb. Playing a siren on the media
 *    stream means a muted tablet stays silent during a break-in.
 *  - **Volume forced to maximum, and put back afterwards.** The panel's alarm volume is whatever
 *    it happened to be left at, which on a device nobody plays audio on is usually nothing. The
 *    previous level is restored on stop so this cannot quietly become permanent.
 *  - **Looped.** The stock alarm tone is a few seconds long; a single burst from the next room
 *    reads as a notification.
 */
object AlarmSiren {

    private const val TAG = "AlarmSiren"

    private var player: MediaPlayer? = null
    private var volumeBeforeSiren: Int? = null

    /**
     * True on the two Galaxy Tabs, false on the phone.
     *
     * Decided by smallest screen width rather than a model allow-list: 600dp is Android's own
     * tablet threshold, it needs no maintenance when a panel is replaced, and getting it wrong
     * fails in the safe direction — an unrecognised device simply does not become a siren.
     */
    fun isWallPanel(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600

    /** Already sounding? Used to avoid restarting the tone on every state push. */
    val isSounding: Boolean
        get() = player?.isPlaying == true

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
                    // and the app has no policy access. Carry on and sound at whatever level the
                    // panel is set to -- quieter than intended beats silence.
                    Log.w(TAG, "Could not raise alarm volume; sounding at the current level", e)
                }
            }

            val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return

            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, tone)
                isLooping = true
                prepare()
                start()
            }
            vibrate(context)
            Log.i(TAG, "Alarm siren started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start the alarm siren", e)
            stop(context)
        }
    }

    fun stop(context: Context) {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping the siren", e)
        }
        player = null

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
