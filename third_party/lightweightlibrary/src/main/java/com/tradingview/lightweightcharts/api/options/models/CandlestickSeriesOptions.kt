package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.chart.models.color.Colorable
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.common.CandlestickStyleOptions
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.PriceLineSource
import com.tradingview.lightweightcharts.api.series.models.PriceFormat
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.runtime.plugins.Plugin

data class CandlestickSeriesOptions(
    override var title: String? = null,
    override var lastValueVisible: Boolean? = null,
    override var priceLineVisible: Boolean? = null,
    override var priceLineSource: PriceLineSource? = null,
    override var priceLineWidth: LineWidth? = null,

    override var priceLineColor: IntColor? = null,
    override var priceLineStyle: LineStyle? = null,
    override var priceFormat: PriceFormat? = null,
    override var baseLineVisible: Boolean? = null,

    override var baseLineColor: Colorable? = null,

    override var baseLineWidth: LineWidth? = null,
    override var baseLineStyle: LineStyle? = null,

    override var upColor: IntColor? = null,

    override var downColor: IntColor? = null,

    override var wickVisible: Boolean? = null,
    override var borderVisible: Boolean? = null,

    override var borderColor: IntColor? = null,

    override var borderUpColor: IntColor? = null,

    override var borderDownColor: IntColor? = null,

    override var wickColor: IntColor? = null,

    override var wickUpColor: IntColor? = null,

    override var wickDownColor: IntColor? = null,

    override var priceScaleId: PriceScaleId? = null,
    override var autoscaleInfoProvider: Plugin? = null,
    override var visible: Boolean? = null
) : SeriesOptionsCommon, CandlestickStyleOptions

inline fun candlestickSeriesOptions(init: CandlestickSeriesOptions.() -> Unit): CandlestickSeriesOptions {
    return CandlestickSeriesOptions().apply(init)
}

inline fun SeriesApi.applyCandlestickSeriesOptions(init: CandlestickSeriesOptions.() -> Unit) {
    applyOptions(CandlestickSeriesOptions().apply(init))
}
