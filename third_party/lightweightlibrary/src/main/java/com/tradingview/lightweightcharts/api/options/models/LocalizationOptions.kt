package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.runtime.plugins.Plugin

data class LocalizationOptions(
    /**
     * Current locale, which will be used for formatting dates.
     */
    var locale: String? = null,

    /**
     * User-defined function for price formatting.
     * Could be used for some specific cases, that could not be covered with PriceFormat
     */
    var priceFormatter: Plugin? = null,

    /**
     * User-defined function for time formatting.
     */
    var timeFormatter: Plugin? = null,

    /**
     * Date formatting string.
     * Might contains `yyyy`, `yy`, `MMMM`, `MMM`, `MM` and `dd`
     * literals which will be replaced with corresponding date's value.
     * Ignored if timeFormatter has been specified.
     */
    var dateFormat: String? = null
)

inline fun localizationOptions(init: LocalizationOptions.() -> Unit): LocalizationOptions {
    return LocalizationOptions().apply(init)
}
