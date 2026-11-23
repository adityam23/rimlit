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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rimlit.data.AppIcons
import app.rimlit.data.AppInfo

/** The size an app's icon is drawn at, and loaded for ([AppIcons]). */
internal const val APP_ICON_DP = 40

/**
 * [app]'s own icon, loaded off the main thread by [icons]; nothing while it loads, and a letter in a colour circle
 * when it has none that loads.
 */
@Composable
internal fun AppIcon(icons: AppIcons, app: AppInfo, modifier: Modifier = Modifier) {
    val loaded by produceState(icons.cached(app.packageName), icons, app.packageName) {
        value = icons.load(app.packageName)
    }
    val icon = remember(loaded) { loaded?.icon?.asImageBitmap() }
    val sized = modifier.size(APP_ICON_DP.dp)
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
