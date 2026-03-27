package io.coinbar.tokenmonitor.ui.theme

import android.content.Context
import android.content.res.Configuration
import io.coinbar.tokenmonitor.domain.model.AppPreferences
import io.coinbar.tokenmonitor.domain.model.AppThemeMode
import io.coinbar.tokenmonitor.domain.model.ThemeTemplateId

fun resolveDarkTheme(context: Context, themeMode: AppThemeMode): Boolean {
    return when (themeMode) {
        AppThemeMode.SYSTEM -> {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

fun resolveTokenMonitorColors(
    context: Context,
    preferences: AppPreferences
): TokenMonitorColors {
    val paletteSet = when (preferences.themeTemplate) {
        ThemeTemplateId.DEFAULT_MD -> DefaultMdPalette
    }
    val palette = if (resolveDarkTheme(context, preferences.themeMode)) {
        paletteSet.dark
    } else {
        paletteSet.light
    }
    return palette.toSemanticColors()
}
