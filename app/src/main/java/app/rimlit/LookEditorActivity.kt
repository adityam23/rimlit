package app.rimlit

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.rimlit.ui.editor.EditorTarget
import app.rimlit.ui.editor.LookEditorScreen
import app.rimlit.ui.glow.ProvideDeviceConditions
import app.rimlit.ui.theme.AppTheme

/**
 * The look editor, in a window of its own: its theme shows the wallpaper behind it (with no permission; since Android
 * 13 no app may read the wallpaper), which only a translucent window can, so every other screen keeps an opaque one.
 * It handles configuration changes in place (see the manifest), so an import or save in flight always finds the editor
 * it was started from.
 */
class LookEditorActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light bars over the editor's dark screen, whichever theme the app is in.
        val dark = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        val target = EditorTarget.from(intent) ?: return finish()
        setContent {
            AppTheme {
                ProvideDeviceConditions {
                    LookEditorScreen(viewModel = viewModel, target = target, onClose = ::finish)
                }
            }
        }
    }

    companion object {
        /** Opens the editor on [target]. */
        fun start(context: Context, target: EditorTarget) {
            context.startActivity(target.putIn(Intent(context, LookEditorActivity::class.java)))
        }
    }
}
