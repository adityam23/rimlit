package app.rimlit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rimlit.appIcons
import app.rimlit.data.AppIcons
import app.rimlit.data.AppInfo

/** The size an app's icon is drawn at, and loaded for at the screen's density. */
private val IconSize = 40.dp

/**
 * [app]'s own icon, loaded off the main thread by the process's [AppIcons]; nothing while it loads, and a letter in a
 * colour circle when it has none that loads.
 */
@Composable
internal fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val icons = LocalContext.current.appIcons
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    val loaded by produceState(icons.cached(app.packageName, sizePx), icons, app.packageName, sizePx) {
        value = icons.load(app.packageName, sizePx)
    }
    val icon = remember(loaded) { loaded?.icon?.asImageBitmap() }
    val sized = modifier.size(IconSize)
    when {
        icon != null -> Image(icon, contentDescription = null, modifier = sized)
        loaded != null -> LetterAvatar(app, sized)
        else -> Box(sized)
    }
}

@Composable
private fun LetterAvatar(app: AppInfo, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(avatarColor(app.packageName)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = app.label.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
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
