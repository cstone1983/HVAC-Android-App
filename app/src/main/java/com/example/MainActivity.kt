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
