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
import androidx.core.app.NotificationCompat
import io.baiyanwu.coinmonitor.appContainer
import io.baiyanwu.coinmonitor.boot.OverlayRestoreReceiver
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var coordinator: OverlayPriceRefreshCoordinator
    private lateinit var windowController: OverlayWindowController

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
            marketQuoteRepository = container.marketQuoteRepository
        ) { items, settings ->
            if (settings.enabled) {
                windowController.showOrUpdate(items, settings)
            } else {
                windowController.hide()
            }
        }
        coordinator.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    appContainer().overlayRepository.setEnabled(false)
                    stopSelfSafely()
                }
            }

            ACTION_REFRESH_NOW -> {
                startForegroundIfNeeded()
                serviceScope.launch {
                    coordinator.refreshNow()
                }
            }

            ACTION_START, null -> {
                startForegroundIfNeeded()
                serviceScope.launch {
                    val settings = appContainer().overlayRepository.getSettings()
                    if (!settings.enabled) {
                        appContainer().overlayRepository.setEnabled(true)
                    }

                    if (!OverlayPermissionHelper.canDrawOverlays(this@OverlayForegroundService)) {
                        stopSelfSafely()
                        return@launch
                    }

                    val (items, latestSettings) = coordinator.snapshot()
                    windowController.showOrUpdate(items, latestSettings)
                    coordinator.refreshNow()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        coordinator.stop()
        windowController.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun stopSelfSafely() {
        windowController.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundIfNeeded() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localizedContext.getString(R.string.overlay_notification_title))
            .setContentText(localizedContext.getString(R.string.overlay_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, localizedContext.getString(R.string.overlay_notification_stop), stopIntent)
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

    companion object {
        const val ACTION_START = "io.baiyanwu.coinmonitor.action.START_OVERLAY"
        const val ACTION_STOP = "io.baiyanwu.coinmonitor.action.STOP_OVERLAY"
        const val ACTION_REFRESH_NOW = "io.baiyanwu.coinmonitor.action.REFRESH_NOW"
        const val ACTION_RESTART = "io.baiyanwu.coinmonitor.action.RESTART_OVERLAY"

        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1001
    }
}
