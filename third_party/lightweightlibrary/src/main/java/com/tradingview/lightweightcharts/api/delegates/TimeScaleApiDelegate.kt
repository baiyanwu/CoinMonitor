package com.tradingview.lightweightcharts.api.delegates

import android.util.SizeF
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.APPLY_OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.COORDINATE_TO_LOGICAL
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.COORDINATE_TO_TIME
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.FIT_CONTENT
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.GET_VISIBLE_LOGICAL_RANGE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.GET_VISIBLE_RANGE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.HEIGHT
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.LOGICAL_TO_COORDINATE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.RESET_TIME_SCALE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SCROLL_POSITION
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SCROLL_TO_POSITION
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SCROLL_TO_REAL_TIME
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SET_VISIBLE_RANGE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SUBSCRIBE_SIZE_CHANGE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SUBSCRIBE_VISIBLE_LOGICAL_RANGE_CHANGE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SUBSCRIBE_VISIBLE_TIME_RANGE_CHANGE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.TIME_TO_COORDINATE
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.WIDTH
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Func.SET_VISIBLE_LOGICAL_RANGE
import com.tradingview.lightweightcharts.api.serializer.LogicalRangeDeserializer
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Params.ANIMATED
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Params.OPTIONS_PARAM
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Params.POSITION
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi.Params.RANGE
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions
import com.tradingview.lightweightcharts.api.serializer.*
import com.tradingview.lightweightcharts.api.series.models.LogicalRange
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.api.series.models.TimeRange
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController
import com.tradingview.lightweightcharts.runtime.version.ChartRuntimeObject

class TimeScaleApiDelegate(
    private val controller: WebMessageController
) : TimeScaleApi, ChartRuntimeObject {

    override fun getVersion(): Int {
        return controller.hashCode()
    }

    override fun scrollPosition(onScrollPositionReceived: (Float) -> Unit) {
        controller.callFunction(
            SCROLL_POSITION,
            callback = onScrollPositionReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun scrollToPosition(position: Float, animated: Boolean) {
        controller.callFunction(
            SCROLL_TO_POSITION,
            mapOf(
                POSITION to position,
                ANIMATED to animated
            )
        )
    }

    override fun scrollToRealTime() {
        controller.callFunction(
            SCROLL_TO_REAL_TIME
        )
    }

    override fun getVisibleRange(onTimeRangeReceived: (TimeRange?) -> Unit) {
        controller.callFunction(
            GET_VISIBLE_RANGE,
            callback = onTimeRangeReceived,
            deserializer = TimeRangeDeserializer()
        )
    }

    override fun setVisibleRange(range: TimeRange) {
        controller.callFunction(
            SET_VISIBLE_RANGE,
            mapOf(
                RANGE to range
            )
        )
    }

    override fun getVisibleLogicalRange(onLogicalRangeReceived: (LogicalRange?) -> Unit) {
        controller.callFunction(
            GET_VISIBLE_LOGICAL_RANGE,
            callback = onLogicalRangeReceived,
            deserializer = LogicalRangeDeserializer()
        )
    }

    override fun setVisibleLogicalRange(range: LogicalRange) {
        controller.callFunction(
            SET_VISIBLE_LOGICAL_RANGE,
            mapOf(
                RANGE to range
            )
        )
    }

    override fun resetTimeScale() {
        controller.callFunction(
            RESET_TIME_SCALE
        )
    }

    override fun fitContent() {
        controller.callFunction(
            FIT_CONTENT
        )
    }

    override fun timeToCoordinate(time: Time, onCoordinateReceived: (x: Float?) -> Unit) {
        controller.callFunction(
            TIME_TO_COORDINATE,
            mapOf(
                "time" to time
            ),
            callback = onCoordinateReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun coordinateToTime(x: Float, onTimeReceived: (time: Time?) -> Unit) {
        controller.callFunction(
            COORDINATE_TO_TIME,
            mapOf(
                "x" to x
            ),
            callback = onTimeReceived,
            deserializer = TimeDeserializer()
        )
    }

    override fun logicalToCoordinate(logical: Int, onCoordinateReceived: (x: Float?) -> Unit) {
        controller.callFunction(
            LOGICAL_TO_COORDINATE,
            mapOf(
                "logical" to logical
            ),
            callback = onCoordinateReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun coordinateToLogical(x: Float, onLogicalReceived: (logical: Int?) -> Unit) {
        controller.callFunction<Int?>(
            COORDINATE_TO_LOGICAL,
            mapOf(
                "x" to x
            ),
            callback = onLogicalReceived,
            deserializer = PrimitiveSerializer.IntDeserializer
        )
    }

    override fun applyOptions(options: TimeScaleOptions) {
        controller.callFunction(
            APPLY_OPTIONS,
            mapOf(
                OPTIONS_PARAM to options
            )
        )
    }

    override fun options(onOptionsReceived: (TimeScaleOptions) -> Unit) {
        controller.callFunction(
            OPTIONS,
            callback = onOptionsReceived,
            deserializer = TimeScaleOptionsDeserializer()
        )
    }

    override fun subscribeVisibleTimeRangeChange(onTimeRangeChanged: (params: TimeRange?) -> Unit) {
        controller.callSubscribe(
            SUBSCRIBE_VISIBLE_TIME_RANGE_CHANGE,
            callback = onTimeRangeChanged,
            deserializer = TimeRangeDeserializer()
        )
    }

    override fun unsubscribeVisibleTimeRangeChange(onTimeRangeChanged: (params: TimeRange?) -> Unit) {
        controller.callUnsubscribe(
            SUBSCRIBE_VISIBLE_TIME_RANGE_CHANGE,
            subscription = onTimeRangeChanged
        )
    }

    override fun subscribeVisibleLogicalRangeChange(onLogicalRangeChanged: (params: LogicalRange?) -> Unit) {
        controller.callSubscribe(
            SUBSCRIBE_VISIBLE_LOGICAL_RANGE_CHANGE,
            callback = onLogicalRangeChanged,
            deserializer = LogicalRangeDeserializer()
        )
    }

    override fun unsubscribeVisibleLogicalRangeChange(onLogicalRangeChanged: (params: LogicalRange?) -> Unit) {
        controller.callUnsubscribe(
            SUBSCRIBE_VISIBLE_LOGICAL_RANGE_CHANGE,
            subscription = onLogicalRangeChanged
        )
    }

    override fun width(onWidthReceived: (Float) -> Unit) {
        controller.callFunction(
            WIDTH,
            callback = onWidthReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun height(onHeightReceived: (Float) -> Unit) {
        controller.callFunction(
            HEIGHT,
            callback = onHeightReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun subscribeSizeChange(onSizeChange: (size: SizeF) -> Unit) {
        controller.callSubscribe(
            SUBSCRIBE_SIZE_CHANGE,
            callback = onSizeChange,
            deserializer = SizeDeserializer()
        )
    }

    override fun unsubscribeSizeChange(onSizeChange: (size: SizeF) -> Unit) {
        controller.callUnsubscribe(
            SUBSCRIBE_SIZE_CHANGE,
            subscription = onSizeChange
        )
    }
}
