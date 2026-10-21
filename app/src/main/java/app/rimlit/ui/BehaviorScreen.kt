package app.rimlit.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.previewSpec
import app.rimlit.ui.components.PreviewButtons
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold

@Composable
fun BehaviorScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SubScreenScaffold(
        title = stringResource(R.string.nav_behavior_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        SettingsList(innerPadding) {
            item { SectionTitle(stringResource(R.string.behavior_section)) }
            item {
                SettingCard(
                    title = stringResource(R.string.show_locked_title),
                    subtitle = stringResource(R.string.show_locked_desc),
                    leading = { SettingIcon(Icons.Filled.Lock) },
                    trailing = {
                        Switch(
                            checked = settings.showWhenLocked,
                            onCheckedChange = { show -> viewModel.update { it.copy(showWhenLocked = show) } },
                        )
                    },
                )
            }
            item {
                SettingCard(
                    title = stringResource(R.string.show_unlocked_title),
                    subtitle = stringResource(R.string.show_unlocked_desc),
                    leading = { SettingIcon(Icons.Filled.PhoneAndroid) },
                    trailing = {
                        Switch(
                            checked = settings.showWhenUnlocked,
                            onCheckedChange = { show -> viewModel.update { it.copy(showWhenUnlocked = show) } },
                        )
                    },
                )
            }
            item { SectionTitle(stringResource(R.string.preview_section)) }
            item {
                PreviewButtons(
                    onPreviewOnScreen = { viewModel.previewOverlay(settings.previewSpec()) },
                    onPreviewLockScreen = { viewModel.previewLockScreen(settings.previewSpec()) },
                )
            }
        }
    }
}
