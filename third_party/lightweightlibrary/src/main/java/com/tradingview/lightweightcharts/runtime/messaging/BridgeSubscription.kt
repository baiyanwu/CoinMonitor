package com.tradingview.lightweightcharts.runtime.messaging

import java.util.*

class BridgeSubscription(
    functionName: String,
    functionParams: Map<String, Any> = emptyMap()
) : BridgeMessage(
    MessageType.SUBSCRIBE,
    Data(
        uuid = UUID.randomUUID().toString(),
        fn = functionName,
        params = functionParams
    )
) {
    val uuid: String get() = data.uuid
}
