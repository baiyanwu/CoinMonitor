package com.tradingview.lightweightcharts.api.series.common

import com.tradingview.lightweightcharts.api.options.models.PriceLineOptions

interface PriceLine {

    object Func {
        const val OPTIONS = "priceLineOptions"
        const val APPLY_OPTIONS = "priceLineApplyOptions"
    }

    object Params {
        const val LINE_ID = "lineId"
        const val OPTIONS_PARAM = "options"
    }

    val uuid: String

    fun applyOptions(options: PriceLineOptions)
    fun options(block: (PriceLineOptions?) -> Unit)
}
