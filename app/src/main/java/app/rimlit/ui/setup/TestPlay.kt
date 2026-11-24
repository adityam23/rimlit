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
import androidx.compose.runtime.Stable
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
import app.rimlit.model.GlowSpec
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.canSendTest
import app.rimlit.ui.ScreenGlow
import kotlinx.coroutines.delay

// How setup shows a glow round the real screen with the grants given ("Try it", and a first look as it is picked),
// and showing it.

/** How setup shows a glow. */
internal enum class TestPlay {
    /** A real test notification, through the listener: the end-to-end glow. */
    NOTIFICATION,

    /** The look previewed through the machine, on screen over other apps. */
    ON_SCREEN,

    /** The look played in Rimlit's own window: nothing can draw over other apps. */
    IN_WINDOW,
}

/**
 * How a look's preview shows, given the grants in [statuses] and why the engine's on-screen overlay isn't the
 * accessibility one ([overlayFallback], `null` while it is, or before the engine has started): on screen, else in
 * Rimlit's window while nothing can draw on screen. Nothing can draw when the engine has no overlay to draw in, such as
 * an accessibility service on but not running without Display over other apps, or, before the engine has started,
 * when neither is granted.
 */
internal fun previewPlay(statuses: List<PermissionStatus>, overlayFallback: OverlayFallback?): TestPlay =
    if (overlayFallback == OverlayFallback.NO_OVERLAY || !SetupStep.ON_SCREEN.granted(statuses)) {
        TestPlay.IN_WINDOW
    } else {
        TestPlay.ON_SCREEN
    }

/**
 * How "Try it" shows the glow: as [previewPlay] has it, but a real test notification instead of a preview on screen
 * when one can be posted ([testPostable]) and glow; in the window, neither would show.
 */
internal fun testPlay(
    statuses: List<PermissionStatus>,
    testPostable: Boolean,
    overlayFallback: OverlayFallback?,
): TestPlay {
    val preview = previewPlay(statuses, overlayFallback)
    return if (preview == TestPlay.ON_SCREEN && statuses.canSendTest(testPostable)) TestPlay.NOTIFICATION else preview
}

/** One glow played in Rimlit's window: its [spec], and the [count] of window plays so far, so each is a new play. */
internal data class WindowPlay(val spec: GlowSpec, val count: Int)

/** Plays glows as a [TestPlay] says, keeping the latest one played in Rimlit's window for [WindowTestGlow]. */
@Stable
internal class GlowPlayer(private val viewModel: MainViewModel) {
    /** The latest glow played in the window; `null` before the first. */
    var inWindow: WindowPlay? by mutableStateOf(null)
        private set

    /** Shows [spec]'s glow as [play] says; a test notification glows as Rimlit's notifications do, without [spec]. */
    fun play(play: TestPlay, spec: GlowSpec) {
        when (play) {
            TestPlay.NOTIFICATION -> viewModel.sendTestNotification()
            TestPlay.ON_SCREEN -> viewModel.preview(PreviewTarget.ON_SCREEN, spec)
            TestPlay.IN_WINDOW -> inWindow = WindowPlay(spec, (inWindow?.count ?: 0) + 1)
        }
    }
}

/** A [GlowPlayer] for this composition: a configuration change forgets its window plays, so none replays. */
@Composable
internal fun rememberGlowPlayer(viewModel: MainViewModel): GlowPlayer = remember(viewModel) { GlowPlayer(viewModel) }

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
 * The glow [player] last played in Rimlit's window, over the page for one glow's duration ([durationMs]), fading in and
 * out as a real one would; a play while it shows starts it again.
 */
@Composable
internal fun WindowTestGlow(player: GlowPlayer, durationMs: Long, modifier: Modifier = Modifier) {
    val shown = player.inWindow
    var glowing by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown != null) {
            glowing = true
            delay(durationMs)
            glowing = false
        }
    }
    AnimatedVisibility(
        visible = glowing,
        modifier = modifier,
        enter = fadeIn(tween(FADE_IN_MS.toInt())),
        exit = fadeOut(tween(FADE_OUT_MS.toInt())),
    ) {
        if (shown != null) key(shown.count) { ScreenGlow(shown.spec, lockScreen = false) }
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
