package app.rimlit

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.rimlit.data.AppInfo
import app.rimlit.data.AppListRepository
import app.rimlit.data.SettingsRepository
import app.rimlit.model.DEFAULT_GLOW_ARGB
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.overlay.EdgeOverlayController
import app.rimlit.overlay.GlowAccessibilityService
import app.rimlit.permissions.PermissionChecks
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionLinks
import app.rimlit.permissions.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val appListRepository = AppListRepository(application)
    private val overlayController by lazy { EdgeOverlayController(application) }

    val settings: StateFlow<GlowSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlowSettings(),
    )

    private val _permissions = MutableStateFlow(PermissionChecks.statuses(application))
    val permissions: StateFlow<List<PermissionStatus>> = _permissions.asStateFlow()

    val apps: StateFlow<List<AppInfo>> = flow { emit(appListRepository.launchableApps()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Re-evaluate permission grants; called from [MainActivity.onResume]. */
    fun refreshPermissions() {
        _permissions.value = PermissionChecks.statuses(getApplication())
    }

    /** Persists a settings change (atomic read-modify-write). */
    fun update(transform: (GlowSettings) -> GlowSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun openPermission(kind: PermissionKind) {
        val app = getApplication<Application>()
        when (kind) {
            PermissionKind.NOTIFICATION_ACCESS -> PermissionLinks.notificationAccess(app)
            PermissionKind.OVERLAY -> PermissionLinks.overlay(app)
            PermissionKind.FULL_SCREEN_INTENT -> PermissionLinks.fullScreenIntent(app)
            PermissionKind.ACCESSIBILITY -> PermissionLinks.accessibility(app)
            PermissionKind.BATTERY_OPTIMIZATION -> PermissionLinks.batteryOptimization(app)
        }
    }

    /** Shows the AOD-style lock-screen glow for [spec] immediately (preview). */
    fun previewLockScreen(spec: GlowSpec) {
        val app = getApplication<Application>()
        app.startActivity(LockScreenGlowActivity.newIntent(app, spec))
    }

    /** Shows the on-screen overlay glow for [spec] immediately (preview), bypassing the notification pipeline. */
    fun previewOverlay(spec: GlowSpec) {
        (GlowAccessibilityService.overlay ?: overlayController).show(spec)
    }

    /**
     * Posts a test notification through the real listener pipeline so the user can
     * check the exact end-to-end effect (screen on → overlay; screen off → lock screen).
     */
    fun sendTestNotification() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                GlowListenerService.TEST_CHANNEL_ID,
                app.getString(R.string.test_notification_channel_name),
                // DEFAULT, not HIGH: a heads-up banner would cover the glow being tested.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = app.getString(R.string.test_notification_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val notification = Notification.Builder(app, GlowListenerService.TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.test_notification_title))
            .setContentText(app.getString(R.string.test_notification_desc))
            .setAutoCancel(true)
            .setTimeoutAfter(settings.value.durationMs)
            .build()
        manager.notify(TEST_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TEST_NOTIFICATION_ID = 100

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
fun GlowSettings.previewSpec(): GlowSpec = toGlowSpec(packageName = null) { MainViewModel.PREVIEW_AUTO_PALETTE }
