package com.tradingview.lightweightcharts.api.series.common

import com.tradingview.lightweightcharts.api.options.models.PriceLineOptions
import com.tradingview.lightweightcharts.api.serializer.PriceLineOptionsDeserializer
import com.tradingview.lightweightcharts.api.series.common.PriceLine.Func.APPLY_OPTIONS
import com.tradingview.lightweightcharts.api.series.common.PriceLine.Func.OPTIONS
import com.tradingview.lightweightcharts.api.series.common.PriceLine.Params.LINE_ID
import com.tradingview.lightweightcharts.api.series.common.PriceLine.Params.OPTIONS_PARAM
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController

class PriceLineDelegate(
    override val uuid: String,
    private val controller: WebMessageController
) : PriceLine {

    override fun applyOptions(options: PriceLineOptions) {
        controller.callFunction(
            APPLY_OPTIONS,
            mapOf(
                LINE_ID to uuid,
                OPTIONS_PARAM to options
            )
        )
    }

    override fun options(block: (PriceLineOptions?) -> Unit) {
        controller.callFunction(
            OPTIONS,
            mapOf(LINE_ID to uuid),
            callback = block,
            deserializer = PriceLineOptionsDeserializer()
        )
    }
}
