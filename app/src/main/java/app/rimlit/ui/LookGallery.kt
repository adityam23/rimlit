package app.rimlit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.appsUsing
import app.rimlit.model.canAddLook
import app.rimlit.model.looksDefaultFirst

private val TileWidth = 96.dp

/** What a gallery tile's long press offers for its look. */
internal enum class TileAction { DUPLICATE, RENAME, SHARE, DELETE }

/**
 * The user's looks as one row of tiles that scrolls sideways, each playing its edge while on screen, named and
 * badged, the default look first and outlined, then "+ New" while another fits. A tap opens a look ([onOpen]), a long
 * press offers [TileAction]s ([onAction]); the default look can't be deleted. A look's app count asks to show the
 * apps using it ([onShowApps]).
 */
@Composable
internal fun LookGallery(
    settings: GlowSettings,
    onOpen: (Look) -> Unit,
    onAction: (Look, TileAction) -> Unit,
    onNew: () -> Unit,
    onShowApps: (Look) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(settings.looksDefaultFirst, key = { it.id }) { look ->
            LookTile(
                settings = settings,
                look = look,
                onOpen = { onOpen(look) },
                onAction = { onAction(look, it) },
                onShowApps = { onShowApps(look) },
            )
        }
        if (settings.canAddLook) item(key = "new") { NewLookTile(onNew) }
    }
}

/** Marks the default look: a star and "Default", wherever looks are listed. */
@Composable
internal fun DefaultBadge(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.look_default),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/**
 * A look as a tile: its edge playing in a small phone (outlined while [selected]), its name and what it is, then
 * [below]. The one face of a look wherever looks are listed: Home's gallery, an app's look sheet.
 */
@Composable
internal fun LookCard(
    settings: GlowSettings,
    look: Look,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    below: @Composable () -> Unit = {},
) {
    val phoneWidth = TileWidth * 3 / 4
    Column(modifier = modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        PhoneGlow(
            settings.previewSpec(look),
            width = phoneWidth,
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(phoneWidth / 8 + SelectionGap),
                )
                .padding(SelectionGap),
        )
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
            textAlign = TextAlign.Center,
        )
        below()
    }
}

/** Room round a tile's phone for its selection outline, kept when unselected so tiles line up. */
private val SelectionGap = 4.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LookTile(
    settings: GlowSettings,
    look: Look,
    onOpen: () -> Unit,
    onAction: (TileAction) -> Unit,
    onShowApps: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.look_actions)
    val isDefault = look.id == settings.defaultLookId
    Box {
        LookCard(
            settings = settings,
            look = look,
            modifier = Modifier.combinedClickable(
                onLongClickLabel = actionsLabel,
                onLongClick = { menu = true },
                onClick = onOpen,
            ),
            selected = isDefault,
        ) {
            if (isDefault) DefaultBadge() else LookUsage(settings.appsUsing(look.id), onShowApps)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val canDelete = !isDefault
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

/** Who has a look other than the default as their own: "Not used", or "3 apps", which opens them ([onShowApps]). */
@Composable
private fun LookUsage(apps: Int, onShowApps: () -> Unit) {
    val style = MaterialTheme.typography.labelSmall
    if (apps == 0) {
        Text(stringResource(R.string.look_unused), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        text = pluralStringResource(R.plurals.look_apps, apps, apps),
        style = style.copy(textDecoration = TextDecoration.Underline),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.look_apps_show),
            role = Role.Button,
            onClick = onShowApps,
        ),
    )
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
                .padding(SelectionGap)
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
