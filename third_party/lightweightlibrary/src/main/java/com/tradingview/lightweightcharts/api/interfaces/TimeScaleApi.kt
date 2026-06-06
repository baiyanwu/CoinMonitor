package com.tradingview.lightweightcharts.api.interfaces

import android.util.SizeF
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions
import com.tradingview.lightweightcharts.api.series.models.LogicalRange
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.api.series.models.TimeRange

interface TimeScaleApi {

    object Func {
        const val SCROLL_POSITION = "scrollPosition"
        const val SCROLL_TO_POSITION = "scrollToPosition"
        const val OPTIONS = "timeScaleOptions"
        const val APPLY_OPTIONS = "timeScaleApplyOptions"
        const val SCROLL_TO_REAL_TIME = "scrollToRealTime"
        const val GET_VISIBLE_RANGE = "getVisibleRange"
        const val SET_VISIBLE_RANGE = "setVisibleRange"
        const val GET_VISIBLE_LOGICAL_RANGE = "getVisibleLogicalRange"
        const val SET_VISIBLE_LOGICAL_RANGE = "setVisibleLogicalRange"
        const val RESET_TIME_SCALE = "resetTimeScale"
        const val FIT_CONTENT = "fitContent"
        const val SUBSCRIBE_VISIBLE_TIME_RANGE_CHANGE = "subscribeVisibleTimeRangeChange"
        const val SUBSCRIBE_VISIBLE_LOGICAL_RANGE_CHANGE = "subscribeVisibleLogicalRangeChange"
        const val TIME_TO_COORDINATE = "timeToCoordinate"
        const val COORDINATE_TO_TIME = "coordinateToTime"
        const val LOGICAL_TO_COORDINATE = "logicalToCoordinate"
        const val COORDINATE_TO_LOGICAL = "coordinateToLogical"
        const val WIDTH = "timeScaleWidth"
        const val HEIGHT = "timeScaleHeight"
        const val SUBSCRIBE_SIZE_CHANGE = "subscribeTimeScaleSizeChange"
    }

    object Params {
        const val OPTIONS_PARAM = "options"
        const val POSITION = "position"
        const val ANIMATED = "animated"
        const val RANGE = "range"
    }

    /**
     * Returns current scroll position of the chart
     * @param onScrollPositionReceived a distance from the right edge to the latest bar, measured in bars
     */
    fun scrollPosition(onScrollPositionReceived: (Float) -> Unit)

    /**
     * Scrolls the chart to the specified position
     * @param position target data position
     * @param animated setting this to true makes the chart scrolling smooth and adds animation
     */
    fun scrollToPosition(position: Float, animated: Boolean = false)

    /**
     * Restores default scroll position of the chart. This process is always animated.
     */
    fun scrollToRealTime()

    /**
     * Returns current visible time range of the chart
     * @param onTimeRangeReceived visible range or null if the chart has no data at all
     */
    fun getVisibleRange(onTimeRangeReceived: (TimeRange?) -> Unit)

    /**
     * Sets visible range of data
     * @param range target visible range of data
     */
    fun setVisibleRange(range: TimeRange)

    /**
     * Returns current visible logical range of the chart.
     */
    fun getVisibleLogicalRange(onLogicalRangeReceived: (LogicalRange?) -> Unit)

    /**
     * Sets visible logical range of the chart.
     */
    fun setVisibleLogicalRange(range: LogicalRange)

    /**
     * Restores default zooming and scroll position of the time scale
     */
    fun resetTimeScale()

    /**
     * Automatically calculates the visible range to fit all data from all series
     * This is a momentary operation.
     */
    fun fitContent()

    /**
     * Converts a time to local x coordinate.
     * @param time time needs to be converted
     * @param onCoordinateReceived returns x coordinate of that time
     *                            or null if no time found on time scale
     */
    fun timeToCoordinate(time: Time, onCoordinateReceived: (x: Float?) -> Unit)

    /**
     * Converts a coordinate to time.
     * @param x coordinate needs to be converted
     * @param onTimeReceived returns the time of a bar that placed on that coordinate
     *                      or null if no bar found at this coordinate
     */
    fun coordinateToTime(x: Float, onTimeReceived: (time: Time?) -> Unit)

    /**
     * Converts a logical index to local x coordinate.
     * @param logical logical index needs to be converted
     * @param onCoordinateReceived returns x coordinate of that time
     *                            or null if the chart doesn't have data
     */
    fun logicalToCoordinate(logical: Int, onCoordinateReceived: (x: Float?) -> Unit)

    /**
     * Converts a coordinate to logical index.
     * @param x coordinate needs to be converted
     * @param onLogicalReceived returns logical index that is located on that coordinate
     *                         or null if the chart doesn't have data
     */
    fun coordinateToLogical(x: Float, onLogicalReceived: (logical: Int?) -> Unit)

    /**
     * Applies new options to the time scale.
     * @param options any subset of options
     */
    fun applyOptions(options: TimeScaleOptions)

    /**
     * Applies new options to the time scale.
     * @param block any subset of options
     */
    fun applyOptions(block: TimeScaleOptions.() -> Unit) = applyOptions(TimeScaleOptions().apply(block))

    /**
     * Returns current options
     * @param onOptionsReceived currently applied options
     */
    fun options(onOptionsReceived: (TimeScaleOptions) -> Unit)

    /**
     * Adds a subscription to visible range changes
     * to receive notification about visible range of data changes
     */
    fun subscribeVisibleTimeRangeChange(onTimeRangeChanged: (params: TimeRange?) -> Unit)

    /**
     * Removes a subscription to visible range changes
     */
    fun unsubscribeVisibleTimeRangeChange(onTimeRangeChanged: (params: TimeRange?) -> Unit)

    /**
     * Adds a subscription to visible logical range changes.
     */
    fun subscribeVisibleLogicalRangeChange(onLogicalRangeChanged: (params: LogicalRange?) -> Unit)

    /**
     * Removes a subscription to visible logical range changes.
     */
    fun unsubscribeVisibleLogicalRangeChange(onLogicalRangeChanged: (params: LogicalRange?) -> Unit)

    /**
     * Returns a current width of the time scale
     */
    fun width(onWidthReceived: (Float) -> Unit)

    /**
     * Returns a current height of the time scale
     */
    fun height(onHeightReceived: (Float) -> Unit)

    /**
     * Allows to subscribe to the size change event
     */
    fun subscribeSizeChange(onSizeChange: (size: SizeF) -> Unit)

    /**
     * Removes a subscription to size changes
     */
    fun unsubscribeSizeChange(onSizeChange: (size: SizeF) -> Unit)
}
