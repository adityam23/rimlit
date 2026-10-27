package app.rimlit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.rimlit.MainViewModel

private object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val STYLE = "style"
    const val BEHAVIOR = "behavior"
}

@Composable
fun AppNavHost(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            MainScreen(
                viewModel = viewModel,
                onOpenApps = { navController.navigate(Routes.APPS) },
                onOpenStyle = { navController.navigate(Routes.STYLE) },
                onOpenBehavior = { navController.navigate(Routes.BEHAVIOR) },
            )
        }
        composable(Routes.APPS) {
            AppsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.STYLE) {
            StyleScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.BEHAVIOR) {
            BehaviorScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
