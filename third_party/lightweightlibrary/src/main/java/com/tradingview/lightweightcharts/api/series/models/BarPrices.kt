package com.tradingview.lightweightcharts.api.series.models

import com.google.gson.*
import java.lang.reflect.Type

data class BarPrices(
    val id: String,
    val prices: BarPrice
) {
    class BarPricesAdapter : JsonDeserializer<BarPrices> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): BarPrices {
            json?.asJsonObject?.apply {
                return BarPrices(get("id")?.asString ?: "", generate(this))
            }
            throw JsonSyntaxException("BarPrice syntax exception")
        }

        private fun generate(json: JsonObject): BarPrice {
            val prices = json.get("prices")
            return if (prices.isJsonObject) {
                Gson().fromJson(prices, BarPrice::class.java)
            } else {
                BarPrice(value = prices.asFloat)
            }
        }
    }
}

data class BarPrice(
    val open: Float? = null,
    val high: Float? = null,
    val low: Float? = null,
    val close: Float? = null,
    val value: Float? = null
)
