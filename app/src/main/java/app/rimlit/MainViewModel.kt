package app.rimlit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.rimlit.data.AppInfo
import app.rimlit.data.AppListRepository
import app.rimlit.data.ParticleImageStore
import app.rimlit.data.SettingsRepository
import app.rimlit.engine.GlowEvent
import app.rimlit.engine.PreviewTarget
import app.rimlit.engine.onScreenOverlay
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.particleImageIds
import app.rimlit.model.withImages
import app.rimlit.notify.GlowNotifications
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.Permissions
import app.rimlit.permissions.overlayFallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val appListRepository = AppListRepository(application)
    private val particleImages = ParticleImageStore(application, ioDispatcher)

    /**
     * Held while particle image files change: an import holds it until its look saves the new ids, so a clean-up
     * never deletes an image its look is about to reference.
     */
    private val particleImageFiles = Mutex()

    init {
        // Whenever a look stops drawing an image (it is removed, its look edited or deleted), its file goes.
        viewModelScope.launch {
            repository.settings.map { it.particleImageIds }.distinctUntilChanged().collect {
                particleImageFiles.withLock { deleteUnusedParticleImages() }
            }
        }
    }

    val settings: StateFlow<GlowSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlowSettings(),
    )

    private val _permissions = MutableStateFlow(Permissions.statuses(application))
    val permissions: StateFlow<List<PermissionStatus>> = _permissions.asStateFlow()

    /** Why on-screen previews would use the app overlay now, so Android shows its notice; `null` if not. */
    val overlayFallback: StateFlow<OverlayFallback?> = combine(
        _permissions,
        application.glowCoordinator.state.map { it?.onScreenOverlay }.distinctUntilChanged(),
        ::overlayFallback,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /**
     * [overlayFallback] where real glows meet it: they are on and show while unlocked, the only place the app overlay
     * stands in (above the lock screen, only the accessibility overlay draws).
     */
    val glowFallback: StateFlow<OverlayFallback?> = combine(overlayFallback, settings) { fallback, settings ->
        fallback.takeIf { settings.enabled && settings.showWhenUnlocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val apps: StateFlow<List<AppInfo>> = flow { emit(appListRepository.launchableApps()) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Re-evaluate permission grants; called from [MainActivity.onResume]. */
    fun refreshPermissions() {
        _permissions.value = Permissions.statuses(getApplication())
    }

    /** Persists a settings change (atomic read-modify-write); the job completes once it is written. */
    fun update(transform: (GlowSettings) -> GlowSettings): Job = viewModelScope.launch { repository.update(transform) }

    /**
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]) as particles of the look [lookId],
     * after its own up to [MAX_PARTICLE_IMAGES]; [onDone] gets how many couldn't be added.
     */
    fun addParticleImages(lookId: String, uris: List<Uri>, onDone: (rejected: Int) -> Unit) {
        viewModelScope.launch {
            val added = particleImageFiles.withLock {
                val ids = uris.take(MAX_PARTICLE_IMAGES).mapNotNull { particleImages.import(it) }
                repository.update { settings ->
                    settings.updateLook(lookId) { it.copy(particles = it.particles.withImages(ids)) }
                }
                // Any past the limit, or for a look deleted meanwhile, are no look's.
                val kept = deleteUnusedParticleImages()
                ids.count { it in kept }
            }
            onDone(uris.size - added)
        }
    }

    /** Deletes the image files no look draws; the ids of those kept. Only while holding [particleImageFiles]. */
    private suspend fun deleteUnusedParticleImages(): Set<String> =
        repository.settings.first().particleImageIds.also { particleImages.retainOnly(it) }

    fun openPermission(kind: PermissionKind) {
        Permissions.open(getApplication(), kind)
    }

    /** Shows [spec] on [target] at once, through the machine: latest wins, like a notification. */
    fun preview(target: PreviewTarget, spec: GlowSpec) {
        getApplication<Application>().glowCoordinator.send(GlowEvent.Preview(target, spec))
    }

    /**
     * Posts a test notification through the real listener pipeline so the user can
     * check the exact end-to-end effect (screen on → overlay; screen off → lock screen).
     */
    fun sendTestNotification() {
        GlowNotifications.postTest(getApplication(), settings.value.durationMs)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] ?: error("Application not available")
                MainViewModel(app)
            }
        }
    }
}
