package com.example.api

/**
 * What a build carries, and what it does not (2026-11-21).
 *
 * A build made on the development PC reads `.env`, so it has the Home Assistant address and token
 * and a GitHub token baked into BuildConfig. A release from cstone1983/app-builder has no `.env`:
 * its BuildConfig holds the `.env.example` values, where both tokens are placeholders. Those
 * builds get the Home Assistant token from the sign-in screen and use GitHub without a token.
 */
const val PLACEHOLDER_HA_URL = "https://localhost/"
const val PLACEHOLDER_HA_TOKEN = "YOUR_HOME_ASSISTANT_TOKEN"

/**
 * The two Home Assistant addresses, built into the app (2026-11-24).
 *
 * app-builder builds with no `.env`, so a release used to start with no main address at all and
 * a fresh install had to be typed into. These are the house's own addresses, so every build now
 * knows them. A value saved in Settings still wins over them.
 *
 * The main address is Home Assistant Cloud (Nabu Casa) and works from anywhere. The home address
 * is the Home Assistant VM on the LAN, over plain HTTP, so it is used only on the home Wi-Fi (see
 * [isHomeWifi]).
 */
const val DEFAULT_MAIN_HA_URL = "https://g30gj85ovdsta2q4kletc4pqblr3njlx.ui.nabu.casa/"
const val DEFAULT_HOME_HA_URL = "http://10.10.11.103:8123/"

/**
 * Whether an address only makes sense inside a home network: a private, loopback or link-local
 * IPv4 address, or a name that only a LAN can resolve (no dot, `.local`, `.localdomain`, `.lan`,
 * `.home.arpa`, `.internal`). The token goes to such an address only on the home Wi-Fi.
 */
fun isLocalAddress(url: String?): Boolean {
    val host = hostOf(url) ?: return false
    if (host == "localhost") return true
    val octets = host.split('.')
    if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
        val a = octets[0].toInt()
        val b = octets[1].toInt()
        return a == 10 || a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }
    if (host.contains(':')) {
        // IPv6 literal: loopback, unique-local (fc00::/7) and link-local (fe80::/10).
        return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8") ||
            host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
    }
    if (!host.contains('.')) return true
    return listOf(".local", ".localdomain", ".lan", ".home.arpa", ".internal", ".localhost").any { host.endsWith(it) }
}

/** The lower-case host of an address, with or without a scheme. Null when there is none. */
fun hostOf(url: String?): String? {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val afterScheme = trimmed.substringAfter("://", trimmed)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@')
    val host = if (authority.startsWith("[")) {
        authority.substringAfter('[').substringBefore(']')
    } else {
        authority.substringBefore(':')
    }
    return host.lowercase().trim().takeIf { it.isNotEmpty() }
}

/** A value that is really there: not blank and not the `.env.example` placeholder. */
private fun realUrl(url: String?): String? =
    url?.trim()?.takeIf { it.isNotEmpty() && it != PLACEHOLDER_HA_URL }

/**
 * The main (away from home) address: the one saved in Settings, then one built in from `.env`,
 * then [DEFAULT_MAIN_HA_URL].
 *
 * A LAN address never counts as the main address. The sign-in screen on a release used to sign in
 * at the home address and save it as the main one, which left that device sending its token to
 * the home address on whatever network it was on. Such a saved value is skipped, so the device
 * goes back to Nabu Casa away from home and keeps the home address for the home Wi-Fi.
 */
fun resolveMainHaUrl(buildUrl: String?, savedUrl: String?): String =
    realUrl(savedUrl)?.takeIf { !isLocalAddress(it) }
        ?: realUrl(buildUrl)?.takeIf { !isLocalAddress(it) }
        ?: DEFAULT_MAIN_HA_URL

/** The home address: the one saved in Settings, then one built in from `.env`, then [DEFAULT_HOME_HA_URL]. */
fun resolveHomeHaUrl(buildUrl: String?, savedUrl: String?): String =
    realUrl(savedUrl) ?: realUrl(buildUrl) ?: DEFAULT_HOME_HA_URL

/**
 * A Wi-Fi name as Android reports it, cleaned: no surrounding quotes, and null for Android's
 * "<unknown ssid>" (what it says when the app has no location access or location is off).
 */
fun cleanSsid(raw: String?): String? {
    val s = raw?.trim()?.removeSurrounding("\"")?.trim().orEmpty()
    return s.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
}

/**
 * Whether this device is on the home Wi-Fi: the Wi-Fi it is on has exactly the saved home name.
 *
 * This is the only test, on every device (the user's rule, 2026-11-24: "should only work on my
 * home network not any network"). The wall panels used to fall back to "my address is in the home
 * address's range", which any other network using the same range also passes. With no saved name,
 * or a name Android won't give, the answer is no, and the device uses the main address.
 */
fun isHomeWifi(savedHomeSsid: String?, currentSsid: String?): Boolean {
    val home = cleanSsid(savedHomeSsid) ?: return false
    val current = cleanSsid(currentSsid) ?: return false
    return home == current
}

/** The address and token the app starts with. No token means the sign-in screen. */
data class HaStartup(val url: String, val token: String) {
    val signedIn: Boolean get() = token.isNotEmpty()
}

/**
 * For the token, a real value built into the app wins, then the saved one, as everywhere else in
 * the app. The `.env.example` placeholders count as absent.
 */
fun resolveHaStartup(buildUrl: String, buildToken: String, savedUrl: String?, savedToken: String?): HaStartup {
    // The address follows [resolveMainHaUrl], like every other part of the app, so there is
    // always one: Settings, then `.env`, then the built-in Nabu Casa address.
    val url = resolveMainHaUrl(buildUrl, savedUrl)
    val token = when {
        buildToken.isNotEmpty() && buildToken != PLACEHOLDER_HA_TOKEN -> buildToken
        !savedToken.isNullOrEmpty() -> savedToken
        else -> ""
    }
    return HaStartup(url, token)
}

/**
 * The address the sign-in screen checks the token against: the main address, or the home (LAN)
 * address when the build has no main address and none has been saved. Empty when there is neither.
 */
fun signInUrl(mainUrl: String, homeUrl: String): String =
    if (mainUrl.isNotBlank() && mainUrl != PLACEHOLDER_HA_URL) mainUrl else homeUrl.trim()

/**
 * Whether a background layout check on a device with no GitHub token has to ask GitHub's API.
 *
 * Without a token the API allows 60 requests an hour for the whole house (it counts by IP
 * address), and three devices checking every five minutes would use more than that. The layout
 * file itself is not behind that limit. So the background check reads the file from the branch
 * first, and only asks the API which commit it is when the file differs from the one seen at the
 * last finished check. [branchLayout] is null when the file could not be read.
 */
fun layoutApiCheckNeeded(branchLayout: String?, lastCheckedLayout: String?): Boolean =
    branchLayout == null || branchLayout != lastCheckedLayout
