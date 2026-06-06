package com.tradingview.lightweightcharts.runtime.controller

import com.google.gson.JsonElement
import com.tradingview.lightweightcharts.Logger
import com.tradingview.lightweightcharts.api.serializer.Deserializer
import com.tradingview.lightweightcharts.api.serializer.PrimitiveSerializer
import com.tradingview.lightweightcharts.runtime.WebMessageChannel
import com.tradingview.lightweightcharts.runtime.messaging.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

open class WebMessageController : WebMessageChannel.BridgeMessageListener {

    private var webMessageChannel: WebMessageChannel? = null
    private val callbackBuffer = ConcurrentHashMap<String, BufferElement>()
    private val messageBuffer = ConcurrentLinkedDeque<BridgeMessage>()

    fun callFunction(
        name: String,
        params: Map<String, Any> = emptyMap()
    ): String {
        return callBridgeFunction(name, params)
    }

    fun callFunction(
        name: String,
        params: Map<String, Any> = emptyMap(),
        callback: (() -> Unit)?
    ): String {
        @Suppress("UNCHECKED_CAST")
        return callBridgeFunction(name, params, callback as? (Any?) -> Unit)
    }

    fun <T : Any?> callFunction(
        name: String,
        params: Map<String, Any> = emptyMap(),
        callback: ((T) -> Unit)?,
        deserializer: Deserializer<out T>
    ): String {
        @Suppress("UNCHECKED_CAST")
        return callBridgeFunction(name, params, callback as? (Any?) -> Unit, deserializer)
    }

    private fun callBridgeFunction(
        name: String,
        params: Map<String, Any> = emptyMap(),
        callback: ((Any?) -> Unit)? = null,
        deserializer: Deserializer<out Any?> = PrimitiveSerializer.NullDeserializer
    ): String {
        val bridge = BridgeFunction(name, params)

        @Suppress("UNCHECKED_CAST")
        callbackBuffer[bridge.uuid] = BufferElement(
            callback,
            deserializer,
            getStackTrace()
        )

        messageBuffer.addLast(bridge)
        sendMessages()
        return bridge.uuid
    }

    fun <T : Any> callSubscribe(
        name: String,
        params: Map<String, Any> = emptyMap(),
        callback: (T) -> Unit,
        deserializer: Deserializer<out T>
    ) {
        val bridge = BridgeSubscription(name, params)
        @Suppress("UNCHECKED_CAST")
        callbackBuffer[bridge.uuid] = BufferElement(
            callback as (Any?) -> Unit,
            deserializer,
            getStackTrace()
        )
        messageBuffer.addLast(bridge)
        sendMessages()
    }

    fun <T : Any> callUnsubscribe(
        name: String,
        subscription: (T) -> Unit
    ) {
        val uuid = callbackBuffer.filterValues { it.callback == subscription }.keys.firstOrNull()
        if (uuid != null) {
            callbackBuffer[uuid] = callbackBuffer[uuid]!!.makeInactive()
            val message = BridgeUnsubscribe(name, uuid)
            messageBuffer.addLast(message)
            sendMessages()
        } else {
            Logger.e("Subscribe cancellation is failed. Key $uuid is not found")
        }
    }

    override fun onMessage(bridgeMessage: BridgeMessage) {
        Logger.d("Received message from web: $bridgeMessage")
        when (bridgeMessage) {
            is BridgeFunctionResult -> {
                val element = callbackBuffer.remove(bridgeMessage.uuid)
                    ?: throw IllegalStateException(
                        "Could not apply the function result, bridgeMessage: $bridgeMessage"
                    )

                element.invoke(bridgeMessage.result)
            }

            is BridgeSubscribeResult -> {
                val element = callbackBuffer[bridgeMessage.uuid]
                if (element != null && !element.isInactive) {
                    element.invoke(bridgeMessage.result)
                } else {
                    Logger.w("Inactive subscription triggered the action")
                }
            }

            is BridgeUnsubscribeResult -> {
                callbackBuffer.remove(bridgeMessage.uuid)
            }

            is BridgeFatalError -> {
                val element = callbackBuffer.remove(bridgeMessage.uuid)

                val message = bridgeMessage.message.split('\n').first()
                val jsException = IllegalStateException(message).apply {
                    val regex = getStackTraceRegex()
                    val trace = regex.findAll(bridgeMessage.message).map { result ->
                        val values = result.groupValues
                        StackTraceElement(
                            "jsCode",
                            values[1],
                            values[2],
                            values[3].toInt()
                        )
                    }.toList()
                    stackTrace = trace.toTypedArray()

                    val exception = IllegalStateException()
                    exception.stackTrace = element?.stackTrace ?: emptyArray()
                    initCause(exception)
                }

                throw jsException
            }
        }
    }

    private fun getStackTraceRegex(): Regex {
        val methodGroup = "(\\S+)"
        val fileGroup = "(file:[^:]+)"
        val lineGroup = "(\\d+)"
        val columnGroup = "(\\d+)"
        val pattern = "at\\s+$methodGroup\\s+[(]$fileGroup:$lineGroup:$columnGroup[)]"
        return Regex(pattern)
    }

    private fun getStackTrace(): Array<StackTraceElement> {
        return Thread.currentThread().stackTrace
            // remove current class name from the stacktrace
            .filter { it.className != WebMessageController::class.qualifiedName }
            // remove getCurrentThread and getStackTrace from the stacktrace
            .drop(2)
            .toTypedArray()
    }

    private fun sendMessages() {
        webMessageChannel?.apply {
            while (messageBuffer.isNotEmpty()) {
                messageBuffer.pollFirst()?.let(::sendMessage)
            }
        }
    }

    fun setWebMessageChannel(webMessageChannel: WebMessageChannel) {
        this.webMessageChannel = webMessageChannel
        webMessageChannel.setOnBridgeMessageListener(this)
        sendMessages()
    }

    data class BufferElement(
        val callback: ((Any?) -> Unit)? = null,
        val deserializer: Deserializer<out Any?>,
        val stackTrace: Array<StackTraceElement>,
        val isInactive: Boolean = false
    ) {
        fun invoke(jsonElement: JsonElement) {
            callback?.invoke(deserializer.deserialize(jsonElement))
        }

        fun makeInactive(): BufferElement = copy(isInactive = true)
    }
}
