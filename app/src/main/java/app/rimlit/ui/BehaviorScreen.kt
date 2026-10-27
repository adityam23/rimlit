package app.rimlit.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.engine.PreviewTarget
import app.rimlit.ui.components.PreviewButtons
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard

@Composable
fun BehaviorScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fallback by viewModel.overlayFallback.collectAsStateWithLifecycle()

    SubScreenScaffold(
        title = stringResource(R.string.nav_behavior_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        SettingsList(innerPadding) {
            item { SectionTitle(stringResource(R.string.behavior_section)) }
            item {
                SwitchCard(
                    title = stringResource(R.string.show_locked_title),
                    checked = settings.showWhenLocked,
                    onCheckedChange = { show -> viewModel.update { it.copy(showWhenLocked = show) } },
                    subtitle = stringResource(R.string.show_locked_desc),
                    leading = { SettingIcon(Icons.Filled.Lock) },
                )
            }
            item {
                SwitchCard(
                    title = stringResource(R.string.show_unlocked_title),
                    checked = settings.showWhenUnlocked,
                    onCheckedChange = { show -> viewModel.update { it.copy(showWhenUnlocked = show) } },
                    subtitle = stringResource(R.string.show_unlocked_desc),
                    leading = { SettingIcon(Icons.Filled.PhoneAndroid) },
                )
            }
            item { SectionTitle(stringResource(R.string.preview_section)) }
            item {
                PreviewButtons(
                    onPreviewOnScreen = { viewModel.preview(PreviewTarget.ON_SCREEN, settings.previewSpec()) },
                    onPreviewLockScreen = { viewModel.preview(PreviewTarget.LOCK_SCREEN, settings.previewSpec()) },
                    onScreenFallsBack = fallback != null,
                )
            }
        }
    }
}
