package com.tradingview.lightweightcharts.api.delegates

import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi.Func.APPLY_OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi.Func.OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi.Func.WIDTH
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi.Params.CALLER
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi.Params.OPTIONS_PARAM
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions
import com.tradingview.lightweightcharts.api.serializer.PriceScaleOptionsDeserializer
import com.tradingview.lightweightcharts.api.serializer.PrimitiveSerializer
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController
import com.tradingview.lightweightcharts.runtime.version.ChartRuntimeObject

class PriceScaleApiDelegate(
    override val uuid: String,
    private val controller: WebMessageController
) : PriceScaleApi, ChartRuntimeObject {

    override fun getVersion(): Int {
        return controller.hashCode()
    }

    override fun applyOptions(options: PriceScaleOptions) {
        controller.callFunction(
            APPLY_OPTIONS,
            mapOf(
                CALLER to uuid,
                OPTIONS_PARAM to options
            )
        )
    }

    override fun options(onOptionsReceived: (PriceScaleOptions) -> Unit) {
        controller.callFunction(
            OPTIONS,
            mapOf(CALLER to uuid),
            callback = onOptionsReceived,
            deserializer = PriceScaleOptionsDeserializer()
        )
    }

    override fun width(onWidthReceived: (Float) -> Unit) {
        controller.callFunction(
            WIDTH,
            mapOf(CALLER to uuid),
            callback = onWidthReceived,
            deserializer = PrimitiveSerializer.FloatDeserializer
        )
    }
}
