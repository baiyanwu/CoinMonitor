package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.chart.models.color.Colorable
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.PriceLineSource
import com.tradingview.lightweightcharts.api.series.models.PriceFormat
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.runtime.plugins.Plugin

/**
 * Structure describing options common for all types of series
 */
interface SeriesOptionsCommon {
    /**
     * Visibility of the label with the latest visible price on the price scale
     */
    val lastValueVisible: Boolean?

    /**
     * Title of the series. This label is placed with price axis label
     */
    val title: String?

    /**
     * Visibility of the price line. Price line is a horizontal line indicating the last price of the series
     */
    val priceLineVisible: Boolean?

    /**
     * Enum of possible modes of priceLine source
     */
    val priceLineSource: PriceLineSource?

    /**
     * Width of the price line. Ignored if priceLineVisible is false
     */
    val priceLineWidth: LineWidth?

    /**
     * Color of the price line. Ignored if priceLineVisible is false
     */
    val priceLineColor: IntColor?

    /**
     * Price line style. Suitable for percentage and indexedTo100 scales
     */
    val priceLineStyle: LineStyle?

    /**
     * Formatting settings associated with the series
     */
    val priceFormat: PriceFormat?

    /**
     * Visibility of base line. Suitable for percentage and indexedTo100 scales
     */
    val baseLineVisible: Boolean?

    /**
     * Color of the base line in IndexedTo100 mode
     */
    val baseLineColor: Colorable?

    /**
     * Base line width. Suitable for percentage and indexedTo100 scales.
     * Ignored if baseLineVisible is not set
     */
    val baseLineWidth: LineWidth?

    /**
     * Base line style. Suitable for percentage and indexedTo100 scales.
     * Ignored if baseLineVisible is not set
     */
    val baseLineStyle: LineStyle?

    /**
     * Target price scale to bind new series to
     */
    val priceScaleId: PriceScaleId?

    /**
     *  function that overrides calculating of visible prices range
     */
    val autoscaleInfoProvider: Plugin?

    /**
     * Visibility of series
     */
    val visible: Boolean?
}
