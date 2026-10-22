package app.rimlit

import android.app.Application
import android.content.Context
import app.rimlit.data.SettingsRepository
import app.rimlit.engine.GlowCoordinator
import app.rimlit.runtime.AndroidGlowDriver
import app.rimlit.runtime.ScreenStateReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Owns the glow machine for the life of the process. `onCreate` runs before any component, so the coordinator and
 * the screen receiver exist before the first notification, preview or surface report.
 */
class RimlitApp : Application() {

    lateinit var coordinator: GlowCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        coordinator = GlowCoordinator { AndroidGlowDriver(this, it) }
        ScreenStateReceiver(coordinator).register(this)
        SettingsRepository(this).settings.onEach(coordinator::settingsChanged).launchIn(MainScope())
    }
}

/** The process's glow coordinator. Main thread only. */
val Context.glowCoordinator: GlowCoordinator
    get() = (applicationContext as RimlitApp).coordinator
