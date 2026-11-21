package app.rimlit.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.rimlit.MainViewModel
import app.rimlit.model.Premade
import app.rimlit.ui.editor.LookEditorScreen

private object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val BEHAVIOR = "behavior"
    const val DEVICE = "device"

    /** The look editor, on the look [LOOK] or a new one from the premade [PREMADE]. */
    const val EDITOR = "editor?look={look}&premade={premade}"
    const val LOOK = "look"
    const val PREMADE = "premade"

    fun editLook(id: String) = "editor?look=${Uri.encode(id)}"

    fun newLook(premade: Premade) = "editor?premade=${premade.name}"
}

@Composable
fun AppNavHost(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    // Screen transitions cross-fade over this, not the window; the editor shows the wallpaper behind itself instead.
    val editing = entry?.destination?.route == Routes.EDITOR
    Surface(color = if (editing) Color.Transparent else MaterialTheme.colorScheme.background, modifier = modifier) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                MainScreen(
                    viewModel = viewModel,
                    onOpenApps = { navController.navigate(Routes.APPS) },
                    onOpenBehavior = { navController.navigate(Routes.BEHAVIOR) },
                    onOpenLook = { id -> navController.navigate(Routes.editLook(id)) },
                    onNewLook = { premade -> navController.navigate(Routes.newLook(premade)) },
                )
            }
            composable(Routes.APPS) {
                AppsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument(Routes.LOOK) {
                        type = NavType.StringType
                        nullable = true
                    },
                    navArgument(Routes.PREMADE) {
                        type = NavType.StringType
                        nullable = true
                    },
                ),
            ) { editor ->
                val args = editor.arguments
                val premade = args?.getString(Routes.PREMADE)
                LookEditorScreen(
                    viewModel = viewModel,
                    lookId = args?.getString(Routes.LOOK),
                    premade = Premade.entries.firstOrNull { it.name == premade },
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
