package com.tradingview.lightweightcharts.api.options.models

data class HandleScrollOptions(
    var mouseWheel: Boolean? = null,
    var pressedMouseMove: Boolean? = null,
    var horzTouchDrag: Boolean? = null,
    var vertTouchDrag: Boolean? = null
)

inline fun handleScrollOptions(init: HandleScrollOptions.() -> Unit): HandleScrollOptions {
    return HandleScrollOptions().apply(init)
}
