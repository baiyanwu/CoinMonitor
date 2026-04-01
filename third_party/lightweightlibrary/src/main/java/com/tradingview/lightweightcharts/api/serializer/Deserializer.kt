package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.tradingview.lightweightcharts.api.serializer.gson.GsonProvider

abstract class Deserializer<T> {

    protected open val gson: Gson by lazy { GsonProvider.newInstance() }

    abstract fun deserialize(json: JsonElement): T?
}
