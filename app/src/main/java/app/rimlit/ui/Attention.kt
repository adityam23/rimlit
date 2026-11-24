package app.rimlit.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.permissions.Need
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.needOf

/**
 * Something that needs the user: a permission still missing, or the accessibility glow turned on but not running.
 * Android's overlay notice, shown while the accessibility glow is off, is the accessibility permission's own reason.
 */
internal sealed interface Attention {
    /** How much glows depend on it. */
    val need: Need

    /** The permission whose settings page sorts it out. */
    val kind: PermissionKind

    /** A recommended permission may be put off ("Not now"): it then shows in Behavior instead of on Home. */
    val dismissible: Boolean get() = need == Need.RECOMMENDED && this is Missing

    /** [kind] isn't granted; [need] as the other grants make it (see [needOf]). */
    data class Missing(override val kind: PermissionKind, override val need: Need = kind.need) : Attention

    /** The accessibility glow is on in settings, but the system hasn't started it: off and on again fixes it. */
    data object ServiceNotRunning : Attention {
        override val need = Need.RECOMMENDED
        override val kind = PermissionKind.ACCESSIBILITY
    }

    companion object {
        private const val NOT_RUNNING = "not_running"

        /**
         * Saves an attention as its permission's and need's names, "OVERLAY:RECOMMENDED", or [NOT_RUNNING]; `null`
         * (none) as an empty string.
         */
        val Saver: Saver<Attention?, String> = Saver(
            save = { attention ->
                when (attention) {
                    null -> ""
                    ServiceNotRunning -> NOT_RUNNING
                    is Missing -> "${attention.kind.name}:${attention.need.name}"
                }
            },
            restore = { saved ->
                if (saved == NOT_RUNNING) {
                    ServiceNotRunning
                } else {
                    val kind = PermissionKind.entries.firstOrNull { it.name == saved.substringBefore(':') }
                    val need = Need.entries.firstOrNull { it.name == saved.substringAfter(':', "") }
                    kind?.let { Missing(it, need ?: it.need) }
                }
            },
        )
    }
}

/**
 * What needs the user, given the grants in [statuses] and why on-screen glows fall back ([fallback]), required first.
 */
internal fun attentions(statuses: List<PermissionStatus>, fallback: OverlayFallback?): List<Attention> {
    val missing = statuses.filterNot { it.granted }.map { Attention.Missing(it.kind, statuses.needOf(it.kind)) }
    val notRunning = listOfNotNull(
        Attention.ServiceNotRunning.takeIf { fallback == OverlayFallback.SERVICE_NOT_RUNNING },
    )
    return (missing + notRunning).sortedBy { it.need }
}

// A missing permission is worded as the permission itself (PermissionWording.kt).

internal val Attention.icon: ImageVector
    get() = when (this) {
        Attention.ServiceNotRunning -> Icons.Filled.SyncProblem
        is Attention.Missing -> kind.icon
    }

@Composable
@ReadOnlyComposable
internal fun Attention.title(): String = when (this) {
    Attention.ServiceNotRunning -> stringResource(R.string.attention_not_running_title)
    is Attention.Missing -> kind.title()
}

/** Why it matters, in a line: the row on Home. */
@Composable
@ReadOnlyComposable
internal fun Attention.reason(): String = when (this) {
    Attention.ServiceNotRunning -> stringResource(R.string.attention_not_running_reason)
    is Attention.Missing -> kind.reason()
}

/** The whole explanation: the sheet a row opens. */
@Composable
@ReadOnlyComposable
internal fun Attention.explanation(): String = when (this) {
    Attention.ServiceNotRunning ->
        stringResource(R.string.fallback_service_not_running, stringResource(R.string.accessibility_service_label))

    is Attention.Missing -> kind.explanation()
}
