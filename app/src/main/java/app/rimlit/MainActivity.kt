package app.rimlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.rimlit.ui.AppNavHost
import app.rimlit.ui.glow.ProvideDeviceConditions
import app.rimlit.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    // Grants are asked for in the guided first launch (ui/setup), not on every launch.
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                ProvideDeviceConditions { AppNavHost(viewModel = viewModel) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}
