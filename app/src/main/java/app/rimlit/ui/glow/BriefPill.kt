package app.rimlit.ui.glow

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.rimlit.GlowListenerService
import app.rimlit.R
import app.rimlit.engine.GlowState
import app.rimlit.engine.PillApp
import app.rimlit.engine.Surface
import app.rimlit.engine.opensOnRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** How far a swipe up or sideways must travel to dismiss the glow. */
private val SWIPE_DISMISS_DISTANCE = 24.dp

/**
 * The pill of what the machine shows on [surface], placed at [placement] (`null` until the window knows its cutout),
 * fading with the edge glow; nothing while there is no pill. A double tap calls [onOpen] while the machine would open
 * something; a swipe up or sideways calls [onDismiss]. A single tap does nothing: Samsung moved its pill to a double
 * tap because single taps opened apps by accident.
 */
@Composable
internal fun BriefPillHost(
    state: StateFlow<GlowState?>,
    surface: Surface,
    placement: PillPlacement?,
    metrics: PillMetrics,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the app's name and icon load: the package manager blocks. */
    loadDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val frame by rememberSurfaceState(state, surface, FRAME)
    val opens by rememberSurfaceState(state, surface, OPENS)
    val shown = frame ?: return
    val app = shown.pill ?: return
    placement ?: return
    // Nothing shows until the name is in: the pill would otherwise start out as a bare package name, or resize.
    // The loaded label stays with its app: after a re-target the last app's label never shows for the new one.
    val context = LocalContext.current
    val loaded by produceState<Pair<PillApp, AppLabel>?>(null, app) {
        // Read on the main thread, used off it, as the listener allows.
        val listener = GlowListenerService.connected
        value = app to withContext(loadDispatcher) {
            val user = (app as? PillApp.Notifying)?.let { listener?.posted(it.key)?.user }
            AppLabel.load(context, app, user, metrics.icon.toInt())
        }
    }
    val label = loaded?.takeIf { it.first == app }?.second ?: return
    val alpha by rememberFrameAlpha(shown)
    val open by rememberUpdatedState(onOpen)
    val dismiss by rememberUpdatedState(onDismiss)
    val openAction = stringResource(R.string.pill_action_open)
    val description = if (opens) stringResource(R.string.pill_open_app, label.name) else label.name
    val actions = listOfNotNull(
        CustomAccessibilityAction(openAction) { handled(open) }.takeIf { opens },
        CustomAccessibilityAction(stringResource(R.string.pill_action_dismiss)) { handled(dismiss) },
    )
    BriefPill(
        label = label,
        placement = placement,
        metrics = metrics,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clearAndSetSemantics {
                contentDescription = description
                customActions = actions
                if (opens) onClick(label = openAction) { handled(open) }
            }
            .pointerInput(opens) { if (opens) detectTapGestures(onDoubleTap = { open() }) }
            .pointerInput(Unit) {
                val distance = SWIPE_DISMISS_DISTANCE.toPx()
                var travel = Offset.Zero
                detectDragGestures(
                    onDragStart = { travel = Offset.Zero },
                    onDragEnd = { if (travel.y < -distance || abs(travel.x) > distance) dismiss() },
                ) { change, drag ->
                    change.consume()
                    travel += drag
                }
            },
    )
}

private val OPENS = { state: GlowState?, surface: Surface -> state?.opensOnRequest(surface) != null }

/**
 * The app's icon and name in a black pill, island style: [PillPlacement.gap] wide between them (over the camera, when
 * it wraps one). Only the app is named, never the notification's title or text.
 */
@Composable
private fun BriefPill(label: AppLabel, placement: PillPlacement, metrics: PillMetrics, modifier: Modifier = Modifier) {
    // Laid out left to right in every locale: the gap must stay over the camera, which is placed from the left.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .height(placement.height.px)
                .widthIn(max = placement.maxWidth.px)
                .background(Color.Black, CircleShape),
        ) {
            Spacer(Modifier.width(metrics.startPadding.px))
            label.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(metrics.icon.px)) }
                ?: Spacer(Modifier.size(metrics.icon.px))
            Spacer(Modifier.width(placement.gap.px))
            Text(
                text = label.name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(metrics.endPadding.px))
        }
    }
}

/** Runs [action] for an accessibility action, which then reports itself handled. */
private fun handled(action: () -> Unit): Boolean {
    action()
    return true
}

/** These window pixels in dp. */
private val Number.px: Dp
    @Composable @ReadOnlyComposable
    get() = with(LocalDensity.current) { toFloat().toDp() }

/** The package [this] names: Rimlit's own for a preview. */
private fun PillApp.packageName(context: Context): String = when (this) {
    is PillApp.Notifying -> packageName
    PillApp.Rimlit -> context.packageName
}

/** An app's name and icon as the pill shows them. */
private data class AppLabel(val name: String, val icon: ImageBitmap?) {
    companion object {
        private const val TAG = "BriefPill"

        /**
         * [app]'s label and icon as [user] has it (`null`: Rimlit's own user), the icon [iconPx] square and both
         * badged for a work profile; its package name and no icon if Rimlit can't see it (an app with no launcher
         * activity is outside its package visibility). Blocks on the package manager.
         */
        fun load(context: Context, app: PillApp, user: UserHandle?, iconPx: Int): AppLabel {
            val packageName = app.packageName(context)
            val pm = context.packageManager
            val owner = user ?: Process.myUserHandle()
            val info = applicationInfo(context, packageName, owner) ?: return AppLabel(packageName, icon = null)
            val name = pm.getUserBadgedLabel(pm.getApplicationLabel(info), owner).toString()
            return AppLabel(name, icon(pm, info, owner, iconPx))
        }

        /**
         * [packageName] as installed for [user]; `null` (logged) if Rimlit can't see it there. Another profile's apps
         * are only reachable through [LauncherApps]: the package manager answers for Rimlit's own user alone.
         */
        private fun applicationInfo(context: Context, packageName: String, user: UserHandle): ApplicationInfo? = try {
            if (user == Process.myUserHandle()) {
                context.packageManager.getApplicationInfo(packageName, 0)
            } else {
                context.getSystemService(LauncherApps::class.java)?.getApplicationInfo(packageName, 0, user)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.i(TAG, "Can't see $packageName in $user; the pill names its package", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to look up $packageName in $user; the pill names its package", e)
            null
        }

        /**
         * [info]'s icon with [user]'s badge, or `null` if it can't be drawn. Another app's icon is untrusted input,
         * as for palette extraction: whatever it throws, the pill only goes without it, rather than taking the
         * process down.
         */
        @Suppress("TooGenericExceptionCaught")
        private fun icon(pm: PackageManager, info: ApplicationInfo, user: UserHandle, px: Int): ImageBitmap? = try {
            pm.getUserBadgedIcon(pm.getApplicationIcon(info), user).toBitmap(px, px).asImageBitmap()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't draw ${info.packageName}'s icon", e)
            null
        }
    }
}
