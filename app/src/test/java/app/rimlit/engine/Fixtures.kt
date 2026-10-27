package app.rimlit.engine

import android.app.NotificationManager
import app.rimlit.model.AutoColorSource
import app.rimlit.model.AutoColors
import app.rimlit.model.ColorMode
import app.rimlit.model.GlowSettings

/** Small, representative inputs shared by the model check and the scenario tests. Never trim them to save time. */
internal object Fixtures {

    const val OTHER_APP = "com.example.chat"
    const val OWN_APP = "app.rimlit"

    fun post(
        key: String,
        fullScreenIntent: Boolean = false,
        onlyAlertOnce: Boolean = false,
        importance: Int? = NotificationManager.IMPORTANCE_DEFAULT,
        ongoing: Boolean = fullScreenIntent,
        own: Boolean = false,
        test: Boolean = false,
    ) = Post(
        key = key,
        packageName = if (own) OWN_APP else OTHER_APP,
        isOwn = own,
        isTest = test,
        hasFullScreenIntent = fullScreenIntent,
        isOngoing = ongoing,
        group = GroupRole.NONE,
        groupAlert = GroupAlert.ALL,
        onlyAlertOnce = onlyAlertOnce,
        importance = importance,
        passesDnd = true,
    )

    /** Auto colours, so posts go through pending extraction; not the default ones, so the effect must carry them. */
    val AUTO = GlowSettings(enabled = true, autoColors = AutoColors(AutoColorSource.NOTIFICATION_IMAGE, count = 2))

    /** Custom colours, so posts show at once. */
    val CUSTOM = AUTO.copy(colorMode = ColorMode.CUSTOM)

    val SETTINGS = listOf(
        AUTO,
        CUSTOM,
        AUTO.copy(enabled = false),
        AUTO.copy(showWhenLocked = false),
        AUTO.copy(showWhenUnlocked = false),
    )

    val POSTS = listOf(
        post("a"),
        post("a", fullScreenIntent = true),
        post("a", onlyAlertOnce = true),
        post("a", importance = NotificationManager.IMPORTANCE_LOW),
        // Also an answered call: re-posted ongoing, without its full-screen intent.
        post("a", ongoing = true),
        post("a", own = true),
        post("b"),
        post("b", fullScreenIntent = true),
        // A reminder left in the shade: a full-screen intent, but not ongoing, so not ringing.
        post("b", fullScreenIntent = true, ongoing = false),
        // The lock-screen trampoline: Rimlit's own full-screen intent, which is not a call.
        post("b", fullScreenIntent = true, ongoing = false, own = true),
        post("b", onlyAlertOnce = true),
        post("b", own = true, test = true),
    )

    val EXTRACTED = listOf(0xFF336699.toInt())

    val PREVIEW_SPEC = CUSTOM.previewSpec()

    val ENVIRONMENTS = listOf(true, false).flatMap { screenOn ->
        listOf(true, false).flatMap { locked ->
            listOf(true, false).map { granted -> Environment(screenOn, locked, granted) }
        }
    }

    val UNLOCKED = Environment(screenOn = true, locked = false, canWakeScreen = true)
    val LOCK_SCREEN = Environment(screenOn = true, locked = true, canWakeScreen = true)
    val SCREEN_OFF = Environment(screenOn = false, locked = true, canWakeScreen = true)

    fun initial(settings: GlowSettings = AUTO, environment: Environment = UNLOCKED, accessibility: Boolean = false) =
        GlowState(settings = settings, environment = environment, accessibilityOverlay = accessibility)
}
