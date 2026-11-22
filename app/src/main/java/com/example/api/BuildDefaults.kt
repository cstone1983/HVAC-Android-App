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

/** The address and token the app starts with. No token means the sign-in screen. */
data class HaStartup(val url: String, val token: String) {
    val signedIn: Boolean get() = token.isNotEmpty()
}

/**
 * A real value built into the app wins, then the saved one, as everywhere else in the app. The
 * `.env.example` placeholders count as absent.
 */
fun resolveHaStartup(buildUrl: String, buildToken: String, savedUrl: String?, savedToken: String?): HaStartup {
    val url = when {
        buildUrl.isNotEmpty() && buildUrl != PLACEHOLDER_HA_URL -> buildUrl
        !savedUrl.isNullOrEmpty() -> savedUrl
        else -> PLACEHOLDER_HA_URL
    }
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
