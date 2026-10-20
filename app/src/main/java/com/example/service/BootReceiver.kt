package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the background service after a reboot and after the app is updated.
 *
 * Without it, a phone that restarted — or any device that had just been sent a new build —
 * carried no connection and no alarm alerts until someone happened to open the app. Both
 * broadcasts are among the few that Android still lets start a foreground service from the
 * background, and `connectedDevice` is not one of the types Android 15 bars from boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> HvacForegroundService.startService(context)
        }
    }
}
