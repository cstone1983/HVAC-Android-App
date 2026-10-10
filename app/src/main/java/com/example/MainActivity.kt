package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.HvacForegroundService
import com.example.ui.HvacDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.HvacViewModel

class MainActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenOnForced = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                HvacForegroundService.startService(this)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to start FGS on grant", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety handler to log and prevent silent tablet crash loops
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MainActivity", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize notification permissions and background service safely
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                tryStartForegroundService()
            }
        } else {
            tryStartForegroundService()
        }

        enableEdgeToEdge()

        // Read initial screen on preference
        val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
        val initialForceScreenOn = sharedPrefs.getBoolean("force_screen_on", true)
        applyKeepScreenOn(initialForceScreenOn)

        setContent {
            val viewModel: HvacViewModel = viewModel()
            val forceScreenOn by viewModel.forceScreenOn.collectAsState()
            val forceFullScreen by viewModel.forceFullScreen.collectAsState()
            val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()

            val view = LocalView.current

            LaunchedEffect(forceScreenOn) {
                applyKeepScreenOn(forceScreenOn)
            }

            // Wake the panel when the alarm wants the keypad in front of someone.
            //
            // The keypad already raises itself on entry delay and on trigger, but raising a
            // dialog on a screen that has dimmed, blanked or locked shows nobody anything — and
            // entry delay is exactly the moment where a dark panel is useless. Sixty seconds is
            // the whole budget, and the panels sit in rooms somebody has just walked into.
            //
            // This does not depend on the force-screen-on setting: that keeps a lit screen lit,
            // which is a different thing from lighting a dark one.
            val alarmWantsAttention by viewModel.showAlarmKeypad.collectAsState()
            LaunchedEffect(alarmWantsAttention) {
                if (alarmWantsAttention) wakeForAlarm()
            }

            // Sound the panel when the alarm is actually going off.
            //
            // Only on TRIGGERED, never on the entry delay: those sixty seconds are for the person
            // who just walked in to disarm, and a siren over the top of that is noise while they
            // type rather than information. The keypad and the screen wake already cover entry.
            //
            // Gated on a per-device preference that defaults on for the wall panels and off for
            // the phone. Stopping is unconditional — whatever the preference says now, anything
            // still sounding gets shut off when the phase leaves TRIGGERED, so turning the
            // setting off mid-siren cannot strand it.
            val alarmPhase by viewModel.alarmState.collectAsState()
            val sirenEnabled by viewModel.alarmSirenEnabled.collectAsState()
            LaunchedEffect(alarmPhase.phase, sirenEnabled) {
                val goingOff = alarmPhase.phase == com.example.model.AlarmPhase.TRIGGERED
                if (goingOff && sirenEnabled) {
                    com.example.service.AlarmSiren.start(applicationContext)
                } else {
                    com.example.service.AlarmSiren.stop(applicationContext)
                }
            }

            DisposableEffect(Unit) {
                // A siren that outlives the screen it belongs to is a tablet nobody can silence.
                onDispose { com.example.service.AlarmSiren.stop(applicationContext) }
            }

            DisposableEffect(forceScreenOn, view) {
                try {
                    view.keepScreenOn = forceScreenOn
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to set view keepScreenOn", e)
                }
                onDispose { }
            }

            LaunchedEffect(forceFullScreen) {
                applyFullScreen(forceFullScreen)
            }

            MyApplicationTheme(darkTheme = darkModeEnabled) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_scaffold")
                ) { innerPadding ->
                    HvacDashboard(
                        viewModel = viewModel,
                        modifier = Modifier
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If the panel is mid entry-delay or going off, that is what the app was opened
        // for. Re-assert the keypad rather than landing on the dashboard.
        HvacViewModel.getInstance()?.openAlarmKeypadIfPinNeeded()
        val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
        val shouldKeepScreenOn = sharedPrefs.getBoolean("force_screen_on", true)
        applyKeepScreenOn(shouldKeepScreenOn)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
            val shouldKeepScreenOn = sharedPrefs.getBoolean("force_screen_on", true)
            applyKeepScreenOn(shouldKeepScreenOn)
        }
    }

    override fun onPause() {
        super.onPause()
        releaseWakeLock()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun tryStartForegroundService() {
        try {
            HvacForegroundService.startService(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start FGS", e)
        }
    }

    /**
     * Brings the screen up for an alarm, over the lock screen if need be.
     *
     * Three mechanisms because no single one covers every case on these panels:
     *  - `setShowWhenLocked` / `setTurnScreenOn` are the supported route on API 27+, but only act
     *    on an activity that is already resumed.
     *  - `FLAG_TURN_SCREEN_ON` with `FLAG_DISMISS_KEYGUARD` covers the window as it comes forward.
     *  - A short `ACQUIRE_CAUSES_WAKEUP` lock actually turns a blanked display back on. It is
     *    given a 2-minute timeout rather than being held: this is a nudge, not a mode, and the
     *    longest thing it has to outlast is a 60-second entry delay.
     *
     * Deliberately additive — none of it clears the long-lived keep-screen-on lock, so the normal
     * wall-panel behaviour is unchanged once the alarm is dealt with.
     */
    private fun wakeForAlarm() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HvacDeck:AlarmWake"
            )?.apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 1000L)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to wake the screen for the alarm", e)
        }
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        isScreenOnForced = enabled
        try {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                try {
                    window.decorView.keepScreenOn = true
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "decorView keepScreenOn error", e)
                }
                acquireWakeLock()
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                try {
                    window.decorView.keepScreenOn = false
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "decorView clear keepScreenOn error", e)
                }
                releaseWakeLock()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to apply keepScreenOn flags", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null) {
                    // Use SCREEN_BRIGHT_WAKE_LOCK | ON_AFTER_RELEASE with fallback to PARTIAL_WAKE_LOCK
                    wakeLock = try {
                        @Suppress("DEPRECATION")
                        powerManager.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                            "HvacDeck:StayOnWakeLock"
                        ).apply {
                            setReferenceCounted(false)
                        }
                    } catch (e: Exception) {
                        powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "HvacDeck:StayOnPartialLock"
                        ).apply {
                            setReferenceCounted(false)
                        }
                    }
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(24 * 60 * 60 * 1000L) // 24h safety timeout
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Could not acquire WakeLock on this device", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Error releasing WakeLock", e)
        }
    }

    private fun applyFullScreen(enabled: Boolean) {
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to apply full screen mode via WindowInsetsController", e)
        }
    }
}
