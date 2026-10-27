package app.rimlit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.rimlit.data.AppInfo
import app.rimlit.data.AppListRepository
import app.rimlit.data.SettingsRepository
import app.rimlit.engine.GlowEvent
import app.rimlit.engine.PreviewTarget
import app.rimlit.engine.onScreenOverlay
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
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

class MainViewModel(application: Application, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val appListRepository = AppListRepository(application)

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
