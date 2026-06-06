package com.tradingview.lightweightcharts.runtime.messaging

import java.util.*

class ConnectionMessage(logLevel: LogLevel) : BridgeMessage(
    MessageType.CONNECTION,
    Data(
        uuid = UUID.randomUUID().toString(),
        logLevel = logLevel
    )
)
