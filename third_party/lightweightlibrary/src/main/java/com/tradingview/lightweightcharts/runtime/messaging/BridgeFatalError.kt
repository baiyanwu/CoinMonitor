package com.tradingview.lightweightcharts.runtime.messaging

class BridgeFatalError(
    bridgeMessage: BridgeMessage
) : BridgeMessage(bridgeMessage.messageType, bridgeMessage.data) {
    val uuid: String get() = data.uuid
    val message: String get() = data.message!!
}
