package com.tradingview.lightweightcharts.api.options.common

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

/** Structure describing a drawing style of the candlestick chart  */
interface CandlestickStyleOptions {
    /** Color of rising candlesticks */
    val upColor: IntColor?

    /** Color of falling candlesticks */
    val downColor: IntColor?

    /** Flag to draw/hide candlestick wicks */
    val wickVisible: Boolean?

    /** Flag to draw/hide candlestick borders around bodies */
    val borderVisible: Boolean?

    /**
     * Color of borders around candles' bodies. Ignored if borderVisible == false
     * If specified, it overrides both borderUpColor and borderDownColor options
     */
    val borderColor: IntColor?

    /** Color of the border of rising candlesticks. Ignored if borderVisible == false or borderColor is specified */
    val borderUpColor: IntColor?

    /** Color of the border of rising candlesticks. Ignored if borderVisible == false or borderColor is specified */
    val borderDownColor: IntColor?

    /**
     * Color of candlestick wicks. Ignored if wickVisible == false
     * If specified, it overrides both wickUpColor and wickDownColor options
     */
    val wickColor: IntColor?

    /** Color of rising candlestick wicks. Ignored if wickVisible == false or wickColor is specified */
    val wickUpColor: IntColor?

    /** Color of falling candlestick wicks. Ignored if wickVisible == false or wickColor is specified */
    val wickDownColor: IntColor?
}
