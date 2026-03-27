package io.baiyanwu.coinmonitor.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import io.baiyanwu.coinmonitor.appContainer
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.OverlayServiceController
import io.baiyanwu.coinmonitor.ui.navigation.CoinMonitorNavHost
import io.baiyanwu.coinmonitor.ui.search.SearchActivity
import io.baiyanwu.coinmonitor.ui.settings.OverlaySettingsActivity
import kotlinx.coroutines.launch

class MainActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            CoinMonitorNavHost(
                container = container,
                onOpenSearch = { SearchActivity.start(this@MainActivity) },
                onOpenOverlaySettings = { OverlaySettingsActivity.start(this@MainActivity) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val settings = appContainer().overlayRepository.getSettings()
            if (settings.enabled && OverlayPermissionHelper.canDrawOverlays(this@MainActivity)) {
                OverlayServiceController.start(this@MainActivity)
            } else if (!settings.enabled) {
                OverlayServiceController.stop(this@MainActivity)
            }
        }
    }
}
