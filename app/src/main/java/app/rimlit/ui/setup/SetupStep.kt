package app.rimlit.ui.setup

import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.isGranted

/**
 * The guided first launch's steps, in order, each with the grants it asks for ([kinds]). Only notification access is
 * [required]: without it Rimlit sees nothing, so there is no way past it; every other step can be put off, and what it
 * asks for then shows on Home.
 */
internal enum class SetupStep(val kinds: List<PermissionKind> = emptyList(), val required: Boolean = false) {
    WELCOME,
    NOTIFICATION_ACCESS(listOf(PermissionKind.NOTIFICATION_ACCESS), required = true),

    /** Either way of glowing over other apps: the accessibility glow, recommended, or Display over other apps. */
    ON_SCREEN(listOf(PermissionKind.ACCESSIBILITY, PermissionKind.OVERLAY)),

    /** Both grants the screen-off wake needs: posting its notification, then its full-screen intent. */
    SCREEN_OFF(listOf(PermissionKind.NOTIFICATIONS, PermissionKind.FULL_SCREEN_INTENT)),
    BATTERY(listOf(PermissionKind.BATTERY_OPTIMIZATION)),
    DEVICE_FIT,
    FIRST_LOOK,
    TEST,
    ;

    /**
     * Whether what this step asks for is granted in [statuses]: either way of glowing on screen, and every grant of the
     * other steps; true for a step that asks for none.
     */
    fun granted(statuses: List<PermissionStatus>): Boolean = when (this) {
        ON_SCREEN -> kinds.any(statuses::isGranted)
        else -> kinds.all(statuses::isGranted)
    }

    /** How the user moves on from this step, given the grants in [statuses]. */
    fun onward(statuses: List<PermissionStatus>): Onward = when {
        this == WELCOME -> Onward.START
        this == entries.last() -> Onward.FINISH
        granted(statuses) -> Onward.NEXT
        required -> Onward.WAIT
        else -> Onward.NOT_NOW
    }

    /** The step before this one; `null` on the first, where back leaves the app. */
    val previous: SetupStep? get() = entries.getOrNull(ordinal - 1)

    /** The step after this one; `null` on the last. */
    val next: SetupStep? get() = entries.getOrNull(ordinal + 1)

    /** How far along the flow this step is, from just above 0 to 1. */
    val progress: Float get() = (ordinal + 1f) / entries.size
}

/** The button that moves on from a step. */
internal enum class Onward {
    /** The welcome's "Get started". */
    START,

    /** Nothing missing, or nothing asked: on to the next step. */
    NEXT,

    /** A grant put off: on to the next step, and the grant shows on Home. */
    NOT_NOW,

    /** A required grant still missing: no way on until it is granted. */
    WAIT,

    /** The last step: setup is done. */
    FINISH,
}
