package io.baiyanwu.coinmonitor.ui

import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.lifecycleScope
import io.baiyanwu.coinmonitor.BuildConfig
import io.baiyanwu.coinmonitor.appContainer
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.OverlayRuntimePolicy
import io.baiyanwu.coinmonitor.overlay.OverlayServiceController
import io.baiyanwu.coinmonitor.ui.navigation.CoinMonitorNavHost
import io.baiyanwu.coinmonitor.ui.kline.AiChatHistoryActivity
import io.baiyanwu.coinmonitor.ui.kline.KlineIndicatorSettingsActivity
import io.baiyanwu.coinmonitor.ui.search.SearchActivity
import io.baiyanwu.coinmonitor.ui.settings.AboutActivity
import io.baiyanwu.coinmonitor.ui.settings.NetworkLogActivity
import io.baiyanwu.coinmonitor.ui.settings.OverlaySettingsActivity
import io.baiyanwu.coinmonitor.ui.settings.ThirdPartyApiSettingsActivity
import io.baiyanwu.coinmonitor.ui.update.AppUpdatePrompt
import kotlinx.coroutines.launch

class MainActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            val uriHandler = LocalUriHandler.current
            var availableUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
            var availableUpdateUrl by rememberSaveable { mutableStateOf<String?>(null) }

            LaunchedEffect(container) {
                container.appUpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)?.let { update ->
                    availableUpdateVersion = update.versionName
                    availableUpdateUrl = update.releaseUrl
                }
            }

            CoinMonitorNavHost(
                container = container,
                onOpenSearch = { searchMode ->
                    SearchActivity.start(this@MainActivity, initialSearchMode = searchMode)
                },
                onOpenKlineSearch = { SearchActivity.startForKline(this@MainActivity) },
                onOpenKlineHistory = { AiChatHistoryActivity.start(this@MainActivity) },
                onOpenKlineIndicatorSettings = { KlineIndicatorSettingsActivity.start(this@MainActivity) },
                onOpenOverlaySettings = { OverlaySettingsActivity.start(this@MainActivity) },
                onOpenThirdPartyApiSettings = { ThirdPartyApiSettingsActivity.start(this@MainActivity) },
                onOpenNetworkLog = { NetworkLogActivity.start(this@MainActivity) },
                onOpenAbout = { AboutActivity.start(this@MainActivity) }
            )

            val updateVersion = availableUpdateVersion
            val updateUrl = availableUpdateUrl
            if (updateVersion != null && updateUrl != null) {
                AppUpdatePrompt(
                    versionName = updateVersion,
                    onDismiss = {
                        availableUpdateVersion = null
                        availableUpdateUrl = null
                    },
                    onOpenRelease = {
                        availableUpdateVersion = null
                        availableUpdateUrl = null
                        runCatching { uriHandler.openUri(updateUrl) }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val settings = appContainer().overlayRepository.getSettings()
            val canDrawOverlays = OverlayPermissionHelper.canDrawOverlays(this@MainActivity)
            if (OverlayRuntimePolicy.shouldRunOverlay(settings.enabled, canDrawOverlays)) {
                OverlayServiceController.start(this@MainActivity)
            } else {
                if (settings.enabled && !canDrawOverlays) {
                    appContainer().overlayRepository.setEnabled(false)
                }
                OverlayServiceController.stop(this@MainActivity)
            }
        }
    }
}
