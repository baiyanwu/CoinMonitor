package com.tradingview.lightweightcharts.runtime.messaging

class BridgeUnsubscribe(
    functionName: String,
    uuid: String
) : BridgeMessage(
    MessageType.UNSUBSCRIBE,
    Data(
        uuid = uuid,
        fn = functionName
    )
)
