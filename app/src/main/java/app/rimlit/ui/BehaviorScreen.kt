package app.rimlit.ui

import android.text.format.DateFormat
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.engine.PreviewTarget
import app.rimlit.model.GlowSettings
import app.rimlit.model.QuietHours
import app.rimlit.model.QuietScope
import app.rimlit.ui.components.PreviewButtons
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard
import java.util.Calendar

private typealias Update = ((GlowSettings) -> GlowSettings) -> Unit

@Composable
fun BehaviorScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fallback by viewModel.overlayFallback.collectAsStateWithLifecycle()
    val update: Update = { transform -> viewModel.update(transform) }

    SubScreenScaffold(
        title = stringResource(R.string.nav_behavior_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        SettingsList(innerPadding) {
            whereSection(settings, update)
            quietHoursSection(settings.quietHours) { edit -> update { it.copy(quietHours = edit(it.quietHours)) } }
            item { SectionTitle(stringResource(R.string.preview_section)) }
            item {
                PreviewButtons(
                    onPreviewOnScreen = { viewModel.preview(PreviewTarget.ON_SCREEN, settings.previewSpec()) },
                    onPreviewLockScreen = { viewModel.preview(PreviewTarget.LOCK_SCREEN, settings.previewSpec()) },
                    fallback = fallback,
                )
            }
        }
    }
}

/** The three places a glow can show, each with its own toggle. */
private fun LazyListScope.whereSection(settings: GlowSettings, update: Update) {
    item { SectionTitle(stringResource(R.string.behavior_section)) }
    item {
        SwitchCard(
            title = stringResource(R.string.show_screen_off_title),
            checked = settings.showWhenScreenOff,
            onCheckedChange = { show -> update { it.copy(showWhenScreenOff = show) } },
            subtitle = stringResource(R.string.show_screen_off_desc),
            leading = { SettingIcon(Icons.Filled.MobileOff) },
        )
    }
    item {
        SwitchCard(
            title = stringResource(R.string.show_lock_screen_title),
            checked = settings.showOnLockScreen,
            onCheckedChange = { show -> update { it.copy(showOnLockScreen = show) } },
            subtitle = stringResource(R.string.show_lock_screen_desc),
            leading = { SettingIcon(Icons.Filled.Lock) },
        )
    }
    item {
        SwitchCard(
            title = stringResource(R.string.show_unlocked_title),
            checked = settings.showWhenUnlocked,
            onCheckedChange = { show -> update { it.copy(showWhenUnlocked = show) } },
            subtitle = stringResource(R.string.show_unlocked_desc),
            leading = { SettingIcon(Icons.Filled.PhoneAndroid) },
        )
    }
}

private fun LazyListScope.quietHoursSection(quiet: QuietHours, update: ((QuietHours) -> QuietHours) -> Unit) {
    item { SectionTitle(stringResource(R.string.quiet_section)) }
    item {
        SwitchCard(
            title = stringResource(R.string.quiet_title),
            checked = quiet.enabled,
            onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
            subtitle = stringResource(R.string.quiet_desc),
            leading = { SettingIcon(Icons.Filled.Bedtime) },
        )
    }
    if (!quiet.enabled) return
    item {
        TimeCard(stringResource(R.string.quiet_start), quiet.startMinute) { m -> update { it.copy(startMinute = m) } }
    }
    item {
        TimeCard(stringResource(R.string.quiet_end), quiet.endMinute) { m -> update { it.copy(endMinute = m) } }
    }
    items(QuietScope.entries, key = { it.name }) { scope ->
        val select = { update { it.copy(scope = scope) } }
        SettingCard(
            title = stringResource(scope.titleRes),
            subtitle = stringResource(scope.descRes),
            onClick = select,
            trailing = { RadioButton(selected = quiet.scope == scope, onClick = select) },
        )
    }
}

/** A time of day, as a card that opens a time picker. */
@Composable
private fun TimeCard(title: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    SettingCard(
        title = title,
        subtitle = timeText(minuteOfDay),
        onClick = { picking = true },
        leading = { SettingIcon(Icons.Filled.Schedule) },
    )
    if (picking) {
        TimePickerDialog(
            minuteOfDay = minuteOfDay,
            onConfirm = { minute ->
                picking = false
                onChange(minute)
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(minuteOfDay: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/** [minuteOfDay] in the user's time format (12- or 24-hour, as the system is set). */
@Composable
@ReadOnlyComposable
private fun timeText(minuteOfDay: Int): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }.time
    return DateFormat.getTimeFormat(LocalContext.current).format(time)
}

private val QuietScope.titleRes
    get() = when (this) {
        QuietScope.ALL -> R.string.quiet_scope_all_title
        QuietScope.SCREEN_OFF -> R.string.quiet_scope_screen_off_title
    }

private val QuietScope.descRes
    get() = when (this) {
        QuietScope.ALL -> R.string.quiet_scope_all_desc
        QuietScope.SCREEN_OFF -> R.string.quiet_scope_screen_off_desc
    }
