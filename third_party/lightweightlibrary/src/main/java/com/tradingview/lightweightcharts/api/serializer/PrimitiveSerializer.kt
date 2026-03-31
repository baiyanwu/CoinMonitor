package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement

sealed class PrimitiveSerializer {
    object StringDeserializer : Deserializer<String>() {
        override fun deserialize(json: JsonElement): String? {
            if (json.isJsonNull) {
                return null
            }
            return json.asString
        }
    }

    object FloatDeserializer : Deserializer<Float>() {
        override fun deserialize(json: JsonElement): Float? {
            if (json.isJsonNull) {
                return null
            }
            return json.asFloat
        }
    }

    object IntDeserializer : Deserializer<Int>() {
        override fun deserialize(json: JsonElement): Int? {
            if (json.isJsonNull) {
                return null
            }
            return json.asInt
        }
    }

    object DoubleDeserializer : Deserializer<Double>() {
        override fun deserialize(json: JsonElement): Double? {
            if (json.isJsonNull) {
                return null
            }
            return json.asDouble
        }
    }

    object NullDeserializer : Deserializer<Any>() {
        override fun deserialize(json: JsonElement): Any? {
            return null
        }
    }
}
