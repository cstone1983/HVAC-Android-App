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

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
