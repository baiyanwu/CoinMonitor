package io.baiyanwu.coinmonitor.domain.model

import java.util.UUID

/**
 * K 线来源枚举。
 */
enum class KlineSource(val title: String) {
    BINANCE("Binance"),
    BINANCE_ALPHA("Alpha"),
    OKX("OKX"),
    ONCHAIN("Onchain");

    companion object {
        fun fromWatchItem(item: WatchItem): KlineSource {
            return if (item.marketType == MarketType.ONCHAIN_TOKEN) {
                ONCHAIN
            } else {
                when (item.exchangeSource) {
                    ExchangeSource.BINANCE -> BINANCE
                    ExchangeSource.BINANCE_ALPHA -> BINANCE_ALPHA
                    ExchangeSource.OKX -> OKX
                }
            }
        }
    }
}

/**
 * K 线周期枚举。
 *
 * `binanceValue` 和 `okxValue` 分别对应不同上游接口的周期参数，
 * UI 层直接遍历这个枚举即可得到完整的周期列表。
 */
enum class KlineInterval(val label: String, val binanceValue: String, val okxValue: String) {
    ONE_MINUTE("1m", "1m", "1m"),
    FIVE_MINUTES("5m", "5m", "5m"),
    FIFTEEN_MINUTES("15m", "15m", "15m"),
    ONE_HOUR("1H", "1h", "1H"),
    FOUR_HOURS("4H", "4h", "4H"),
    ONE_DAY("1D", "1d", "1Dutc"),
    THREE_DAYS("3D", "3d", "3Dutc"),
    ONE_WEEK("1W", "1w", "1Wutc"),
    ONE_MONTH("1M", "1M", "1Mutc")
}

/**
 * K 线指标枚举。
 */
enum class KlineIndicator(val label: String) {
    MA("MA"),
    EMA("EMA"),
    BOLL("BOLL"),
    MACD("MACD"),
    KDJ("KDJ"),
    RSI("RSI"),
    VOL("VOL")
}

data class CandleEntry(
    val openTimeMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isConfirmed: Boolean = true
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "",
    val role: AiChatRole,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val itemId: String? = null,
    val symbol: String? = null,
    val sourceTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AiChatSessionSummary(
    val session: AiChatSession,
    val latestMessagePreview: String?
)

enum class AiChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}
