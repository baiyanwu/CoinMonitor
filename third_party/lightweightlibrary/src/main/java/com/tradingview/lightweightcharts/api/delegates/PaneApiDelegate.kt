package com.tradingview.lightweightcharts.api.delegates

import com.tradingview.lightweightcharts.api.interfaces.PaneApi
import com.tradingview.lightweightcharts.api.interfaces.PaneApi.Func.GET_STRETCH_FACTOR
import com.tradingview.lightweightcharts.api.interfaces.PaneApi.Func.PANE_INDEX
import com.tradingview.lightweightcharts.api.interfaces.PaneApi.Func.SET_STRETCH_FACTOR
import com.tradingview.lightweightcharts.api.interfaces.PaneApi.Params.PANE_UUID
import com.tradingview.lightweightcharts.api.interfaces.PaneApi.Params.STRETCH_FACTOR
import com.tradingview.lightweightcharts.api.serializer.PrimitiveSerializer
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController
import com.tradingview.lightweightcharts.runtime.version.ChartRuntimeObject

class PaneApiDelegate(
    override val uuid: String,
    private val controller: WebMessageController
) : PaneApi, ChartRuntimeObject {

    override fun getVersion(): Int {
        return controller.hashCode()
    }

    override fun setStretchFactor(stretchFactor: Float) {
        controller.callFunction(
            SET_STRETCH_FACTOR,
            mapOf(
                PANE_UUID to uuid,
                STRETCH_FACTOR to stretchFactor
            )
        )
    }

    override fun getStretchFactor(onStretchFactorReceived: (Float) -> Unit) {
        controller.callFunction(
            GET_STRETCH_FACTOR,
            mapOf(PANE_UUID to uuid),
            callback = onStretchFactorReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun paneIndex(onPaneIndexReceived: (Int) -> Unit) {
        controller.callFunction(
            PANE_INDEX,
            mapOf(PANE_UUID to uuid),
            callback = onPaneIndexReceived,
            deserializer = PrimitiveSerializer.IntDeserializer
        )
    }
}
