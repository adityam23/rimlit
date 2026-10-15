package app.rimlit.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.previewSpec
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold

@Composable
fun BehaviorScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.nav_behavior_title), onBack = onBack) { innerPadding ->
        SettingsList(innerPadding) {
            item { SectionTitle(stringResource(R.string.behavior_section)) }
            item {
                SettingCard(
                    title = stringResource(R.string.show_locked_title),
                    subtitle = stringResource(R.string.show_locked_desc),
                    leading = { Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary) },
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
                    leading = { Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) },
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
                Button(
                    onClick = { viewModel.previewLockScreen(settings.previewSpec()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.preview_locked))
                }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.previewOverlay(settings.previewSpec()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.preview_unlocked))
                }
            }
        }
    }
}
