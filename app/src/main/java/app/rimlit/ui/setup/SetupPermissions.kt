package app.rimlit.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import app.rimlit.R
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.Permissions
import app.rimlit.permissions.isGranted
import app.rimlit.permissions.needOf
import app.rimlit.ui.NeedLabel
import app.rimlit.ui.components.NavCard
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.icon
import app.rimlit.ui.reason
import app.rimlit.ui.title

/**
 * Whether Android refused to post notifications without showing its dialog, since the user refused it for good before:
 * not [granted], and it would show no rationale either before the request ([rationaleBefore]) or after
 * ([rationaleAfter]). A dialog the user answered "Don't allow" leaves a rationale on one side or the other: after a
 * first refusal, before a second. A first dialog dismissed without an answer reads as refused for good too, which only
 * opens the settings page, where the grant is given all the same.
 */
internal fun refusedForGood(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
    !granted && !rationaleBefore && !rationaleAfter

/**
 * Opens where a grant is given: for posting notifications, Android's own dialog while it can still be asked (then
 * [onAnswer]), and otherwise the grant's settings page ([onOpenSettings]). When Android refuses at once without
 * showing its dialog ([refusedForGood]), the settings page opens straight away, so the tap is never lost.
 */
@Composable
internal fun rememberPermissionOpener(
    onOpenSettings: (PermissionKind) -> Unit,
    onAnswer: () -> Unit,
): (PermissionKind) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Only asked on Android 13 and newer (Permissions.canAskToPost), where POST_NOTIFICATIONS is a runtime permission.
    val rationale = {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    }
    // Read as each request is launched: saved, since the activity may be recreated while Android's dialog is up.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (refusedForGood(granted, rationaleBefore, rationale())) onOpenSettings(PermissionKind.NOTIFICATIONS)
        onAnswer()
    }
    return { kind ->
        if (kind == PermissionKind.NOTIFICATIONS && Permissions.canAskToPost(context)) {
            rationaleBefore = rationale()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onOpenSettings(kind)
        }
    }
}

/**
 * The grants [step] asks for, as Home's rows: each labelled granted, or how much glows need it in [statuses]
 * (Display over other apps as the fallback in the on-screen step), opening where it is granted ([onOpen]). Notification
 * access or accessibility, while missing on Android 13 and newer, adds how to allow restricted settings
 * ([onOpenAppInfo]).
 */
internal fun LazyListScope.permissionItems(
    step: SetupStep,
    statuses: List<PermissionStatus>,
    onOpen: (PermissionKind) -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    items(step.kinds, key = { it.name }) { kind ->
        // Home's wording for the grant, granted or not.
        val granted = statuses.isGranted(kind)
        NavCard(
            icon = kind.icon,
            title = kind.title(),
            subtitle = kind.reason(),
            onClick = { onOpen(kind) },
            badge = {
                when {
                    granted -> GrantLabel(stringResource(R.string.setup_granted))

                    kind == PermissionKind.OVERLAY && step == SetupStep.ON_SCREEN ->
                        GrantLabel(stringResource(R.string.setup_fallback))

                    else -> NeedLabel(statuses.needOf(kind))
                }
            },
        )
    }
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        step.kinds.any { it in RESTRICTED && !statuses.isGranted(it) }
    if (restricted) {
        item(key = "restricted") {
            SettingCard(
                title = stringResource(R.string.setup_restricted_title),
                subtitle = stringResource(R.string.setup_restricted_desc),
                onClick = onOpenAppInfo,
                leading = { SettingIcon(Icons.AutoMirrored.Filled.HelpOutline) },
            )
        }
    }
}

/** The grants Android 13 and newer block for sideloaded apps until "Allow restricted settings" is used. */
private val RESTRICTED = setOf(PermissionKind.NOTIFICATION_ACCESS, PermissionKind.ACCESSIBILITY)

/** A grant's state where Home shows how much it is needed: granted, or the on-screen fallback. */
@Composable
private fun GrantLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
