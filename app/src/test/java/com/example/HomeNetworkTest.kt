package com.example

import android.content.Context
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import com.example.api.DEFAULT_HOME_HA_URL
import com.example.api.DEFAULT_MAIN_HA_URL
import com.example.api.HomeNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWifiInfo

/**
 * The home address only on the home Wi-Fi (2026-11-24), as the ViewModel, the background service,
 * Android Auto, the WebSocket failover and the REST guard all see it through [HomeNetwork].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeNetworkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences(HomeNetwork.PREFS, Context.MODE_PRIVATE)

    private fun onWifi(name: String) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = ShadowWifiInfo.newInstance()
        shadowOf(info).setSSID(name)
        shadowOf(wm).setConnectionInfo(info)
    }

    @Test
    fun `on the saved home Wi-Fi the home address may be used`() {
        prefs().edit().putString(HomeNetwork.KEY_HOME_SSID, "StoneHome").commit()
        onWifi("StoneHome")
        assertTrue(HomeNetwork.onHomeWifi(context))
        assertTrue(HomeNetwork.mayUse(DEFAULT_HOME_HA_URL, context))
    }

    @Test
    fun `on another Wi-Fi only the main address may be used`() {
        prefs().edit().putString(HomeNetwork.KEY_HOME_SSID, "StoneHome").commit()
        onWifi("CoffeeShop")
        assertFalse(HomeNetwork.onHomeWifi(context))
        assertFalse(HomeNetwork.mayUse(DEFAULT_HOME_HA_URL, context))
        assertFalse(HomeNetwork.mayUse("http://192.168.1.10:8123/api/states", context))
        assertTrue(HomeNetwork.mayUse(DEFAULT_MAIN_HA_URL + "api/states", context))
    }

    @Test
    fun `with no home Wi-Fi saved the home address is never used`() {
        onWifi("StoneHome")
        assertFalse(HomeNetwork.onHomeWifi(context))
        assertFalse(HomeNetwork.mayUse(DEFAULT_HOME_HA_URL, context))
    }

    @Test
    fun `nothing saved gives the built-in addresses`() {
        assertEquals(DEFAULT_MAIN_HA_URL, HomeNetwork.mainUrl(context))
        assertEquals(DEFAULT_HOME_HA_URL, HomeNetwork.homeUrl(context))
    }

    @Test
    fun `saved addresses win, but a saved LAN main address does not`() {
        prefs().edit()
            .putString(HomeNetwork.KEY_MAIN_URL, "http://10.10.11.103:8123/")
            .putString(HomeNetwork.KEY_HOME_URL, "http://10.10.11.50:8123/")
            .commit()
        assertEquals(DEFAULT_MAIN_HA_URL, HomeNetwork.mainUrl(context))
        assertEquals("http://10.10.11.50:8123/", HomeNetwork.homeUrl(context))

        prefs().edit().putString(HomeNetwork.KEY_MAIN_URL, "https://ha.example.com/").commit()
        assertEquals("https://ha.example.com/", HomeNetwork.mainUrl(context))
    }

    @Test
    fun `no context means no home address`() {
        assertFalse(HomeNetwork.onHomeWifi(null))
        assertTrue(HomeNetwork.mayUse(DEFAULT_MAIN_HA_URL, null))
        assertFalse(HomeNetwork.mayUse(DEFAULT_HOME_HA_URL, null))
    }
}
