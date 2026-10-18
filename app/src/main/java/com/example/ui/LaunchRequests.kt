package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the app was opened to do, when it was opened from the notification rather than the
 * launcher. MainActivity fills these from the intent; the dashboard acts on them and clears them.
 *
 * Kept outside the view model on purpose: the intent can arrive before the view model's first
 * state does, and the dashboard waits for the zones before opening one.
 */
object LaunchRequests {
    /** A zone key: open the zones tab and that zone's popup. */
    val openZone = MutableStateFlow<String?>(null)

    /** A house mode that crosses heating and cooling: ask the usual confirmation in the app. */
    val confirmMode = MutableStateFlow<String?>(null)

    const val EXTRA_OPEN_ZONE = "com.example.extra.OPEN_ZONE"
    const val EXTRA_CONFIRM_MODE = "com.example.extra.CONFIRM_MODE"
}
