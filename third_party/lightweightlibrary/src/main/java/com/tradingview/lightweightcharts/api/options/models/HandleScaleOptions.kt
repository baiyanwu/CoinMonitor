package com.tradingview.lightweightcharts.api.options.models

data class HandleScaleOptions(
    /**
     * Enable scaling with the mouse wheel.
     */
    var mouseWheel: Boolean? = null,

    /**
     * Enable scaling with pinch/zoom gestures.
     */
    var pinch: Boolean? = null,

    /**
     * Enable scaling the price and/or time scales by holding down the left mouse button and moving the mouse.
     */
    var axisPressedMouseMove: AxisPressedMouseMoveOptions? = null,

    /**
     * Enable resetting scaling by double-clicking
     */
    var axisDoubleClickReset: AxisDoubleClickOptions? = null,
)

data class AxisPressedMouseMoveOptions(
    /**
     * Enable scaling the time axis by holding down the left mouse button and moving the mouse.
     */
    var time: Boolean? = null,

    /**
     * Enable scaling the price axis by holding down the left mouse button and moving the mouse.
     */
    var price: Boolean? = null,
)

data class AxisDoubleClickOptions(
    /**
     * Enable resetting scaling the time axis by double-clicking.
     */
    var time: Boolean? = null,

    /**
     * Enable reseting scaling the price axis by by double-clicking
     */
    var price: Boolean? = null,
)

inline fun handleScaleOptions(init: HandleScaleOptions.() -> Unit): HandleScaleOptions {
    return HandleScaleOptions().apply(init)
}

inline fun axisPressedMouseMoveOptions(
    init: AxisPressedMouseMoveOptions.() -> Unit,
): AxisPressedMouseMoveOptions {
    return AxisPressedMouseMoveOptions().apply(init)
}

inline fun axisDoubleClickOptions(
    init: AxisDoubleClickOptions.() -> Unit,
): AxisDoubleClickOptions {
    return AxisDoubleClickOptions().apply(init)
}
