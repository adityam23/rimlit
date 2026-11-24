package app.rimlit

import android.app.Application
import android.content.Context
import app.rimlit.data.LookFiles
import app.rimlit.data.LookLibrary
import app.rimlit.data.ParticleImageStore
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
 * Owns, for the life of the process, the glow machine and what every screen shares: the settings, the particle image
 * files, the look library (its lock and its clean-up) and the look editor's drafts. `onCreate` runs before any
 * component, so they exist before the first notification, preview, surface report or screen.
 */
class RimlitApp(private val io: CoroutineDispatcher = Dispatchers.IO) : Application() {

    lateinit var coordinator: GlowCoordinator
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var particleImages: ParticleImageStore
        private set
    lateinit var lookFiles: LookFiles
        private set
    lateinit var library: LookLibrary
        private set
    lateinit var drafts: LookDrafts
        private set

    /** Lives as long as the process: settings collection, glow timers, extraction deliveries and the look library. */
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        particleImages = ParticleImageStore(this, io)
        lookFiles = LookFiles(this, particleImages, io)
        library = LookLibrary(settings, particleImages)
        drafts = LookDrafts(library, scope)
        coordinator = GlowCoordinator { AndroidGlowDriver(this, it, scope) }
        ScreenStateReceiver(coordinator).register(this)
        settings.settings.onEach(coordinator::settingsChanged).launchIn(scope)
        scope.launch { library.deleteUnusedImagesOnChange() }
        // Stale shares from a run that was killed mid-share, or before this fix: nothing reads them back.
        scope.launch(io) { clearSharedLookFiles(this@RimlitApp) }
    }
}

private val Context.app get() = applicationContext as RimlitApp

/** The process's glow coordinator. Main thread only. */
val Context.glowCoordinator: GlowCoordinator get() = app.coordinator

/** The process's settings repository. */
val Context.settingsRepository: SettingsRepository get() = app.settings

/** The process's particle image files. */
val Context.particleImages: ParticleImageStore get() = app.particleImages

/** The process's look files. */
val Context.lookFiles: LookFiles get() = app.lookFiles

/** The process's look library. */
val Context.lookLibrary: LookLibrary get() = app.library

/** The process's look editor drafts. */
val Context.lookDrafts: LookDrafts get() = app.drafts
