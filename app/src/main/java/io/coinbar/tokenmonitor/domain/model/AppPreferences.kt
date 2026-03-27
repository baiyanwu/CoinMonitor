package io.coinbar.tokenmonitor.domain.model

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
    val themeTemplate: ThemeTemplateId = ThemeTemplateId.DEFAULT_MD
)
