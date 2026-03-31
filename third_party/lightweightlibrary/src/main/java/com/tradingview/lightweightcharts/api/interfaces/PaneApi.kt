package com.tradingview.lightweightcharts.api.interfaces

interface PaneApi {

    object Func {
        const val SET_STRETCH_FACTOR = "paneSetStretchFactor"
        const val GET_STRETCH_FACTOR = "paneGetStretchFactor"
        const val PANE_INDEX = "paneIndex"
    }

    object Params {
        const val PANE_UUID = "paneId"
        const val STRETCH_FACTOR = "stretchFactor"
    }

    val uuid: String

    fun setStretchFactor(stretchFactor: Float)

    fun getStretchFactor(onStretchFactorReceived: (Float) -> Unit)

    fun paneIndex(onPaneIndexReceived: (Int) -> Unit)
}
