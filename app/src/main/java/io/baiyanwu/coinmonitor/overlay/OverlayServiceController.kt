package io.baiyanwu.coinmonitor.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object OverlayServiceController {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, OverlayForegroundService::class.java).setAction(OverlayForegroundService.ACTION_START)
        )
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, OverlayForegroundService::class.java))
    }

    fun refreshNow(context: Context) {
        context.startService(
            Intent(context, OverlayForegroundService::class.java).setAction(OverlayForegroundService.ACTION_REFRESH_NOW)
        )
    }
}
