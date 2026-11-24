package com.example.api

import android.content.Context
import android.util.Log

/**
 * The one place that decides whether this device may use the home (LAN) address (2026-11-24).
 *
 * The home address is plain HTTP, so anything sent there, the Home Assistant token included, can
 * be read by whatever answers at that address. On the home Wi-Fi that is Home Assistant. On any
 * other network it could be anything. So the ViewModel, the background service, Android Auto, the
 * WebSocket's own failover and every REST request ask here first, and the answer is yes only when
 * the Wi-Fi this device is on has the home Wi-Fi's saved name ([isHomeWifi]).
 *
 * Reading a Wi-Fi name needs location access (Android's rule). Without it, or with location
 * turned off, Android reports no name, and the answer is no: the device uses the main address.
 */
object HomeNetwork {
    private const val TAG = "HomeNetwork"
    const val PREFS = "hvac_settings"
    const val KEY_HOME_SSID = "home_wifi_ssid"
    const val KEY_MAIN_URL = "ha_url"
    const val KEY_HOME_URL = "backup_ha_url"

    @Volatile
    private var appContext: Context? = null

    /** Called by everything that connects, so the REST guard can answer before a ViewModel exists. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** The home Wi-Fi name saved in Settings, or "" when none is. */
    fun savedHomeSsid(context: Context? = appContext): String = try {
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_HOME_SSID, "").orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** The name of the Wi-Fi this device is on, or null when not on Wi-Fi or Android won't say. */
    fun currentSsid(context: Context? = appContext): String? = try {
        val wm = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        cleanSsid(wm?.connectionInfo?.ssid)
    } catch (e: Exception) {
        null
    }

    /** True only on the home Wi-Fi. Any doubt (no context, no saved name, no readable name) is false. */
    fun onHomeWifi(context: Context? = appContext): Boolean = try {
        isHomeWifi(savedHomeSsid(context), currentSsid(context))
    } catch (e: Exception) {
        Log.w(TAG, "Could not tell whether this is the home Wi-Fi", e)
        false
    }

    /** Whether a request may go to [url] right now: always for the main address, only at home for a LAN one. */
    fun mayUse(url: String?, context: Context? = appContext): Boolean =
        !isLocalAddress(url) || onHomeWifi(context)

    /** The main address, by [resolveMainHaUrl], from Settings and the build. */
    fun mainUrl(context: Context? = appContext): String {
        val build = try { com.example.BuildConfig.HA_URL } catch (e: Exception) { "" }
        val saved = try {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_MAIN_URL, null)
        } catch (e: Exception) {
            null
        }
        return resolveMainHaUrl(build, saved)
    }

    /** The home address, by [resolveHomeHaUrl], from Settings and the build. */
    fun homeUrl(context: Context? = appContext): String {
        val build = try { com.example.BuildConfig.HA_BACKUP_URL } catch (e: Exception) { "" }
        val saved = try {
            context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_HOME_URL, null)
        } catch (e: Exception) {
            null
        }
        return resolveHomeHaUrl(build, saved)
    }
}
