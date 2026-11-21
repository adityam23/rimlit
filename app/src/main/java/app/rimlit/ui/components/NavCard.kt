package app.rimlit.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** A [SettingCard] that opens another screen or setting: [icon], [title] and [subtitle], and a chevron. */
@Composable
fun NavCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingCard(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        leading = { SettingIcon(icon) },
        trailing = { Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
