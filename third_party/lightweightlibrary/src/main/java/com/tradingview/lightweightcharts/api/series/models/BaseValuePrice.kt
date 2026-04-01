package com.tradingview.lightweightcharts.api.series.models

/**
 * Represents a type of priced base value of baseline series type.
 */
data class BaseValuePrice(
    val price: Int,
    val type: BaseValuePriceType = BaseValuePriceType.PRICE
) : BaseValueType

enum class BaseValuePriceType {
    PRICE
}
