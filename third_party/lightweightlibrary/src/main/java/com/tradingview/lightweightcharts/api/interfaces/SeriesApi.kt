package com.tradingview.lightweightcharts.api.interfaces

import com.tradingview.lightweightcharts.api.options.enums.MismatchDirection
import com.tradingview.lightweightcharts.api.options.models.PriceLineOptions
import com.tradingview.lightweightcharts.api.options.models.SeriesOptionsCommon
import com.tradingview.lightweightcharts.api.series.common.PriceLine
import com.tradingview.lightweightcharts.api.series.common.SeriesData
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.api.series.models.SeriesMarker

interface SeriesApi {

    object Func {
        const val ADD_LINE_SERIES = "addLineSeries"
        const val ADD_BASELINE_SERIES = "addBaselineSeries"
        const val ADD_AREA_SERIES = "addAreaSeries"
        const val ADD_BAR_SERIES = "addBarSeries"
        const val ADD_CANDLESTICK_SERIES = "addCandlestickSeries"
        const val ADD_HISTOGRAM_SERIES = "addHistogramSeries"
        const val SET_SERIES = "setSeries"
        const val PRICE_TO_COORDINATE = "priceToCoordinate"
        const val COORDINATE_TO_PRICE = "coordinateToPrice"
        const val APPLY_OPTIONS = "applyOptionsSeries"
        const val PRICE_SCALE_SERIES = "priceScaleSeries"
        const val DATA_BY_INDEX_SERIES = "dataByIndexSeries"
        const val SET_MARKERS = "setMarkers"
        const val GET_MARKERS_SERIES = "getMarkersSeries"
        const val CREATE_PRICE_LINE = "createPriceLine"
        const val REMOVE_PRICE_LINE = "removePriceLine"
        const val UPDATE = "update"
        const val SERIES_TYPE = "seriesType"
        const val MOVE_TO_PANE = "moveToPane"
        const val GET_PANE = "getPane"
    }

    object Params {
        const val SERIES_UUID = "seriesId"
        const val LINE_ID = "lineId"
        const val DATA = "data"
        const val PRICE = "price"
        const val COORDINATE = "coordinate"
        const val OPTIONS = "options"
        const val BAR = "bar"
        const val LOGICAL_INDEX = "logicalIndex"
        const val MISMATCH_DIRECTION = "mismatchDirection"
        const val PANE_INDEX = "paneIndex"
    }

    val uuid: String

    /**
     * Converts specified series price to pixel coordinate according to the chart price scale
     * @param price input price to be converted
     * @param onCoordinateReceived pixel coordinate of the price level on the chart
     */
    fun priceToCoordinate(price: Float, onCoordinateReceived: (Float?) -> Unit)

    /**
     * Converts specified coordinate to price value according to the series price scale
     * @param coordinate input coordinate to be converted
     * @param onPriceReceived price value of the coordinate on the chart
     */
    fun coordinateToPrice(coordinate: Float, onPriceReceived: (Float?) -> Unit)

    /**
     * Applies new options to the existing series
     * @param options any subset of options
     */
    fun applyOptions(options: SeriesOptionsCommon)

    /**
     * Returns currently applied options
     * @param onOptionsReceived full set of currently applied options, including defaults
     */
    fun options(onOptionsReceived: (SeriesOptionsCommon) -> Unit)

    /**
     * Returns interface of the price scale the series is currently attached
     */
    fun priceScale(): PriceScaleApi

    /**
     * Sets or replaces series data
     * @param data ordered (earlier time point goes first) array of data items.
     * Old data is fully replaced with the new one.
     */
    fun setData(data: List<SeriesData>)

    /**
     * Adds or replaces a new bar
     * @param bar a single data item to be added.
     * Time of the new item must be greater or equal to the latest existing time point.
     * If the new item's time is equal to the last existing item's time,
     * then the existing item is replaced with the new one.
     */
    fun update(bar: SeriesData)

    /**
     * Returns a bar data by provided logical index.
     */
    fun <T : SeriesData> dataByIndex(
        clazz: Class<T>,
        logicalIndex: Int,
        direction: MismatchDirection = MismatchDirection.None,
        dataReceived: (T) -> Unit,
    )

    /**
     * Sets markers for the series
     * @param data array of series markers.
     * This array should be sorted by time.
     * Several markers with same time are allowed.
     */
    fun setMarkers(data: List<SeriesMarker>)

    /**
     * Returns an list of series markers.
     */
    fun markers(markersReceived: (List<SeriesMarker>) -> Unit)

    /**
     * Creates a new price line
     * @param options any subset of options
     */
    fun createPriceLine(options: PriceLineOptions): PriceLine

    /**
     * Removes an existing price line
     * @param line line to remove
     */
    fun removePriceLine(line: PriceLine)

    /**
     * Returns the type of this series
     * @param onSeriesTypeReceived this *SeriesType*
     */
    fun seriesType(onSeriesTypeReceived: (SeriesType) -> Unit)

    /**
     * Moves the series to the specified pane.
     */
    fun moveToPane(paneIndex: Int)

    /**
     * Returns the pane the series is currently attached to.
     */
    fun getPane(onPaneReceived: (PaneApi) -> Unit)
}

inline fun <reified T : SeriesData> SeriesApi.dataByIndex(
    logicalIndex: Int,
    direction: MismatchDirection = MismatchDirection.None,
    noinline dataReceived: (T) -> Unit,
) = dataByIndex(T::class.java, logicalIndex, direction, dataReceived)
