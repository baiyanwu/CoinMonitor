package com.tradingview.lightweightcharts.runtime.messaging

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

open class BridgeMessage(
    val messageType: MessageType,
    val data: Data
) {
    override fun toString(): String {
        return "${this::class.java.simpleName}(messageType=$messageType, data=$data)"
    }
}

open class Data(
    open val uuid: String,
    open val fn: String? = null,
    open val params: Map<String, Any>? = null,
    open val result: JsonElement? = null,
    open val message: String? = null,
    open val logLevel: LogLevel? = null
)

enum class LogLevel {
    @SerializedName("debug")
    DEBUG,

    @SerializedName("warning")
    WARNING,

    @SerializedName("error")
    ERROR,

    @SerializedName("none")
    NONE;

    fun isDebug(): Boolean = ordinal == 0
    fun isWarning(): Boolean = ordinal <= 1
    fun isError(): Boolean = ordinal <= 2
    fun isEnabled(): Boolean = ordinal <= 3
}
