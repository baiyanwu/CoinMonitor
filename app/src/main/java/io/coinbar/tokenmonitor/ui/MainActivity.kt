package io.coinbar.tokenmonitor.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.coinbar.tokenmonitor.appContainer
import io.coinbar.tokenmonitor.domain.model.AppThemeMode
import io.coinbar.tokenmonitor.overlay.OverlayPermissionHelper
import io.coinbar.tokenmonitor.overlay.OverlayServiceController
import io.coinbar.tokenmonitor.ui.navigation.TokenMonitorNavHost
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorTheme
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorThemeTokens
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppConfigurationApplier.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val container = appContainer()

        setContent {
            val preferences by container.appPreferencesRepository.observePreferences()
                .collectAsStateWithLifecycle(initialValue = container.appPreferencesRepository.getPreferences())
            val isDarkTheme = when (preferences.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            TokenMonitorTheme(
                darkTheme = isDarkTheme,
                themeTemplate = preferences.themeTemplate
            ) {
                val colors = TokenMonitorThemeTokens.colors
                SideEffect {
                    window.statusBarColor = colors.pageBackground.toArgb()
                    window.navigationBarColor = colors.cardBackground.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }
                AppConfigurationApplier.ProvideLocalizedResources(language = preferences.language) {
                    TokenMonitorNavHost(container = container)
                }
            }
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
