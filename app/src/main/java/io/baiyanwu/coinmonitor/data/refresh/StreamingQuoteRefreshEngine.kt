package io.baiyanwu.coinmonitor.data.refresh

import android.util.Log
import io.baiyanwu.coinmonitor.data.network.NetworkLogRedactor
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 行情刷新默认优先走交易所官方 WSS，把“每轮全量 HTTP 询价”改成“增量推送”。
 *
 * 当前覆盖 Binance Spot / Binance USD-M Futures / Binance Alpha / OKX Spot / OKX USD-M Futures。
 * 链上行情独立使用 DexScreener REST 轮询，不参与任何交易所 WSS 生命周期。
 */
class StreamingQuoteRefreshEngine(
    private val scope: CoroutineScope,
    private val watchlistRepository: WatchlistRepository,
    private val quoteRepository: QuoteRepository,
    private val marketQuoteRepository: MarketQuoteRepository,
    private val networkLogRepository: NetworkLogRepository
) : QuoteRefreshEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val refreshMutex = Mutex()
    private val pendingQuoteMutex = Mutex()

    private var currentConfig: QuoteRefreshConfig = QuoteRefreshConfig(
        enabled = false,
        items = emptyList(),
        refreshIntervalMillis = 15_000L
    )
    private var bootstrapJob: Job? = null
    private var binanceJob: Job? = null
    private var binanceFuturesJob: Job? = null
    private var alphaJob: Job? = null
    private var okxJob: Job? = null
    private var onchainPollingJob: Job? = null
    private var fallbackJob: Job? = null
    private var flushJob: Job? = null
    private var binanceSocket: WebSocket? = null
    private var binanceFuturesSocket: WebSocket? = null
    private var alphaSocket: WebSocket? = null
    private var okxSocket: WebSocket? = null
    private val pendingQuotes = linkedMapOf<String, MarketQuote>()
    private var currentSubscriptionFingerprint: String = ""

    override fun updateConfig(config: QuoteRefreshConfig) {
        val nextFingerprint = buildSubscriptionFingerprint(config)
        val shouldRestart = nextFingerprint != currentSubscriptionFingerprint ||
            config.enabled != currentConfig.enabled ||
            config.refreshIntervalMillis != currentConfig.refreshIntervalMillis ||
            config.onchainRefreshIntervalMillis != currentConfig.onchainRefreshIntervalMillis
        currentConfig = config
        if (!shouldRestart) return

        currentSubscriptionFingerprint = nextFingerprint
        restart(config)
    }

    override suspend fun refreshNow() {
        refreshQuotes(currentConfig.items)
    }

    override fun reconnect() {
        restart(currentConfig)
    }

    override fun stop() {
        bootstrapJob?.cancel()
        bootstrapJob = null
        binanceJob?.cancel()
        binanceJob = null
        binanceFuturesJob?.cancel()
        binanceFuturesJob = null
        alphaJob?.cancel()
        alphaJob = null
        okxJob?.cancel()
        okxJob = null
        onchainPollingJob?.cancel()
        onchainPollingJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        flushJob?.cancel()
        flushJob = null
        closeSockets()
    }

    private fun restart(config: QuoteRefreshConfig) {
        bootstrapJob?.cancel()
        bootstrapJob = null
        binanceJob?.cancel()
        binanceJob = null
        binanceFuturesJob?.cancel()
        binanceFuturesJob = null
        alphaJob?.cancel()
        alphaJob = null
        okxJob?.cancel()
        okxJob = null
        onchainPollingJob?.cancel()
        onchainPollingJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        closeSockets()

        if (!config.enabled || config.items.isEmpty()) return

        val binanceItems = config.items.filter(::isBinanceSpotItem)
        val binanceFuturesItems = config.items.filter(::isBinanceUsdtFuturesItem)
        val alphaItems = config.items.filter(::isAlphaSpotItem)
        val okxItems = config.items.filter(::isOkxPublicTickerItem)
        val onchainItems = config.items.filter(::isOnchainItem)
        val fallbackItems = config.items.filterNot { item ->
            isBinanceSpotItem(item) ||
                isBinanceUsdtFuturesItem(item) ||
                isAlphaSpotItem(item) ||
                isOkxPublicTickerItem(item) ||
                isOnchainItem(item)
        }

        // 首次启动或观察列表变化时先做一轮快照拉取，避免长连接尚未推第一帧时页面出现价格空档。
        bootstrapJob = scope.launch {
            refreshQuotes(config.items)
        }

        if (binanceItems.isNotEmpty()) {
            binanceJob = scope.launch {
                runBinanceSocketLoop(binanceItems)
            }
        }
        if (binanceFuturesItems.isNotEmpty()) {
            binanceFuturesJob = scope.launch {
                runBinanceFuturesSocketLoop(binanceFuturesItems)
            }
        }
        if (alphaItems.isNotEmpty()) {
            alphaJob = scope.launch {
                runAlphaSocketLoop(alphaItems)
            }
        }
        if (okxItems.isNotEmpty()) {
            okxJob = scope.launch {
                runOkxSocketLoop(okxItems)
            }
        }
        if (onchainItems.isNotEmpty()) {
            onchainPollingJob = scope.launch {
                runOnchainPollingLoop(onchainItems)
            }
        }
        if (fallbackItems.isNotEmpty()) {
            fallbackJob = scope.launch {
                runFallbackPollingLoop(fallbackItems)
            }
        }
    }

    private suspend fun runFallbackPollingLoop(items: List<WatchItem>) {
        refreshQuotes(items)
        while (scope.isActive) {
            delay(currentConfig.refreshIntervalMillis)
            refreshQuotes(currentConfig.items.filter { current ->
                items.any { fallback -> fallback.id == current.id }
            })
        }
    }

    private suspend fun runBinanceSocketLoop(items: List<WatchItem>) {
        val symbolMap = items.associateBy { it.id.substringAfter("binance:").uppercase() }

        while (scope.isActive && currentConfig.enabled) {
            val closed = CompletableDeferred<Unit>()
            val request = Request.Builder()
                .url(BINANCE_PUBLIC_WS_URL)
                .build()

            binanceSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        logWs("WS OPEN binance-spot $BINANCE_PUBLIC_WS_URL")
                        val subscribeRequest = buildJsonObject {
                            put("method", "SUBSCRIBE")
                            put("params", buildJsonArray {
                                symbolMap.keys.sorted().forEach { symbol ->
                                    add(JsonPrimitive("${symbol.lowercase()}@ticker"))
                                }
                            })
                            put("id", 1)
                        }
                        logWs(
                            line = "WS SEND binance-spot subscribe ${symbolMap.size}",
                            detail = subscribeRequest.toString()
                        )
                        webSocket.send(subscribeRequest.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        logWs("WS RECV binance-spot $text")
                        parseBinanceTickerMessage(text, symbolMap)?.let { quote ->
                            scope.launch {
                                enqueueQuote(quote)
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        logWs(
                            line = "WS FAIL binance-spot ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                            detail = t.stackTraceToString()
                        )
                        Log.w(TAG, "Binance spot WSS failed: ${t.message}")
                        closed.complete(Unit)
                    }
                }
            )

            closed.await()
            binanceSocket = null
            if (!scope.isActive || !currentConfig.enabled) break

            // WSS 临时抖动时，先用一次 REST 兜住价格，再进入下一轮重连，避免页面长时间完全静默。
            refreshQuotes(currentConfig.items.filter(::isBinanceSpotItem))
            delay(resolveReconnectDelayMillis())
        }
    }

    private suspend fun runBinanceFuturesSocketLoop(items: List<WatchItem>) {
        val symbolMap = items.associateBy { it.id.substringAfter("binance-futures:").uppercase() }

        while (scope.isActive && currentConfig.enabled) {
            val closed = CompletableDeferred<Unit>()
            val request = Request.Builder()
                .url(BINANCE_FUTURES_PUBLIC_WS_URL)
                .build()

            binanceFuturesSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        logWs("WS OPEN binance-usdt-futures $BINANCE_FUTURES_PUBLIC_WS_URL")
                        val subscribeRequest = buildJsonObject {
                            put("method", "SUBSCRIBE")
                            put("params", buildJsonArray {
                                symbolMap.keys.sorted().forEach { symbol ->
                                    add(JsonPrimitive("${symbol.lowercase()}@ticker"))
                                }
                            })
                            put("id", 1)
                        }
                        logWs(
                            line = "WS SEND binance-usdt-futures subscribe ${symbolMap.size}",
                            detail = subscribeRequest.toString()
                        )
                        webSocket.send(subscribeRequest.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        logWs("WS RECV binance-usdt-futures $text")
                        parseBinanceTickerMessage(text, symbolMap)?.let { quote ->
                            scope.launch {
                                enqueueQuote(quote)
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        logWs(
                            line = "WS FAIL binance-usdt-futures ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                            detail = t.stackTraceToString()
                        )
                        Log.w(TAG, "Binance USD-M futures WSS failed: ${t.message}")
                        closed.complete(Unit)
                    }
                }
            )

            closed.await()
            binanceFuturesSocket = null
            if (!scope.isActive || !currentConfig.enabled) break

            refreshQuotes(currentConfig.items.filter(::isBinanceUsdtFuturesItem))
            delay(resolveReconnectDelayMillis())
        }
    }

    private suspend fun runAlphaSocketLoop(items: List<WatchItem>) {
        val symbolMap = items.associateBy { it.id.substringAfter("binance-alpha:").uppercase() }

        while (scope.isActive && currentConfig.enabled) {
            val closed = CompletableDeferred<Unit>()
            val request = Request.Builder()
                .url(ALPHA_PUBLIC_WS_URL)
                .build()

            alphaSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        logWs("WS OPEN binance-alpha $ALPHA_PUBLIC_WS_URL")
                        val subscribeRequest = buildJsonObject {
                            put("method", "SUBSCRIBE")
                            put("params", buildJsonArray {
                                symbolMap.keys.sorted().forEach { symbol ->
                                    add(JsonPrimitive("${symbol.lowercase()}@ticker"))
                                }
                            })
                            put("id", 1)
                        }
                        logWs(
                            line = "WS SEND binance-alpha subscribe ${symbolMap.size}",
                            detail = subscribeRequest.toString()
                        )
                        webSocket.send(subscribeRequest.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        logWs("WS RECV binance-alpha $text")
                        parseAlphaTickerMessage(text, symbolMap)?.let { quote ->
                            scope.launch {
                                enqueueQuote(quote)
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        logWs(
                            line = "WS FAIL binance-alpha ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                            detail = t.stackTraceToString()
                        )
                        Log.w(TAG, "Binance alpha WSS failed: ${t.message}")
                        closed.complete(Unit)
                    }
                }
            )

            closed.await()
            alphaSocket = null
            if (!scope.isActive || !currentConfig.enabled) break

            refreshQuotes(currentConfig.items.filter(::isAlphaSpotItem))
            delay(resolveReconnectDelayMillis())
        }
    }

    private suspend fun runOkxSocketLoop(items: List<WatchItem>) {
        val instrumentMap = items.associateBy(::resolveOkxInstrumentId)

        while (scope.isActive && currentConfig.enabled) {
            val closed = CompletableDeferred<Unit>()
            var lastMessageTimestamp = System.currentTimeMillis()
            var heartbeatJob: Job? = null
            val request = Request.Builder()
                .url(OKX_PUBLIC_WS_URL)
                .build()

            okxSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        logWs("WS OPEN okx-public $OKX_PUBLIC_WS_URL")
                        val subscribeRequest = buildJsonObject {
                            put("op", "subscribe")
                            put("args", buildJsonArray {
                                instrumentMap.keys.sorted().forEach { instId ->
                                    add(buildJsonObject {
                                        put("channel", "tickers")
                                        put("instId", instId)
                                    })
                                }
                            })
                        }
                        logWs(
                            line = "WS SEND okx-public subscribe ${instrumentMap.size}",
                            detail = subscribeRequest.toString()
                        )
                        webSocket.send(subscribeRequest.toString())

                        heartbeatJob = scope.launch {
                            while (isActive) {
                                delay(25_000L)
                                if (System.currentTimeMillis() - lastMessageTimestamp >= 25_000L) {
                                    webSocket.send("ping")
                                }
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        lastMessageTimestamp = System.currentTimeMillis()
                        if (text == "pong") return

                        logWs("WS RECV okx-public $text")
                        parseOkxTickerMessages(text, instrumentMap).forEach { quote ->
                            scope.launch {
                                enqueueQuote(quote)
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        heartbeatJob?.cancel()
                        webSocket.close(code, reason)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        heartbeatJob?.cancel()
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        heartbeatJob?.cancel()
                        logWs(
                            line = "WS FAIL okx-public ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                            detail = t.stackTraceToString()
                        )
                        Log.w(TAG, "OKX public ticker WSS failed: ${t.message}")
                        closed.complete(Unit)
                    }
                }
            )

            closed.await()
            heartbeatJob?.cancel()
            okxSocket = null
            if (!scope.isActive || !currentConfig.enabled) break

            refreshQuotes(currentConfig.items.filter(::isOkxPublicTickerItem))
            delay(resolveReconnectDelayMillis())
        }
    }

    private suspend fun runOnchainPollingLoop(items: List<WatchItem>) {
        val itemIds = items.mapTo(mutableSetOf()) { it.id }
        while (scope.isActive && currentConfig.enabled) {
            val currentItems = currentConfig.items.filter { item ->
                item.id in itemIds && isOnchainItem(item)
            }
            if (currentItems.isEmpty()) return
            delay(currentConfig.onchainRefreshIntervalMillis)
            refreshQuotes(currentItems)
        }
    }

    private suspend fun refreshQuotes(items: List<WatchItem>) {
        if (items.isEmpty()) return
        refreshMutex.withLock {
            val quotes = marketQuoteRepository.fetchQuotes(items)
            if (quotes.isNotEmpty()) {
                quoteRepository.applyQuotes(quotes)
            }
        }
    }

    private suspend fun enqueueQuote(quote: MarketQuote) {
        pendingQuoteMutex.withLock {
            pendingQuotes[quote.id] = quote
            if (flushJob?.isActive == true) return

            flushJob = scope.launch {
                delay(350L)
                flushPendingQuotes()
            }
        }
    }

    private suspend fun flushPendingQuotes() {
        val quotesToFlush = pendingQuoteMutex.withLock {
            if (pendingQuotes.isEmpty()) return
            val snapshot = pendingQuotes.values.toList()
            pendingQuotes.clear()
            snapshot
        }
        quoteRepository.applyQuotes(quotesToFlush)
    }

    private fun closeSockets() {
        binanceSocket?.close(1000, "restart")
        binanceSocket = null
        binanceFuturesSocket?.close(1000, "restart")
        binanceFuturesSocket = null
        alphaSocket?.close(1000, "restart")
        alphaSocket = null
        okxSocket?.close(1000, "restart")
        okxSocket = null
    }

    private fun resolveReconnectDelayMillis(): Long {
        return maxOf(currentConfig.refreshIntervalMillis, MIN_RECONNECT_DELAY_MILLIS)
    }

    /**
     * 观察列表每次价格变化都会触发 Room 回流，如果直接用整条 WatchItem 比较，就会导致长连接被反复重建。
     * 这里把“订阅真正关心的字段”压成指纹，只有订阅集合发生变化时才重启连接。
     */
    private fun buildSubscriptionFingerprint(config: QuoteRefreshConfig): String {
        return buildString {
            append(config.enabled)
            append('|')
            append(config.refreshIntervalMillis)
            append('|')
            config.items
                .map { item ->
                    listOf(
                        item.id,
                        item.exchangeSource.name,
                        item.marketType.name,
                        item.chainIndex.orEmpty(),
                        item.tokenAddress.orEmpty()
                    ).joinToString("#")
                }
                .sorted()
                .forEach { key ->
                    append(key)
                    append(';')
                }
        }
    }

    private fun parseBinanceTickerMessage(
        text: String,
        symbolMap: Map<String, WatchItem>
    ): MarketQuote? {
        val payload = runCatching {
            json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return null

        val tickerPayload = payload["data"]?.jsonObject?.takeIf { it["s"] != null } ?: payload
        if (tickerPayload["result"] != null) return null
        val symbol = tickerPayload["s"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return null
        val item = symbolMap[symbol] ?: return null
        val lastPrice = tickerPayload["c"]?.jsonPrimitive?.doubleOrNull ?: return null
        val openPrice = tickerPayload["o"]?.jsonPrimitive?.doubleOrNull
        val changePercent = tickerPayload["P"]?.jsonPrimitive?.doubleOrNull ?: run {
            if (openPrice == null || openPrice <= 0.0) null else ((lastPrice - openPrice) / openPrice) * 100
        }
        return MarketQuote(
            id = item.id,
            symbol = item.symbol,
            name = item.name,
            priceUsd = lastPrice,
            change24hPercent = changePercent
        )
    }

    private fun parseOkxTickerMessages(
        text: String,
        instrumentMap: Map<String, WatchItem>
    ): List<MarketQuote> {
        val payload = runCatching {
            json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return emptyList()
        val arg = payload["arg"]?.jsonObject ?: return emptyList()
        if (arg["channel"]?.jsonPrimitive?.contentOrNull != "tickers") return emptyList()

        val rows = payload["data"]?.jsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            buildOkxQuote(row = row, instrumentMap = instrumentMap)
        }
    }

    private fun buildOkxQuote(
        row: JsonElement,
        instrumentMap: Map<String, WatchItem>
    ): MarketQuote? {
        val jsonRow = row.jsonObject
        val instId = jsonRow["instId"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return null
        val item = instrumentMap[instId] ?: return null
        val last = jsonRow["last"]?.jsonPrimitive?.doubleOrNull ?: return null
        val open24h = jsonRow["open24h"]?.jsonPrimitive?.doubleOrNull
        val changePercent = if (open24h != null && open24h > 0) {
            ((last - open24h) / open24h) * 100
        } else {
            null
        }
        return MarketQuote(
            id = item.id,
            symbol = item.symbol,
            name = item.name,
            priceUsd = last,
            change24hPercent = changePercent
        )
    }

    private fun parseAlphaTickerMessage(
        text: String,
        symbolMap: Map<String, WatchItem>
    ): MarketQuote? {
        val payload = runCatching {
            json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return null

        if (payload["result"] != null) return null
        val data = payload["data"]?.jsonObject ?: return null
        val symbol = data["s"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return null
        val item = symbolMap[symbol] ?: return null
        val lastPrice = data["c"]?.jsonPrimitive?.doubleOrNull ?: return null
        val changePercent = data["P"]?.jsonPrimitive?.doubleOrNull
        return MarketQuote(
            id = item.id,
            symbol = item.symbol,
            name = item.name,
            priceUsd = lastPrice,
            change24hPercent = changePercent
        )
    }

    private fun isBinanceSpotItem(item: WatchItem): Boolean {
        return item.marketType == MarketType.CEX_SPOT &&
            item.exchangeSource == ExchangeSource.BINANCE
    }

    private fun isOkxSpotItem(item: WatchItem): Boolean {
        return item.marketType == MarketType.CEX_SPOT &&
            item.exchangeSource == ExchangeSource.OKX
    }

    private fun isBinanceUsdtFuturesItem(item: WatchItem): Boolean {
        return item.marketType == MarketType.CEX_USDT_FUTURES &&
            item.exchangeSource == ExchangeSource.BINANCE
    }

    private fun isOkxUsdtFuturesItem(item: WatchItem): Boolean {
        return item.marketType == MarketType.CEX_USDT_FUTURES &&
            item.exchangeSource == ExchangeSource.OKX
    }

    private fun isOkxPublicTickerItem(item: WatchItem): Boolean {
        return isOkxSpotItem(item) || isOkxUsdtFuturesItem(item)
    }

    private fun isAlphaSpotItem(item: WatchItem): Boolean {
        return item.marketType == MarketType.CEX_SPOT &&
            item.exchangeSource == ExchangeSource.BINANCE_ALPHA
    }

    private fun isOnchainItem(item: WatchItem): Boolean {
        return item.marketType == MarketType.ONCHAIN_TOKEN
    }

    private fun resolveOkxInstrumentId(item: WatchItem): String {
        return when (item.marketType) {
            MarketType.CEX_USDT_FUTURES -> item.id.substringAfter("okx-futures:")
            else -> item.id.substringAfter("okx:")
        }.uppercase()
    }

    private fun logWs(line: String, detail: String = line) {
        networkLogRepository.append(
            protocol = NetworkLogProtocol.WSS,
            line = NetworkLogRedactor.redactText(line),
            detail = NetworkLogRedactor.redactText(detail)
        )
    }

    companion object {
        private const val TAG = "CoinMonitorWSS"
        private const val BINANCE_PUBLIC_WS_URL = "wss://stream.binance.com:9443/ws"
        private const val BINANCE_FUTURES_PUBLIC_WS_URL = "wss://fstream.binance.com/market/ws"
        private const val ALPHA_PUBLIC_WS_URL = "wss://nbstream.binance.com/w3w/wsa/stream"
        private const val OKX_PUBLIC_WS_URL = "wss://ws.okx.com:8443/ws/v5/public"
        private const val MIN_RECONNECT_DELAY_MILLIS = 15_000L
    }
}
