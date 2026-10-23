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
import app.rimlit.model.DEFAULT_GLOW_ARGB
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.notify.GlowNotifications
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.permissions.Permissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    val apps: StateFlow<List<AppInfo>> = flow { emit(appListRepository.launchableApps()) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Re-evaluate permission grants; called from [MainActivity.onResume]. */
    fun refreshPermissions() {
        _permissions.value = Permissions.statuses(getApplication())
    }

    /** Persists a settings change (atomic read-modify-write). */
    fun update(transform: (GlowSettings) -> GlowSettings) {
        viewModelScope.launch { repository.update(transform) }
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
        /** Stand-in for the auto palette in previews, where there is no notifying app to sample. */
        val PREVIEW_AUTO_PALETTE = listOf(DEFAULT_GLOW_ARGB, 0xFFE040FB.toInt())

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] ?: error("Application not available")
                MainViewModel(app)
            }
        }
    }
}

/** The spec previews render for these settings (auto colors use [MainViewModel.PREVIEW_AUTO_PALETTE]). */
fun GlowSettings.previewSpec(): GlowSpec =
    toGlowSpec(paletteFor(packageName = null) ?: GlowPalette.Colors(MainViewModel.PREVIEW_AUTO_PALETTE))
