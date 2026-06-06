package com.tradingview.lightweightcharts.api.series.common

/** Interface to be implemented by the object in order to be used as a price formatter */
interface PriceFormatter {

    object Func {
        const val PRICE_FORMATTER = "formatPrice"
    }

    object Params {
        const val FORMATTER_ID = "formatterId"
        const val PRICE = "price"
    }

    val uuid: String

    /**
     * Formatting function
     * @param price - original price to be formatted
     * @returns - formatted price
     */
    fun format(price: Float, result: (String?) -> Unit)
}
