package app.rimlit

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.rimlit.data.AppInfo
import app.rimlit.data.AppListRepository
import app.rimlit.data.LookArchive
import app.rimlit.data.LookArchiveException
import app.rimlit.data.LookFileResult
import app.rimlit.data.LookFileResult.Exported
import app.rimlit.data.LookFileResult.Failed
import app.rimlit.data.LookFileResult.Imported
import app.rimlit.data.LookFileResult.NoRoom
import app.rimlit.data.LookFileResult.Shareable
import app.rimlit.data.LookFiles
import app.rimlit.data.LookLibrary
import app.rimlit.data.ParticleImageStore
import app.rimlit.data.SettingsRepository
import app.rimlit.engine.GlowEvent
import app.rimlit.engine.PreviewTarget
import app.rimlit.engine.onScreenOverlay
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.model.MAX_PARTICLE_IMAGES
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "MainViewModel"

/** [block]'s result, or why it failed reading or writing a look file. */
private suspend fun lookFileResult(block: suspend () -> LookFileResult?): LookFileResult? = try {
    block()
} catch (e: IOException) {
    Log.w(TAG, "A look file failed", e)
    Failed((e as? LookArchiveException)?.problem)
} catch (e: SecurityException) {
    Log.w(TAG, "A look file Rimlit may no longer use", e)
    Failed(null)
}

/** The result of importing [added] looks: none means the library filled up meanwhile. */
private fun imported(added: Int): LookFileResult = if (added == 0) NoRoom else Imported(added)

class MainViewModel(application: Application, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val appListRepository = AppListRepository(application)
    private val particleImages = ParticleImageStore(application, ioDispatcher)
    private val lookFiles = LookFiles(application, particleImages, ioDispatcher)
    private val library = LookLibrary(repository, particleImages, lookFiles)

    private val _pendingImport = MutableStateFlow<LookArchive?>(null)

    /** A look file read for import whose looks don't all fit, while the user picks which to add. */
    val pendingImport: StateFlow<LookArchive?> = _pendingImport.asStateFlow()

    init {
        viewModelScope.launch { library.deleteUnusedImagesOnChange() }
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
        viewModelScope.launch { onDone(uris.size - library.addParticleImages(lookId, uris)) }
    }

    /** Writes the looks [ids] (all or picked), with their images, to the document [uri]. */
    fun exportLooks(ids: Set<String>, uri: Uri, onDone: (LookFileResult) -> Unit) {
        viewModelScope.launch { lookFileResult { Exported(library.export(ids, uri)) }?.let(onDone) }
    }

    /** Writes the looks [ids], with their images, to a file for the share sheet; [onDone] gets its URI. */
    fun shareLooks(ids: Set<String>, onDone: (LookFileResult) -> Unit) {
        viewModelScope.launch { lookFileResult { Shareable(library.shareable(ids)) }?.let(onDone) }
    }

    /**
     * Reads the look file at [uri] and adds its looks; when they don't all fit, it waits in [pendingImport] for the
     * user to pick ([importPicked]) or cancel ([cancelImport]), and [onDone] is called then.
     */
    fun importLooks(uri: Uri, onDone: (LookFileResult) -> Unit) {
        viewModelScope.launch {
            lookFileResult {
                val archive = lookFiles.read(uri)
                val room = library.room()
                when {
                    room <= 0 -> NoRoom
                    archive.looks.size <= room -> imported(library.add(archive, archive.looks))
                    else -> null.also { _pendingImport.value = archive }
                }
            }?.let(onDone)
        }
    }

    /** Adds the looks [ids] of the [pendingImport], as many as fit. */
    fun importPicked(ids: Set<String>, onDone: (LookFileResult) -> Unit) {
        val archive = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            lookFileResult { imported(library.add(archive, archive.looks.filter { it.id in ids })) }?.let(onDone)
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

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
