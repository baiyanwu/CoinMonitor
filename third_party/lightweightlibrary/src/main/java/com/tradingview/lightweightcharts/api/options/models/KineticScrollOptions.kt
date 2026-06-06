package com.tradingview.lightweightcharts.api.options.models

/**
 * You can disable or enable kinetic scroll via mouse or via touch gestures separately
 */
data class KineticScrollOptions(
    /**
     * If true, kinetic scroll is enabled via touch gestures
     */
    var touch: Boolean? = null,

    /**
     * If true, kinetic scroll is enabled via mouse
     */
    var mouse: Boolean? = null,
)

inline fun kineticScrollOptions(init: KineticScrollOptions.() -> Unit): KineticScrollOptions {
    return KineticScrollOptions().apply(init)
}
