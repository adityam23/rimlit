package app.rimlit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Cyan700,
    secondary = Magenta700,
    tertiary = Cyan500,
    background = LightSurface,
    surface = LightSurface,
)

private val DarkColors = darkColorScheme(
    primary = Cyan300,
    secondary = Magenta300,
    tertiary = Cyan500,
    background = DarkSurface,
    surface = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
)

/** Material 3 following the system's dark mode, with dynamic colour on Android 12+. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors

        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
