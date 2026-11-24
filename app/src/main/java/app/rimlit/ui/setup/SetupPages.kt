package app.rimlit.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.Premade
import app.rimlit.ui.PhoneGlow
import app.rimlit.ui.components.CheckboxCard
import app.rimlit.ui.components.LookLimitNote
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.lookKind
import app.rimlit.ui.premadeItems

// The guided first launch's pages beyond its grants and its test: the welcome, each step's heading and the first look.

/** The welcome: the default look glowing, the name, what Rimlit does, and that nothing leaves the phone. */
internal fun LazyListScope.welcomeItems(settings: GlowSettings) {
    item(key = "welcome") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PhoneGlow(remember(settings) { settings.previewSpec() }, width = 112.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.setup_welcome_pitch),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.setup_welcome_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What [step] is for: its title and a paragraph. */
internal fun LazyListScope.stepHeading(step: SetupStep) {
    item(key = "heading_${step.name}") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(step.title(), style = MaterialTheme.typography.headlineSmall)
            Text(
                step.description(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The first look: keep the default look ([picked] `null`, as it starts) or pick a premade for it, each playing its
 * edge ([onPick]), or, when none [canPick], a note that no more looks fit; and whether to open it in the editor once
 * setup is done ([customise], [onCustomise]).
 */
internal fun LazyListScope.firstLookItems(
    settings: GlowSettings,
    canPick: Boolean,
    picked: Premade?,
    onPick: (Premade?) -> Unit,
    customise: Boolean,
    onCustomise: (Boolean) -> Unit,
) {
    item(key = "keep") {
        val look = settings.defaultLook
        RadioCard(
            title = stringResource(R.string.setup_look_keep, look.name),
            selected = picked == null,
            onSelect = { onPick(null) },
            subtitle = lookKind(look),
            leading = {
                PhoneGlow(remember(settings) { settings.previewSpec(look) }, width = 32.dp, moving = picked == null)
            },
        )
    }
    if (canPick) {
        // Only the chosen row's edge moves: a list of moving glows would be busy, and costly to draw.
        premadeItems(settings, selected = picked, onSelect = onPick, animatesSelected = true)
    } else {
        item(key = "look_limit") { LookLimitNote() }
    }
    item(key = "customise") {
        CheckboxCard(
            title = stringResource(R.string.setup_look_customise),
            checked = customise,
            onCheckedChange = onCustomise,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun SetupStep.title(): String = stringResource(
    when (this) {
        SetupStep.WELCOME -> R.string.app_name
        SetupStep.NOTIFICATION_ACCESS -> R.string.setup_access_title
        SetupStep.ON_SCREEN -> R.string.setup_on_screen_title
        SetupStep.SCREEN_OFF -> R.string.setup_screen_off_title
        SetupStep.BATTERY -> R.string.setup_battery_title
        SetupStep.DEVICE_FIT -> R.string.setup_fit_title
        SetupStep.FIRST_LOOK -> R.string.setup_look_title
        SetupStep.TEST -> R.string.setup_test_title
    },
)

@Composable
@ReadOnlyComposable
private fun SetupStep.description(): String = stringResource(
    when (this) {
        SetupStep.WELCOME -> R.string.setup_welcome_pitch
        SetupStep.NOTIFICATION_ACCESS -> R.string.setup_access_desc
        SetupStep.ON_SCREEN -> R.string.setup_on_screen_desc
        SetupStep.SCREEN_OFF -> R.string.setup_screen_off_desc
        SetupStep.BATTERY -> R.string.setup_battery_desc
        SetupStep.DEVICE_FIT -> R.string.setup_fit_desc
        SetupStep.FIRST_LOOK -> R.string.setup_look_desc
        SetupStep.TEST -> R.string.setup_test_desc
    },
)
