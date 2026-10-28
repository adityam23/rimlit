package app.rimlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.AppInfo
import app.rimlit.model.GlowSettings
import app.rimlit.model.Rule
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.paletteBrush

@Composable
fun AppsScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppInfo?>(null) }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    SubScreenScaffold(
        title = stringResource(R.string.nav_apps_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.apps_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            AppList(
                apps = filtered,
                settings = settings,
                onEditLook = { editing = it },
                onToggle = { app, blocked -> viewModel.update { it.withBlocked(app.packageName, blocked) } },
            )
        }
    }

    editing?.let { app ->
        AppLookDialog(
            app = app,
            settings = settings,
            onSave = { rule ->
                viewModel.update { it.withAppRule(app.packageName, rule) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AppList(
    apps: List<AppInfo>,
    settings: GlowSettings,
    onEditLook: (AppInfo) -> Unit,
    onToggle: (AppInfo, Boolean) -> Unit,
) {
    if (apps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.apps_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            val rule = settings.appRules[app.packageName]
            AppRow(
                app = app,
                blocked = app.packageName in settings.blockedPackages,
                palette = (rule as? Rule.UsePalette)?.palette?.argb,
                ruleLabel = settings.ruleLabel(rule),
                onEditLook = { onEditLook(app) },
                onToggle = { blocked -> onToggle(app, blocked) },
            )
        }
    }
}

/** What an app's [rule] reads as under its name; `null` when it glows with the default look. */
@Composable
@ReadOnlyComposable
private fun GlowSettings.ruleLabel(rule: Rule?): String? = when (rule) {
    is Rule.UseLook -> look(rule.lookId)?.let { stringResource(R.string.app_rule_look, it.name) }
    is Rule.UsePalette -> stringResource(R.string.app_rule_palette)
    null -> null
}

private fun GlowSettings.withBlocked(packageName: String, blocked: Boolean) =
    copy(blockedPackages = if (blocked) blockedPackages + packageName else blockedPackages - packageName)

@Composable
private fun AppRow(
    app: AppInfo,
    blocked: Boolean,
    palette: List<Int>?,
    ruleLabel: String?,
    onEditLook: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(label = app.label, seed = app.packageName)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ruleLabel?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        PaletteButton(palette = palette, onClick = onEditLook)
        Spacer(modifier = Modifier.width(12.dp))
        // Switch shows "allowed"; checked means NOT blocked.
        Switch(checked = !blocked, onCheckedChange = { allowed -> onToggle(!allowed) })
    }
}

/** Opens the app's look: shows the app's own palette, or a palette icon when it glows with a look's colors. */
@Composable
private fun PaletteButton(palette: List<Int>?, onClick: () -> Unit) {
    val description = stringResource(R.string.app_look_edit)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (palette != null) Modifier.background(paletteBrush(palette, sweep = true)) else Modifier)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (palette == null) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LetterAvatar(label: String, seed: String) {
    val initial = label.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(avatarColor(seed)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private val AvatarPalette = listOf(
    Color(0xFF00BCD4),
    Color(0xFFE040FB),
    Color(0xFF536DFE),
    Color(0xFF00C853),
    Color(0xFFFF6D00),
    Color(0xFFD50000),
    Color(0xFF8D6E63),
    Color(0xFF00897B),
)

private fun avatarColor(seed: String): Color = AvatarPalette[(seed.hashCode() and Int.MAX_VALUE) % AvatarPalette.size]
