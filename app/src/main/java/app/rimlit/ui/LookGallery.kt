package app.rimlit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.canAddLook

private val TileWidth = 96.dp

/** What a gallery tile's long press offers for its look. */
internal enum class TileAction { DUPLICATE, RENAME, SHARE, DELETE }

/**
 * The user's looks as one row of tiles that scrolls sideways, each playing its edge while on screen, named and
 * badged, then "+ New" while another fits. A tap opens a look ([onOpen]), a long press offers [TileAction]s
 * ([onAction]); the default look can't be deleted.
 */
@Composable
internal fun LookGallery(
    settings: GlowSettings,
    onOpen: (Look) -> Unit,
    onAction: (Look, TileAction) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(settings.looks, key = { it.id }) { look ->
            LookTile(settings, look, onOpen = { onOpen(look) }, onAction = { onAction(look, it) })
        }
        if (settings.canAddLook) item(key = "new") { NewLookTile(onNew) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LookTile(settings: GlowSettings, look: Look, onOpen: () -> Unit, onAction: (TileAction) -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.look_actions)
    Box {
        Column(
            modifier = Modifier
                .width(TileWidth)
                .combinedClickable(onLongClickLabel = actionsLabel, onLongClick = { menu = true }, onClick = onOpen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhoneGlow(settings.previewSpec(look), width = TileWidth * 3 / 4)
            Text(
                text = look.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = lookKind(look),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = settings.usage(look),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val canDelete = look.id != settings.defaultLookId
            for (action in TileAction.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(action.labelRes)) },
                    onClick = {
                        menu = false
                        onAction(action)
                    },
                    enabled = when (action) {
                        TileAction.DUPLICATE -> settings.canAddLook
                        TileAction.DELETE -> canDelete
                        TileAction.RENAME, TileAction.SHARE -> true
                    },
                )
            }
        }
    }
}

@Composable
private fun NewLookTile(onNew: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TileWidth)
            .clickable(onClick = onNew),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = TileWidth * 3 / 4, height = TileWidth * 3 / 2)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(TileWidth * 3 / 32)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = stringResource(R.string.look_new),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val TileAction.labelRes
    get() = when (this) {
        TileAction.DUPLICATE -> R.string.look_duplicate
        TileAction.RENAME -> R.string.look_rename
        TileAction.SHARE -> R.string.looks_share
        TileAction.DELETE -> R.string.look_delete
    }
