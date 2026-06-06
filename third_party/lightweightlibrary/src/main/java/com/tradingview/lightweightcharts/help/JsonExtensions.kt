package com.tradingview.lightweightcharts.help

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

fun JsonElement?.isNumber(): Boolean {
    return this is JsonPrimitive && this.isNumber
}

fun JsonElement?.isString(): Boolean {
    return this is JsonPrimitive && this.isString
}

fun JsonElement?.requireString(): String {
    return this!!.asString
}

fun JsonElement?.requireInt(): Int {
    return this!!.asInt
}
