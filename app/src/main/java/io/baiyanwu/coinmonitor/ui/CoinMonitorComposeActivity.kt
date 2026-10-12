package io.baiyanwu.coinmonitor.ui

import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        useDesignPageBackground: Boolean = false,
        content: @Composable (AppContainer) -> Unit
    ) {
        enableEdgeToEdge()
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
                val pageBackground = if (useDesignPageBackground) {
                    CoinMonitorThemeTokens.design.colors.pageBackground
                } else {
                    colors.pageBackground
                }
                SideEffect {
                    val systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        isDarkTheme
                    }
                    enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = systemBarStyle)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
                // 宿主替代旧的 decor 留边；消费安全区域，子页面不会重复添加系统栏或 IME 留白。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pageBackground)
                        .safeDrawingPadding()
                ) {
                    AppConfigurationApplier.ProvideLocalizedResources(language = preferences.language) {
                        content(container)
                    }
                }
            }
        }
    }
}
