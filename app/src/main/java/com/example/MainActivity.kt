package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ui.HvacDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.HvacViewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    if (isGranted) {
      try {
        com.example.service.HvacForegroundService.startService(this)
      } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to start FGS on grant", e)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        try {
          com.example.service.HvacForegroundService.startService(this)
        } catch (e: Exception) {
          android.util.Log.e("MainActivity", "Failed to start FGS", e)
        }
      }
    } else {
      try {
        com.example.service.HvacForegroundService.startService(this)
      } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to start FGS", e)
      }
    }

    enableEdgeToEdge()
    setContent {
      val viewModel: HvacViewModel = viewModel()
      val forceScreenOn by viewModel.forceScreenOn.collectAsState()
      val forceFullScreen by viewModel.forceFullScreen.collectAsState()
      val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()

      LaunchedEffect(forceScreenOn) {
        if (forceScreenOn) {
          window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
      }

      LaunchedEffect(forceFullScreen) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (forceFullScreen) {
          controller.hide(WindowInsetsCompat.Type.systemBars())
          controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
          controller.show(WindowInsetsCompat.Type.systemBars())
        }
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
}
