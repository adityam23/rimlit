package app.rimlit.model

/** Minutes in a day: quiet hours and the clock count minutes from midnight, in `0 until MINUTES_PER_DAY`. */
const val MINUTES_PER_DAY = 24 * 60

/** Which glows quiet hours mute. */
enum class QuietScope {
    /** Every glow. */
    ALL,

    /** Only the lock-screen glow, the one that wakes the screen. */
    SCREEN_OFF,
}

/**
 * A daily window with no glows (ringing calls and alarms still glow): from [startMinute] (inclusive) to [endMinute]
 * (exclusive), wrapping past midnight when the end is earlier. Equal ends mean the whole day.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val scope: QuietScope = QuietScope.ALL,
) {
    /** A glow at [minuteOfDay] is muted; [lockScreen]: it would show on the lock-screen surface. */
    fun mutes(minuteOfDay: Int, lockScreen: Boolean): Boolean =
        enabled && (scope == QuietScope.ALL || lockScreen) && covers(minuteOfDay)

    private fun covers(minute: Int): Boolean = when {
        startMinute == endMinute -> true
        startMinute < endMinute -> minute in startMinute until endMinute
        else -> minute >= startMinute || minute < endMinute
    }
}
