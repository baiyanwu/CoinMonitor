package io.baiyanwu.coinmonitor.ui

import android.content.Context
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.baiyanwu.coinmonitor.appContainer
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/**
 * 统一承接主题、语言和系统栏样式，避免每个页面 Activity 各自重复一套 Compose 宿主配置。
 */
abstract class CoinMonitorComposeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppConfigurationApplier.wrapContext(newBase))
    }

    protected fun setCoinMonitorContent(
        content: @Composable (AppContainer) -> Unit
    ) {
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

            CoinMonitorTheme(
                darkTheme = isDarkTheme,
                themeTemplate = preferences.themeTemplate
            ) {
                val colors = CoinMonitorThemeTokens.colors
                SideEffect {
                    window.statusBarColor = colors.pageBackground.toArgb()
                    window.navigationBarColor = colors.cardBackground.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }
                AppConfigurationApplier.ProvideLocalizedResources(language = preferences.language) {
                    content(container)
                }
            }
        }
    }
}
