package com.example

import com.example.api.PLACEHOLDER_HA_TOKEN
import com.example.api.PLACEHOLDER_HA_URL
import com.example.api.layoutApiCheckNeeded
import com.example.api.resolveHaStartup
import com.example.api.signInUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A release from cstone1983/app-builder is built with no `.env` (2026-11-21), so its BuildConfig
 * holds the `.env.example` placeholders. The app must then start on the sign-in screen, sign in
 * through the home address, and check for layout updates without a GitHub token.
 */
class BuildDefaultsTest {

    private val home = "http://10.10.1.116:8123/"

    @Test
    fun `a build with no token and nothing saved is not signed in`() {
        val s = resolveHaStartup(PLACEHOLDER_HA_URL, PLACEHOLDER_HA_TOKEN, null, null)
        assertFalse(s.signedIn)
        assertEquals("", s.token)
        assertEquals(PLACEHOLDER_HA_URL, s.url)
    }

    @Test
    fun `an empty build token is not signed in either`() {
        assertFalse(resolveHaStartup("", "", null, "").signedIn)
    }

    @Test
    fun `a token saved by the sign-in screen is used on the next start`() {
        val s = resolveHaStartup(PLACEHOLDER_HA_URL, PLACEHOLDER_HA_TOKEN, home, "saved-token")
        assertTrue(s.signedIn)
        assertEquals("saved-token", s.token)
        assertEquals(home, s.url)
    }

    @Test
    fun `a build made with a real env file is signed in with no saved values`() {
        val s = resolveHaStartup("https://ha.example/", "built-token", null, null)
        assertTrue(s.signedIn)
        assertEquals("built-token", s.token)
        assertEquals("https://ha.example/", s.url)
    }

    @Test
    fun `a real built-in value wins over a saved one, as before`() {
        val s = resolveHaStartup("https://ha.example/", "built-token", home, "saved-token")
        assertEquals("built-token", s.token)
        assertEquals("https://ha.example/", s.url)
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
