package app.rimlit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.rimlit.LookEditorActivity
import app.rimlit.MainViewModel
import app.rimlit.ui.editor.EditorTarget
import app.rimlit.ui.setup.SetupScreen

private object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val BEHAVIOR = "behavior"
    const val DEVICE = "device"
    const val SETUP = "setup"
}

/**
 * The app's screens; the look editor is its own activity ([LookEditorActivity]), opened from Home, Apps and the end
 * of setup. The app opens on the guided first launch until it is finished, and draws nothing until the stored settings
 * say which, so Home never flashes first.
 */
@Composable
fun AppNavHost(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val stored by viewModel.storedSettings.collectAsStateWithLifecycle()
    // Chosen once, when the settings are first read: finishing setup later doesn't move the start.
    var start by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(stored) {
        if (start == null) start = stored?.let { if (it.setupDone) Routes.HOME else Routes.SETUP }
    }
    val navController = rememberNavController()
    val context = LocalContext.current
    val openEditor = { target: EditorTarget -> LookEditorActivity.start(context, target) }
    // Screen transitions cross-fade; this keeps the theme background behind them, not the window's.
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        val startRoute = start ?: return@Surface
        NavHost(navController = navController, startDestination = startRoute) {
            composable(Routes.SETUP) {
                SetupScreen(
                    viewModel = viewModel,
                    onFinish = { customiseLookId ->
                        navController.navigate(Routes.HOME) { popUpTo(navController.graph.id) { inclusive = true } }
                        customiseLookId?.let { openEditor(EditorTarget.Edit(it)) }
                    },
                )
            }
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
                    onOpenSetup = { navController.navigate(Routes.SETUP) { launchSingleTop = true } },
                )
            }
            composable(Routes.DEVICE) {
                DeviceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
