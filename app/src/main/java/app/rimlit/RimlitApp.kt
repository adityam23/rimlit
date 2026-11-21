package app.rimlit

import android.app.Application
import android.content.Context
import app.rimlit.data.SettingsRepository
import app.rimlit.data.clearSharedLookFiles
import app.rimlit.engine.GlowCoordinator
import app.rimlit.runtime.AndroidGlowDriver
import app.rimlit.runtime.ScreenStateReceiver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the glow machine for the life of the process. `onCreate` runs before any component, so the coordinator and
 * the screen receiver exist before the first notification, preview or surface report.
 */
class RimlitApp(private val io: CoroutineDispatcher = Dispatchers.IO) : Application() {

    lateinit var coordinator: GlowCoordinator
        private set

    /** Lives as long as the process: settings collection, glow timers and extraction deliveries. */
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        coordinator = GlowCoordinator { AndroidGlowDriver(this, it, scope) }
        ScreenStateReceiver(coordinator).register(this)
        val repository = SettingsRepository(this)
        repository.settings.onEach(coordinator::settingsChanged).launchIn(scope)
        // Settings from an older version are written in today's form once, so their migration isn't redone per read.
        scope.launch(io) { repository.migrate() }
        // Stale shares from a run that was killed mid-share, or before this fix: nothing reads them back.
        scope.launch(io) { clearSharedLookFiles(this@RimlitApp) }
    }
}

/** The process's glow coordinator. Main thread only. */
val Context.glowCoordinator: GlowCoordinator
    get() = (applicationContext as RimlitApp).coordinator
