package app.rimlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.permissions.Need
import app.rimlit.permissions.PermissionKind
import app.rimlit.ui.components.NavCard
import app.rimlit.ui.components.SectionTitle

/** [attentions] under [title] as compact rows, each opening its sheet ([onOpen]); nothing at all for none. */
internal fun LazyListScope.attentionItems(title: Int, attentions: List<Attention>, onOpen: (Attention) -> Unit) {
    if (attentions.isEmpty()) return
    item(key = "attention_title") { SectionTitle(stringResource(title)) }
    items(attentions, key = { "attention_${it.kind.name}_${it is Attention.ServiceNotRunning}" }) { attention ->
        NavCard(
            icon = attention.icon,
            title = attention.title(),
            subtitle = attention.reason(),
            onClick = { onOpen(attention) },
            badge = { NeedLabel(attention.need) },
        )
    }
}

/**
 * The whole of [attention], while one is open: its explanation, "Open settings" ([onOpenSettings], its permission's
 * page), and, where things may be put off ([onNotNow] set) and this one may, "Not now". Either closes it ([onClose]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttentionSheet(
    attention: Attention?,
    onOpenSettings: (PermissionKind) -> Unit,
    onNotNow: ((Attention) -> Unit)?,
    onClose: () -> Unit,
) {
    attention ?: return
    // Short enough to show whole: half open, its buttons would be out of sight.
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(attention.title(), style = MaterialTheme.typography.headlineSmall)
            NeedLabel(attention.need)
            Text(
                attention.explanation(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onNotNow != null && attention.dismissible) {
                    TextButton(
                        onClick = {
                            onNotNow(attention)
                            onClose()
                        },
                    ) { Text(stringResource(R.string.attention_not_now)) }
                }
                Button(
                    onClick = {
                        onOpenSettings(attention.kind)
                        onClose()
                    },
                ) { Text(stringResource(R.string.attention_open_settings)) }
            }
        }
    }
}

/** How much glows depend on it: "Required" or "Recommended", in its colour. */
@Composable
private fun NeedLabel(need: Need) {
    Text(
        text = stringResource(
            when (need) {
                Need.REQUIRED -> R.string.status_needed
                Need.RECOMMENDED -> R.string.status_recommended
            },
        ),
        style = MaterialTheme.typography.labelLarge,
        color = need.color(),
    )
}

@Composable
@ReadOnlyComposable
private fun Need.color(): Color = when (this) {
    Need.REQUIRED -> MaterialTheme.colorScheme.error
    Need.RECOMMENDED -> MaterialTheme.colorScheme.tertiary
}
