package io.baiyanwu.coinmonitor.domain.model

enum class ThemeTemplateId {
    DEFAULT_MD
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    CHINESE_SIMPLIFIED("zh-CN"),
    ENGLISH("en")
}

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeTemplate: ThemeTemplateId = ThemeTemplateId.DEFAULT_MD,
    val refreshIntervalSeconds: Int = DEFAULT_REFRESH_INTERVAL_SECONDS
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 3
        const val MIN_REFRESH_INTERVAL_SECONDS = 1
        const val MAX_REFRESH_INTERVAL_SECONDS = 10
    }
}
