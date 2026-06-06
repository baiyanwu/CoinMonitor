package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.chart.models.color.Colorable
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.common.AreaStyleOptions
import com.tradingview.lightweightcharts.api.series.enums.LastPriceAnimationMode
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineType
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.PriceLineSource
import com.tradingview.lightweightcharts.api.series.models.PriceFormat
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.runtime.plugins.Plugin

data class AreaSeriesOptions(

    // ------------ SeriesOptionsCommon ----------------
    /**
     * Visibility of the label with the latest visible price on the price scale
     */
    override var lastValueVisible: Boolean? = null,

    /**
     * Title of the series. This label is placed with price axis label
     */
    override var title: String? = null,

    /**
     * Visibility of the price line. Price line is a horizontal line indicating the last price of the series
     */
    override var priceLineVisible: Boolean? = null,

    /**
     * Enum of possible modes of priceLine source
     */
    override var priceLineSource: PriceLineSource? = null,

    /**
     * Width of the price line. Ignored if priceLineVisible is false
     */
    override var priceLineWidth: LineWidth? = null,

    /**
     * Color of the price line. Ignored if priceLineVisible is false
     */
    override var priceLineColor: IntColor? = null,

    /**
     * Price line style. Suitable for percentage and indexedTo100 scales
     */
    override var priceLineStyle: LineStyle? = null,

    /**
     * Formatting settings associated with the series
     */
    override var priceFormat: PriceFormat? = null,

    /**
     * Visibility of base line. Suitable for percentage and indexedTo100 scales
     */
    override var baseLineVisible: Boolean? = null,

    /**
     * Color of the base line in IndexedTo100 mode
     */
    override var baseLineColor: Colorable? = null,

    /**
     * Base line width. Suitable for percentage and indexedTo100 scales.
     * Ignored if baseLineVisible is not set
     */
    override var baseLineWidth: LineWidth? = null,

    /**
     * Base line style. Suitable for percentage and indexedTo100 scales.
     * Ignored if baseLineVisible is not set
     */
    override var baseLineStyle: LineStyle? = null,

    /**
     * Target price scale to bind new series to
     */
    override var priceScaleId: PriceScaleId? = null,

    /**
     *  function that overrides calculating of visible prices range
     */
    override var autoscaleInfoProvider: Plugin? = null,

    /**
     * Visibility of series
     */
    override var visible: Boolean? = null,

    // ----------  AreaStyleOptions    ---------------------
    /**
     * Color of the top part of the area.
     */
    override var topColor: IntColor? = null,

    /**
     * Color of the bottom part of the area.
     */
    override var bottomColor: IntColor? = null,

    /**
     * Invert the filled area. Fills the area above the line if set to true.
     */
    override var invertFilledArea: Boolean? = null,

    /**
     * Line color.
     */
    override var lineColor: IntColor? = null,

    /**
     * Line style.
     */
    override var lineStyle: LineStyle? = null,

    /**
     * Line width in pixels.
     */
    override var lineWidth: LineWidth? = null,

    /**
     * Line type.
     */
    override var lineType: LineType? = null,

    /**
     * Show the crosshair marker.
     */
    override var crosshairMarkerVisible: Boolean? = null,

    /**
     * Crosshair marker radius in pixels.
     */
    override var crosshairMarkerRadius: Float? = null,

    /**
     * Crosshair marker border color. An empty string falls back to the the color of the series under the crosshair.
     */
    override var crosshairMarkerBorderColor: IntColor? = null,

    /**
     * The crosshair marker background color. An empty string falls back to the the color of the series under the crosshair.
     */
    override var crosshairMarkerBackgroundColor: IntColor? = null,

    /**
     * Crosshair marker border width in pixels.
     */
    override var crosshairMarkerBorderWidth: Float? = null,

    /**
     * Last price animation mode.
     */
    override var lastPriceAnimation: LastPriceAnimationMode? = null,

) : SeriesOptionsCommon, AreaStyleOptions

inline fun areaSeriesOptions(init: AreaSeriesOptions.() -> Unit): AreaSeriesOptions {
    return AreaSeriesOptions().apply(init)
}

inline fun SeriesApi.applyAreaSeriesOptions(init: AreaSeriesOptions.() -> Unit) {
    applyOptions(AreaSeriesOptions().apply(init))
}
