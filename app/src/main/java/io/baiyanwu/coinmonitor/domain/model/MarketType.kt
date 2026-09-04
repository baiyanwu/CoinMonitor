package io.baiyanwu.coinmonitor.domain.model

enum class MarketType {
    CEX_SPOT,
    CEX_USDT_FUTURES,
    ONCHAIN_TOKEN
}

enum class ChainFamily {
    EVM,
    SOL,
    OTHER
}
