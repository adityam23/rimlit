package app.rimlit.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import app.rimlit.engine.PreviewTarget
import app.rimlit.model.GlowSettings
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.canSendTest
import app.rimlit.ui.ScreenGlow
import kotlinx.coroutines.delay

// The setup's last step, "Try it": how the glow can be shown with the grants given, and showing it.

/** How "Try it" shows the glow. */
internal enum class TestPlay {
    /** A real test notification, through the listener: the end-to-end glow. */
    NOTIFICATION,

    /** The default look previewed through the machine, on screen over other apps: no notification can glow. */
    ON_SCREEN,

    /** The default look played in Rimlit's own window: nothing can draw over other apps. */
    IN_WINDOW,
}

/**
 * How "Try it" shows the glow, given the grants in [statuses], whether the test notification can be posted
 * ([testPostable]) and why the engine's on-screen overlay isn't the accessibility one ([overlayFallback], `null` while
 * it is, or before the engine has started): in Rimlit's window while nothing can draw on screen, since neither a test
 * nor a preview would show, else a real test notification when it can glow, else a preview on screen. Nothing can
 * draw when the engine has no overlay to draw in, such as an accessibility service on but not running without Display
 * over other apps, or, before the engine has started, when neither is granted.
 */
internal fun testPlay(
    statuses: List<PermissionStatus>,
    testPostable: Boolean,
    overlayFallback: OverlayFallback?,
): TestPlay = when {
    overlayFallback == OverlayFallback.NO_OVERLAY || !SetupStep.ON_SCREEN.granted(statuses) -> TestPlay.IN_WINDOW
    statuses.canSendTest(testPostable) -> TestPlay.NOTIFICATION
    else -> TestPlay.ON_SCREEN
}

/** Shows the default look's glow as [play] says: [playInWindow] plays it in Rimlit's own window. */
internal fun MainViewModel.tryIt(play: TestPlay, settings: GlowSettings, playInWindow: () -> Unit) {
    when (play) {
        TestPlay.NOTIFICATION -> sendTestNotification()
        TestPlay.ON_SCREEN -> preview(PreviewTarget.ON_SCREEN, settings.previewSpec())
        TestPlay.IN_WINDOW -> playInWindow()
    }
}

/** "Try it" as [play] shows the glow ([onTest]), with a note on why it isn't a real test notification. */
internal fun LazyListScope.testItems(play: TestPlay, onTest: () -> Unit) {
    item(key = "test") {
        Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
            Text(play.label())
        }
    }
    val note = when (play) {
        TestPlay.NOTIFICATION -> null
        TestPlay.ON_SCREEN -> R.string.setup_test_preview_note
        TestPlay.IN_WINDOW -> R.string.setup_test_window_note
    }
    if (note != null) {
        item(key = "test_note") {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The default look played over the page for one glow's duration after each "Try it" in Rimlit's window ([plays], the
 * taps counted), fading in and out as a real one would; a tap while it plays starts it again. Only taps after it is
 * first shown play, so coming back to the step doesn't replay the last.
 */
@Composable
internal fun WindowTestGlow(settings: GlowSettings, plays: Int, modifier: Modifier = Modifier) {
    var glowing by remember { mutableStateOf(false) }
    val shownAt = remember { plays }
    LaunchedEffect(plays) {
        if (plays != shownAt) {
            glowing = true
            delay(settings.durationMs)
            glowing = false
        }
    }
    AnimatedVisibility(
        visible = glowing,
        modifier = modifier,
        enter = fadeIn(tween(FADE_IN_MS.toInt())),
        exit = fadeOut(tween(FADE_OUT_MS.toInt())),
    ) {
        val spec = remember(settings) { settings.previewSpec() }
        key(plays) { ScreenGlow(spec, lockScreen = false) }
    }
}

@Composable
@ReadOnlyComposable
private fun TestPlay.label(): String = stringResource(
    when (this) {
        TestPlay.NOTIFICATION -> R.string.test_notification_button
        TestPlay.ON_SCREEN, TestPlay.IN_WINDOW -> R.string.setup_test_preview
    },
)
