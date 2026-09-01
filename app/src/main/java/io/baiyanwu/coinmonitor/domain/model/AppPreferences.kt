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

enum class RefreshIntervalMode {
    CUSTOM,
    THIRTY_SECONDS,
    ONE_MINUTE
}

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeTemplate: ThemeTemplateId = ThemeTemplateId.DEFAULT_MD,
    val refreshIntervalMode: RefreshIntervalMode = RefreshIntervalMode.CUSTOM,
    val customRefreshIntervalSeconds: Int = DEFAULT_CUSTOM_REFRESH_INTERVAL_SECONDS,
    val onchainRefreshIntervalSeconds: Int = DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS,
    val klineIndicatorSettings: KlineIndicatorSettings = KlineIndicatorSettings()
) {
    val klineMainIndicator: KlineIndicator
        get() = klineIndicatorSettings.selectedMainIndicator

    val klineSubIndicator: KlineIndicator
        get() = klineIndicatorSettings.selectedSubIndicator

    val refreshIntervalSeconds: Int
        get() = when (refreshIntervalMode) {
            RefreshIntervalMode.CUSTOM -> customRefreshIntervalSeconds.coerceIn(
                MIN_CUSTOM_REFRESH_INTERVAL_SECONDS,
                MAX_CUSTOM_REFRESH_INTERVAL_SECONDS
            )

            RefreshIntervalMode.THIRTY_SECONDS -> PRESET_THIRTY_SECONDS
            RefreshIntervalMode.ONE_MINUTE -> PRESET_ONE_MINUTE_SECONDS
        }

    companion object {
        const val DEFAULT_CUSTOM_REFRESH_INTERVAL_SECONDS = 3
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = DEFAULT_CUSTOM_REFRESH_INTERVAL_SECONDS
        const val MIN_CUSTOM_REFRESH_INTERVAL_SECONDS = 3
        const val MAX_CUSTOM_REFRESH_INTERVAL_SECONDS = 10
        const val MIN_REFRESH_INTERVAL_SECONDS = MIN_CUSTOM_REFRESH_INTERVAL_SECONDS
        const val MAX_REFRESH_INTERVAL_SECONDS = MAX_CUSTOM_REFRESH_INTERVAL_SECONDS
        const val PRESET_THIRTY_SECONDS = 30
        const val PRESET_ONE_MINUTE_SECONDS = 60
        const val DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS = 45
        const val MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS = 10
        const val MAX_ONCHAIN_REFRESH_INTERVAL_SECONDS = 120
        const val ONCHAIN_REFRESH_INTERVAL_STEP_SECONDS = 5
        const val RECOMMENDED_MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS = 30

        fun normalizeOnchainRefreshIntervalSeconds(value: Int): Int {
            val clamped = value.coerceIn(
                MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS,
                MAX_ONCHAIN_REFRESH_INTERVAL_SECONDS
            )
            val steps = (
                clamped - MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS +
                    ONCHAIN_REFRESH_INTERVAL_STEP_SECONDS / 2
                ) / ONCHAIN_REFRESH_INTERVAL_STEP_SECONDS
            return MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS + steps * ONCHAIN_REFRESH_INTERVAL_STEP_SECONDS
        }
    }
}
