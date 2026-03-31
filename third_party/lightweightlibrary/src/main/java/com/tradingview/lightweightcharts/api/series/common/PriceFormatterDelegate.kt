package com.tradingview.lightweightcharts.api.series.common

import com.tradingview.lightweightcharts.api.serializer.PrimitiveSerializer
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController

class PriceFormatterDelegate(
    override val uuid: String,
    private val controller: WebMessageController
) : PriceFormatter {

    override fun format(price: Float, result: (String?) -> Unit) {
        controller.callFunction(
            PriceFormatter.Func.PRICE_FORMATTER,
            mapOf(
                PriceFormatter.Params.FORMATTER_ID to uuid,
                PriceFormatter.Params.PRICE to price
            ),
            callback = result,
            PrimitiveSerializer.StringDeserializer
        )
    }
}
