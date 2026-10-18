package com.example.model

/**
 * What kind of thing this install is running on, and therefore how it behaves when the alarm
 * goes off.
 *
 * This exists because the alternative was turning out badly. Every alarm behaviour added — the
 * siren, the countdown beep, the screen wake, the notification — arrived with its own question
 * of *which devices should do this*, and each was answered separately: one by a hardcoded list
 * of model numbers, one by a preference, one by nothing at all. Four settings that are really
 * one decision, phrased four ways, each able to drift out of agreement with the others.
 *
 * There is only one decision. Is this thing bolted to a wall, or is it in somebody's pocket?
 * Everything else follows from the answer, and the answer is obvious to whoever is holding it.
 *
 * The properties below are deliberately derived rather than stored. A mode that can be
 * overridden behaviour-by-behaviour is just the old pile of flags with a label on top.
 */
enum class DeviceMode {

    /** Bolted to a wall, mains powered, nobody is carrying it. */
    PANEL,

    /** In a pocket, and may be in a meeting. */
    PHONE;

    /**
     * Sound the full siren until the alarm is disarmed, at forced maximum volume.
     *
     * The dividing line. A wall panel exists to be heard from another room; a phone that starts
     * wailing in a meeting gets the whole feature switched off, and then it is not there on the
     * night it matters.
     */
    val soundsSiren: Boolean get() = this == PANEL

    /**
     * Turn the screen on without being touched, so the keypad is already up when someone walks
     * in to disarm.
     *
     * Panels only. A phone that lights itself up in a pocket is wasting battery and dialling
     * things by accident; the triggered notification's full-screen intent gets it in front of
     * you properly when you pick it up.
     */
    val wakesScreenForAlarm: Boolean get() = this == PANEL

    /**
     * Tick through the entry and exit countdown.
     *
     * Both. The phone is often the only thing on you when you come through the door, and a
     * handful of soft pips over sixty seconds is nothing like a siren — it stops a false alarm
     * rather than announcing one.
     */
    val beepsCountdown: Boolean get() = true

    /**
     * Post notifications at all — the triggered alert, and the quieter status ones.
     *
     * Phones only, and the reasoning is the same for both kinds. A notification is for
     * someone who is not looking at the thing it concerns. A wall panel is the thing it
     * concerns: it is already showing the state in large type, and during an alarm it is
     * also sirening and holding the keypad up. Telling it about itself adds nothing and
     * costs a buzz in a room nobody is in.
     *
     * The phone is the opposite case, and is usually the only device actually *with*
     * whoever needs to know.
     */
    val notifiesOnTrigger: Boolean get() = this == PHONE

    /**
     * Post the quiet status notifications — armed, disarmed, and whatever gets added next.
     *
     * Same rule, same reason. Which events these cover is configuration rather than code,
     * so the list can grow without a release: see `AlarmConfig.notifyEvents`.
     */
    val showsStatusNotifications: Boolean get() = this == PHONE

    /**
     * Whether the screen goes back to the home page after a stretch untouched.
     *
     * A wall panel should: whoever walks up next expects the house at a glance, not the page the
     * last person left open. A phone should not — it is one person's, picked up and put down
     * mid-task, and coming back to find the solar tab swapped for home is just annoying.
     */
    val returnsHomeWhenIdle: Boolean get() = this == PANEL

    val label: String
        get() = when (this) {
            PANEL -> "Wall panel"
            PHONE -> "Phone"
        }

    val summary: String
        get() = when (this) {
            PANEL -> "Sirens at full volume until disarmed, wakes its own screen, and beeps " +
                "the countdown. Sends no notifications."
            PHONE -> "Beeps the countdown and sends notifications, including through Do Not " +
                "Disturb. Never sirens."
        }

    companion object {

        fun from(name: String?): DeviceMode? = values().firstOrNull { it.name == name }

        /**
         * The mode to use when nothing has been chosen yet.
         *
         * Three sources, in order of how much they actually say about intent:
         *
         *  1. An explicit choice in settings, which is the point of the whole thing.
         *  2. The old per-device siren switch, so a panel somebody had already turned on does
         *     not quietly revert on upgrade. Carrying it across matters more than it looks:
         *     silently resetting an alarm behaviour is the kind of regression nobody notices
         *     until the night it was needed.
         *  3. The model number, which is only ever a guess, but a good one and better than
         *     asking on first launch.
         *
         * Pure on purpose — no Android types — so the precedence can be reasoned about and
         * tested without a device attached.
         */
        fun resolve(
            stored: String?,
            legacySirenEnabled: Boolean?,
            isWallPanel: Boolean
        ): DeviceMode =
            from(stored)
                ?: legacySirenEnabled?.let { if (it) PANEL else PHONE }
                ?: if (isWallPanel) PANEL else PHONE
    }
}
