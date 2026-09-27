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

enum class OnchainRefreshMode {
    SMART,
    FIXED
}

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeTemplate: ThemeTemplateId = ThemeTemplateId.DEFAULT_MD,
    val refreshIntervalMode: RefreshIntervalMode = RefreshIntervalMode.CUSTOM,
    val customRefreshIntervalSeconds: Int = DEFAULT_CUSTOM_REFRESH_INTERVAL_SECONDS,
    val onchainRefreshMode: OnchainRefreshMode = OnchainRefreshMode.SMART,
    val onchainRefreshIntervalSeconds: Int = DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS,
    val onchainProviderOrder: List<OnchainDataProvider> = DEFAULT_ONCHAIN_PROVIDER_ORDER,
    val showOnchainMarketCap: Boolean = false,
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
        const val DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS = 30
        const val MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS = 30
        const val MAX_ONCHAIN_REFRESH_INTERVAL_SECONDS = 120
        val DEFAULT_ONCHAIN_PROVIDER_ORDER = listOf(
            OnchainDataProvider.DEX_SCREENER,
            OnchainDataProvider.OKX_DEX
        )

        fun normalizeOnchainRefreshIntervalSeconds(value: Int): Int {
            return value.coerceIn(
                MIN_ONCHAIN_REFRESH_INTERVAL_SECONDS,
                MAX_ONCHAIN_REFRESH_INTERVAL_SECONDS
            )
        }

        fun normalizeOnchainProviderOrder(
            providers: List<OnchainDataProvider>
        ): List<OnchainDataProvider> {
            return buildList {
                providers.distinct().forEach(::add)
                OnchainDataProvider.entries.filterNot(::contains).forEach(::add)
            }
        }

        val ONCHAIN_FIXED_INTERVAL_OPTIONS_SECONDS = listOf(30, 45, 60, 120)
    }
}
