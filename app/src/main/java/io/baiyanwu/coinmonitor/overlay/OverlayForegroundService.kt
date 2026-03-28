package io.baiyanwu.coinmonitor.overlay

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.baiyanwu.coinmonitor.appContainer
import io.baiyanwu.coinmonitor.boot.OverlayRestoreReceiver
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class OverlayForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private lateinit var coordinator: OverlayPriceRefreshCoordinator
    private lateinit var windowController: OverlayWindowController
    private var isForegroundStarted: Boolean = false
    private var latestSettings: OverlaySettings = OverlaySettings()

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()

        val container = appContainer()
        windowController = OverlayWindowController(
            context = this,
            overlayRepository = container.overlayRepository,
            appPreferencesRepository = container.appPreferencesRepository,
            scope = serviceScope
        )
        coordinator = OverlayPriceRefreshCoordinator(
            scope = serviceScope,
            watchlistRepository = container.watchlistRepository,
            overlayRepository = container.overlayRepository,
            appPreferencesRepository = container.appPreferencesRepository,
            marketQuoteRepository = container.marketQuoteRepository
        ) { items, settings ->
            // 临时隐藏属于运行态，后续行情刷新时也必须继续尊重这个状态。
            if (settings.enabled && !OverlayRuntimeSession.temporarilyHidden.value) {
                windowController.showOrUpdate(items, settings)
            } else {
                windowController.hide()
            }
        }
        coordinator.start()

        serviceScope.launch {
            combine(
                container.overlayRepository.observeSettings(),
                OverlayRuntimeSession.temporarilyHidden
            ) { settings, temporarilyHidden ->
                NotificationSnapshot(
                    settings = settings,
                    temporarilyHidden = temporarilyHidden
                )
            }.collect { snapshot ->
                latestSettings = snapshot.settings
                if (isForegroundStarted) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            settings = snapshot.settings,
                            temporarilyHidden = snapshot.temporarilyHidden
                        )
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                startForegroundIfNeeded()
                serviceScope.launch {
                    appContainer().overlayRepository.setEnabled(false)
                    stopSelfSafely()
                }
                return START_NOT_STICKY
            }

            ACTION_HIDE_OVERLAY -> {
                startForegroundIfNeeded()
                OverlayRuntimeSession.setTemporarilyHidden(true)
                windowController.hide()
                return START_STICKY
            }

            ACTION_SHOW_OVERLAY -> {
                startForegroundIfNeeded()
                OverlayRuntimeSession.setTemporarilyHidden(false)
                serviceScope.launch {
                    renderLatestOverlay()
                }
                return START_STICKY
            }

            ACTION_TOGGLE_DRAG -> {
                startForegroundIfNeeded()
                serviceScope.launch {
                    val settings = appContainer().overlayRepository.getSettings()
                    appContainer().overlayRepository.setLocked(!settings.locked)
                }
                return START_STICKY
            }

            ACTION_REFRESH_NOW -> {
                startForegroundIfNeeded()
                serviceScope.launch {
                    val settings = appContainer().overlayRepository.getSettings()
                    val canDrawOverlays = OverlayPermissionHelper.canDrawOverlays(this@OverlayForegroundService)
                    if (OverlayRuntimePolicy.shouldRunOverlay(settings.enabled, canDrawOverlays)) {
                        coordinator.refreshNow()
                    } else {
                        if (settings.enabled && !canDrawOverlays) {
                            appContainer().overlayRepository.setEnabled(false)
                        }
                        stopSelfSafely()
                    }
                }
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                startForegroundIfNeeded()
                serviceScope.launch {
                    if (!renderLatestOverlay()) return@launch
                    coordinator.refreshNow()
                }
                return START_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        coordinator.stop()
        windowController.hide()
        OverlayRuntimeSession.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            val settings = appContainer().overlayRepository.getSettings()
            if (
                OverlayRuntimePolicy.shouldRunOverlay(
                    settingsEnabled = settings.enabled,
                    canDrawOverlays = OverlayPermissionHelper.canDrawOverlays(this@OverlayForegroundService)
                )
            ) {
                scheduleRestart()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun stopSelfSafely() {
        OverlayRuntimeSession.reset()
        windowController.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
        stopSelf()
    }

    private fun startForegroundIfNeeded() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                settings = latestSettings,
                temporarilyHidden = OverlayRuntimeSession.temporarilyHidden.value
            )
        )
        isForegroundStarted = true
    }

    private fun buildNotification(
        settings: OverlaySettings,
        temporarilyHidden: Boolean
    ): Notification {
        val preferences = appContainer().appPreferencesRepository.getPreferences()
        val localizedContext = AppConfigurationApplier.wrapContext(this, preferences)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val visibilityIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, OverlayForegroundService::class.java).setAction(
                if (temporarilyHidden) ACTION_SHOW_OVERLAY else ACTION_HIDE_OVERLAY
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dragIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, OverlayForegroundService::class.java).setAction(ACTION_TOGGLE_DRAG),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            temporarilyHidden -> localizedContext.getString(R.string.overlay_notification_hidden_text)
            settings.locked -> localizedContext.getString(
                R.string.overlay_notification_locked_text,
                preferences.refreshIntervalSeconds
            )

            else -> localizedContext.getString(
                R.string.overlay_notification_unlocked_text,
                preferences.refreshIntervalSeconds
            )
        }

        val customContent = buildNotificationContentView(
            title = localizedContext.getString(R.string.overlay_notification_title),
            contentText = contentText,
            visibilityLabel = localizedContext.getString(
                if (temporarilyHidden) {
                    R.string.overlay_notification_show_short
                } else {
                    R.string.overlay_notification_hide_short
                }
            ),
            dragLabel = localizedContext.getString(
                if (settings.locked) {
                    R.string.overlay_notification_free_drag
                } else {
                    R.string.overlay_notification_lock_drag
                }
            ),
            stopLabel = localizedContext.getString(R.string.overlay_notification_stop),
            visibilityIntent = visibilityIntent,
            dragIntent = dragIntent,
            stopIntent = stopIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localizedContext.getString(R.string.overlay_notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customContent)
            .setCustomBigContentView(customContent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val preferences = appContainer().appPreferencesRepository.getPreferences()
        val localizedContext = AppConfigurationApplier.wrapContext(this, preferences)

        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localizedContext.getString(R.string.overlay_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun scheduleRestart() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, OverlayRestoreReceiver::class.java).apply {
            action = ACTION_RESTART
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1_000,
            pendingIntent
        )
    }

    private suspend fun renderLatestOverlay(): Boolean {
        val settings = appContainer().overlayRepository.getSettings()
        val canDrawOverlays = OverlayPermissionHelper.canDrawOverlays(this@OverlayForegroundService)
        if (!OverlayRuntimePolicy.shouldRunOverlay(settings.enabled, canDrawOverlays)) {
            if (settings.enabled && !canDrawOverlays) {
                appContainer().overlayRepository.setEnabled(false)
            }
            stopSelfSafely()
            return false
        }

        val (items, latestSettings) = coordinator.snapshot()
        if (OverlayRuntimeSession.temporarilyHidden.value) {
            windowController.hide()
        } else {
            windowController.showOrUpdate(items, latestSettings)
        }
        return true
    }

    companion object {
        const val ACTION_START = "io.baiyanwu.coinmonitor.action.START_OVERLAY"
        const val ACTION_STOP = "io.baiyanwu.coinmonitor.action.STOP_OVERLAY"
        const val ACTION_REFRESH_NOW = "io.baiyanwu.coinmonitor.action.REFRESH_NOW"
        const val ACTION_HIDE_OVERLAY = "io.baiyanwu.coinmonitor.action.HIDE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "io.baiyanwu.coinmonitor.action.SHOW_OVERLAY"
        const val ACTION_TOGGLE_DRAG = "io.baiyanwu.coinmonitor.action.TOGGLE_DRAG"
        const val ACTION_RESTART = "io.baiyanwu.coinmonitor.action.RESTART_OVERLAY"

        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1001
    }

    private fun buildNotificationContentView(
        title: String,
        contentText: String,
        visibilityLabel: String,
        dragLabel: String,
        stopLabel: String,
        visibilityIntent: PendingIntent,
        dragIntent: PendingIntent,
        stopIntent: PendingIntent
    ): RemoteViews {
        return RemoteViews(packageName, R.layout.overlay_notification_content).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_body, contentText)
            setTextViewText(R.id.action_visibility, visibilityLabel)
            setTextViewText(R.id.action_drag, dragLabel)
            setTextViewText(R.id.action_stop, stopLabel)

            val titleColor = ContextCompat.getColor(this@OverlayForegroundService, R.color.overlay_notification_title_color)
            val bodyColor = ContextCompat.getColor(this@OverlayForegroundService, R.color.overlay_notification_body_color)
            val actionColor = ContextCompat.getColor(this@OverlayForegroundService, R.color.overlay_notification_action_color)
            setTextColor(R.id.notification_title, titleColor)
            setTextColor(R.id.notification_body, bodyColor)
            setTextColor(R.id.action_visibility, actionColor)
            setTextColor(R.id.action_drag, actionColor)
            setTextColor(R.id.action_stop, actionColor)

            setOnClickPendingIntent(R.id.action_visibility, visibilityIntent)
            setOnClickPendingIntent(R.id.action_drag, dragIntent)
            setOnClickPendingIntent(R.id.action_stop, stopIntent)
        }
    }
}

private data class NotificationSnapshot(
    val settings: OverlaySettings,
    val temporarilyHidden: Boolean
)
