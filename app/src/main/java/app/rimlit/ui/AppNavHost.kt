package app.rimlit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.rimlit.LookEditorActivity
import app.rimlit.MainViewModel
import app.rimlit.ui.editor.EditorTarget

private object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val BEHAVIOR = "behavior"
    const val DEVICE = "device"
}

/** The app's screens; the look editor is its own activity ([LookEditorActivity]), opened from Home and Apps. */
@Composable
fun AppNavHost(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val openEditor = { target: EditorTarget -> LookEditorActivity.start(context, target) }
    // Screen transitions cross-fade; this keeps the theme background behind them, not the window's.
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                MainScreen(
                    viewModel = viewModel,
                    onOpenApps = { navController.navigate(Routes.APPS) },
                    onOpenBehavior = { navController.navigate(Routes.BEHAVIOR) },
                    onOpenEditor = openEditor,
                )
            }
            composable(Routes.APPS) {
                AppsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = openEditor,
                )
            }
            composable(Routes.BEHAVIOR) {
                BehaviorScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDevice = { navController.navigate(Routes.DEVICE) },
                )
            }
            composable(Routes.DEVICE) {
                DeviceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
