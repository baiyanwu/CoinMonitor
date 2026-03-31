package com.tradingview.lightweightcharts.api.options.models

/**
 * Structure describing options of the chart. Series options are to be set separately
 */
data class ChartOptions(
    /**
     * Structure with watermark options
     */
    var watermark: WatermarkOptions? = null,

    /**
     * Structure with layout options
     */
    var layout: LayoutOptions? = null,

    /**
     * ## Deprecated
     * Use [leftPriceScale]/[rightPriceScale]/[overlayPriceScale] instead.
     *
     * Structure with price scale options
     */
    @Deprecated("Use leftPriceScale/rightPriceScale/overlayPriceScale instead")
    var priceScale: PriceScaleOptions? = null,

    var leftPriceScale: PriceScaleOptions? = null,

    var rightPriceScale: PriceScaleOptions? = null,

    var overlayPriceScale: PriceScaleOptions? = null,

    /**
     * Structure with time scale options
     */
    var timeScale: TimeScaleOptions? = null,

    /**
     * Structure with crosshair options
     */
    var crosshair: CrosshairOptions? = null,

    /**
     * Structure with grid options
     */
    var grid: GridOptions? = null,

    /**
     * Structure with localization options
     */
    var localization: LocalizationOptions? = null,

    /**
     * Structure that describes scrolling behavior
     */
    var handleScroll: HandleScrollOptions? = null,

    /**
     * Structure that describes scaling behavior
     */
    var handleScale: HandleScaleOptions? = null,

    /**
     * Structure that describes kinetic scroll behavior
     */
    var kineticScroll: KineticScrollOptions? = null,

    /**
     * Structure that describes tracking mode's behavior
     */
    var trackingMode: TrackingModeOptions? = null,
)

inline fun chartOptions(init: ChartOptions.() -> Unit): ChartOptions {
    return ChartOptions().apply(init)
}
