package io.baiyanwu.coinmonitor.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.baiyanwu.coinmonitor.appContainer
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.OverlayRuntimePolicy
import io.baiyanwu.coinmonitor.overlay.OverlayServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val settings = context.appContainer().overlayRepository.getSettings()
                val canDrawOverlays = OverlayPermissionHelper.canDrawOverlays(context)
                if (OverlayRuntimePolicy.shouldRunOverlay(settings.enabled, canDrawOverlays)) {
                    OverlayServiceController.start(context)
                } else if (settings.enabled && !canDrawOverlays) {
                    context.appContainer().overlayRepository.setEnabled(false)
                }
            }
            pendingResult.finish()
        }
    }
}
