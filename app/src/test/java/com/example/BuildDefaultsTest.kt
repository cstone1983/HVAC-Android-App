package com.example

import com.example.api.DEFAULT_HOME_HA_URL
import com.example.api.DEFAULT_MAIN_HA_URL
import com.example.api.PLACEHOLDER_HA_TOKEN
import com.example.api.PLACEHOLDER_HA_URL
import com.example.api.cleanSsid
import com.example.api.hostOf
import com.example.api.isHomeWifi
import com.example.api.isLocalAddress
import com.example.api.layoutApiCheckNeeded
import com.example.api.resolveHaStartup
import com.example.api.resolveHomeHaUrl
import com.example.api.resolveMainHaUrl
import com.example.api.signInUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A release from cstone1983/app-builder is built with no `.env` (2026-11-21), so its BuildConfig
 * holds the `.env.example` placeholders. The app must then start on the sign-in screen and check
 * for layout updates without a GitHub token.
 *
 * Since 2026-11-24 the two Home Assistant addresses are built in: Nabu Casa as the main one and the
 * Home Assistant VM as the home one, which is used only on the home Wi-Fi, by its saved name.
 */
class BuildDefaultsTest {

    private val home = "http://10.10.11.103:8123/"

    @Test
    fun `a build with no token and nothing saved is not signed in`() {
        val s = resolveHaStartup(PLACEHOLDER_HA_URL, PLACEHOLDER_HA_TOKEN, null, null)
        assertFalse(s.signedIn)
        assertEquals("", s.token)
        assertEquals(DEFAULT_MAIN_HA_URL, s.url)
    }

    @Test
    fun `an empty build token is not signed in either`() {
        assertFalse(resolveHaStartup("", "", null, "").signedIn)
    }

    @Test
    fun `a token saved by the sign-in screen is used on the next start`() {
        val s = resolveHaStartup(PLACEHOLDER_HA_URL, PLACEHOLDER_HA_TOKEN, "https://ha.example/", "saved-token")
        assertTrue(s.signedIn)
        assertEquals("saved-token", s.token)
        assertEquals("https://ha.example/", s.url)
    }

    @Test
    fun `a home address saved as the main one by an old sign-in gives way to Nabu Casa`() {
        val s = resolveHaStartup(PLACEHOLDER_HA_URL, PLACEHOLDER_HA_TOKEN, home, "saved-token")
        assertTrue(s.signedIn)
        assertEquals(DEFAULT_MAIN_HA_URL, s.url)
    }

    @Test
    fun `a build made with a real env file is signed in with no saved values`() {
        val s = resolveHaStartup("https://ha.example/", "built-token", null, null)
        assertTrue(s.signedIn)
        assertEquals("built-token", s.token)
        assertEquals("https://ha.example/", s.url)
    }

    @Test
    fun `a real built-in token wins over a saved one, as before`() {
        val s = resolveHaStartup("https://ha.example/", "built-token", "https://saved.example/", "saved-token")
        assertEquals("built-token", s.token)
    }

    @Test
    fun `an address saved in Settings wins over the built-in one`() {
        val s = resolveHaStartup("https://ha.example/", "built-token", "https://saved.example/", "saved-token")
        assertEquals("https://saved.example/", s.url)
    }

    // --- built-in addresses ---

    @Test
    fun `the built-in addresses are Nabu Casa and the Home Assistant VM`() {
        assertEquals("https://g30gj85ovdsta2q4kletc4pqblr3njlx.ui.nabu.casa/", DEFAULT_MAIN_HA_URL)
        assertEquals("http://10.10.11.103:8123/", DEFAULT_HOME_HA_URL)
        assertFalse(isLocalAddress(DEFAULT_MAIN_HA_URL))
        assertTrue(isLocalAddress(DEFAULT_HOME_HA_URL))
    }

    @Test
    fun `a fresh install with nothing saved gets both built-in addresses`() {
        assertEquals(DEFAULT_MAIN_HA_URL, resolveMainHaUrl(PLACEHOLDER_HA_URL, null))
        assertEquals(DEFAULT_MAIN_HA_URL, resolveMainHaUrl("", ""))
        assertEquals(DEFAULT_MAIN_HA_URL, resolveMainHaUrl(null, " "))
        assertEquals(DEFAULT_HOME_HA_URL, resolveHomeHaUrl("", null))
        assertEquals(DEFAULT_HOME_HA_URL, resolveHomeHaUrl(null, ""))
    }

    @Test
    fun `addresses saved in Settings still win`() {
        assertEquals("https://other.example/", resolveMainHaUrl(PLACEHOLDER_HA_URL, "https://other.example/"))
        assertEquals("http://10.10.11.50:8123/", resolveHomeHaUrl("", "http://10.10.11.50:8123/"))
        assertEquals("http://10.10.11.50:8123/", resolveHomeHaUrl("http://10.10.11.103:8123/", "http://10.10.11.50:8123/"))
    }

    @Test
    fun `a development build's own addresses come before the built-in ones`() {
        assertEquals("https://ha.example/", resolveMainHaUrl("https://ha.example/", null))
        assertEquals("http://192.168.1.20:8123/", resolveHomeHaUrl("http://192.168.1.20:8123/", null))
    }

    @Test
    fun `a LAN address is never the main address, saved or built in`() {
        assertEquals(DEFAULT_MAIN_HA_URL, resolveMainHaUrl(PLACEHOLDER_HA_URL, "http://10.10.11.103:8123/"))
        assertEquals(DEFAULT_MAIN_HA_URL, resolveMainHaUrl("http://192.168.1.20:8123/", null))
        assertEquals("https://ha.example/", resolveMainHaUrl("https://ha.example/", "http://homeassistant.localdomain:8123"))
    }

    @Test
    fun `local addresses are recognised`() {
        listOf(
            "http://10.10.11.103:8123/", "10.0.0.1", "http://192.168.1.10:8123", "http://172.16.0.5",
            "http://172.31.255.1:8123", "http://127.0.0.1:8123", "http://169.254.1.1", "http://100.100.1.1",
            "http://localhost:8123", "http://homeassistant:8123", "http://homeassistant.local:8123",
            "http://homeassistant.localdomain:8123/", "http://ha.lan", "http://ha.home.arpa",
            "http://[::1]:8123/", "http://[fd00::1]:8123", "http://[fe80::1]"
        ).forEach { assertTrue(it, isLocalAddress(it)) }
    }

    @Test
    fun `public addresses are not local`() {
        listOf(
            DEFAULT_MAIN_HA_URL, "https://ha.example.com", "http://172.32.0.1", "http://8.8.8.8",
            "https://user:pass@ha.example.com:8443/path", "https://[2001:db8::1]/", "http://192.169.1.1"
        ).forEach { assertFalse(it, isLocalAddress(it)) }
        assertFalse(isLocalAddress(""))
        assertFalse(isLocalAddress(null))
    }

    @Test
    fun `host is read from an address with or without a scheme`() {
        assertEquals("10.10.11.103", hostOf("http://10.10.11.103:8123/api"))
        assertEquals("ha.example.com", hostOf(" HA.Example.com:8123 "))
        assertEquals("ha.example.com", hostOf("https://u:p@ha.example.com/x?y#z"))
        assertEquals("::1", hostOf("http://[::1]:8123/"))
        assertNull(hostOf("  "))
    }

    // --- home Wi-Fi ---

    @Test
    fun `home is the saved Wi-Fi name and only that`() {
        assertTrue(isHomeWifi("StoneHome", "StoneHome"))
        assertTrue(isHomeWifi("StoneHome", "\"StoneHome\""))
        assertFalse(isHomeWifi("StoneHome", "Neighbours"))
        assertFalse(isHomeWifi("StoneHome", "stonehome"))
    }

    @Test
    fun `with no saved name the home address is never used, wall panels included`() {
        assertFalse(isHomeWifi("", "StoneHome"))
        assertFalse(isHomeWifi(null, "StoneHome"))
        assertFalse(isHomeWifi("  ", "StoneHome"))
    }

    @Test
    fun `a Wi-Fi name Android won't give is not home`() {
        assertFalse(isHomeWifi("StoneHome", null))
        assertFalse(isHomeWifi("StoneHome", "<unknown ssid>"))
        assertFalse(isHomeWifi("<unknown ssid>", "<unknown ssid>"))
        assertFalse(isHomeWifi("StoneHome", ""))
    }

    @Test
    fun `Wi-Fi names are cleaned of Android's quotes`() {
        assertEquals("StoneHome", cleanSsid("\"StoneHome\""))
        assertEquals("Stone Home", cleanSsid("  Stone Home "))
        assertNull(cleanSsid("<unknown ssid>"))
        assertNull(cleanSsid("\"\""))
        assertNull(cleanSsid(null))
    }

    @Test
    fun `sign-in uses the home address when the build has no main address`() {
        assertEquals(home, signInUrl(PLACEHOLDER_HA_URL, home))
        assertEquals(home, signInUrl("", home))
    }

    @Test
    fun `sign-in uses the main address when there is one`() {
        assertEquals("https://ha.example/", signInUrl("https://ha.example/", home))
    }

    @Test
    fun `sign-in has nothing to try with neither address`() {
        assertEquals("", signInUrl(PLACEHOLDER_HA_URL, " "))
    }

    @Test
    fun `the first background layout check asks the API`() {
        assertTrue(layoutApiCheckNeeded("{\"version\":\"1\"}", null))
    }

    @Test
    fun `an unchanged layout file keeps the background check off the API`() {
        assertFalse(layoutApiCheckNeeded("{\"version\":\"1\"}", "{\"version\":\"1\"}"))
    }

    @Test
    fun `a changed layout file sends the background check to the API`() {
        assertTrue(layoutApiCheckNeeded("{\"version\":\"2\"}", "{\"version\":\"1\"}"))
    }

    @Test
    fun `a layout file that could not be read falls back to the API`() {
        assertTrue(layoutApiCheckNeeded(null, "{\"version\":\"1\"}"))
    }
}
