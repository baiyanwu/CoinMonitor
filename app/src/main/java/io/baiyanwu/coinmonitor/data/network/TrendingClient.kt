package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.resumeWithException

enum class TrendingFailure { CREDENTIALS, AUTHENTICATION, QUOTA, RATE_LIMIT, NETWORK, INVALID_DATA }
class TrendingException(val failure: TrendingFailure, val retryAt: Long = 0) : Exception(failure.name)

class TrendingClient(private val httpClient: OkHttpClient) {
    private val sourceLocks = TrendingSource.entries.associateWith { Mutex() }
    private val lastStarted = java.util.concurrent.ConcurrentHashMap<TrendingSource, Long>()
    // enqueue() only moves transport off-thread. Reading its body and decoding also need IO.
    suspend fun fetch(source: TrendingSource, options: TrendingOptions, gmgnKey: String, okx: OkxWalletCredentials): List<TrendingToken> = withContext(Dispatchers.IO) {
        when (source) {
            TrendingSource.PUMP -> TrendingDecoder.pump(execute(pumpRequest(), source))
            TrendingSource.OKX -> {
                if (!okx.isReady) throw TrendingException(TrendingFailure.CREDENTIALS)
                TrendingDecoder.okx(execute(okxRequest(options, okx), source), options.chain)
            }
            TrendingSource.GMGN -> {
                if (gmgnKey.isBlank()) throw TrendingException(TrendingFailure.CREDENTIALS)
                if (options.mode == TrendingMode.HOT_SEARCH) {
                    TrendingDecoder.hotSearch(execute(gmgnHotSearchRequest(options, gmgnKey), source), options)
                } else {
                    val rows = mutableListOf<TrendingToken>()
                    (options.chain?.let(::listOf) ?: TrendingChain.entries).forEachIndexed { index, chain ->
                        if (index > 0) delay(2_000)
                        rows += TrendingDecoder.gmgn(execute(gmgnRequest(chain, options.period, gmgnKey), source), chain)
                    }
                    rows.sortedByDescending { it.volume ?: Double.NEGATIVE_INFINITY }.take(30)
                }
            }
        }
    }
    private suspend fun execute(request: Request, source: TrendingSource): JsonElement {
        return sourceLocks.getValue(source).withLock {
            // This gate also covers the last request of an all-chain fetch when filters change.
            val wait = (lastStarted[source] ?: 0) + 2000 - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastStarted[source] = System.currentTimeMillis()
            executeRequest(request, source)
        }
    }
    private suspend fun executeRequest(request: Request, source: TrendingSource): JsonElement {
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(TrendingException(TrendingFailure.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            val element = runCatching { Json.parseToJsonElement(it.body?.string().orEmpty()) }.getOrNull()
            validate(source, it.code, it.headers, element, System.currentTimeMillis())
            return element ?: throw TrendingException(TrendingFailure.INVALID_DATA)
        }
    }
    companion object {
        fun pumpRequest(): Request = Request.Builder()
            .url("https://frontend-api-v3.pump.fun/coins/great-coins?topK=50")
            .header("Accept", "application/json").get().build()
        fun gmgnRequest(chain: TrendingChain, period: String, key: String, now: Long = System.currentTimeMillis()): Request {
            val url = HttpUrl.Builder().scheme("https").host("openapi.gmgn.ai").addPathSegments("v1/market/rank")
                .addQueryParameter("chain", chain.gmgnId).addQueryParameter("interval", period)
                .addQueryParameter("limit", "30").addQueryParameter("order_by", "volume")
                .addQueryParameter("direction", "desc").addQueryParameter("timestamp", (now / 1000).toString())
                .addQueryParameter("client_id", UUID.randomUUID().toString())
            chain.filters.forEach { url.addQueryParameter("filters", it) }
            return Request.Builder().url(url.build()).header("X-APIKEY", key).header("Accept", "application/json").build()
        }
        fun gmgnHotSearchRequest(options: TrendingOptions, key: String, now: Long = System.currentTimeMillis()): Request {
            val url = HttpUrl.Builder().scheme("https").host("openapi.gmgn.ai").addPathSegments("v1/market/hot_searches")
                .addQueryParameter("timestamp", (now / 1000).toString()).addQueryParameter("client_id", UUID.randomUUID().toString()).build()
            val body = buildJsonObject {
                put("params", buildJsonArray {
                    (options.chain?.let(::listOf) ?: TrendingChain.entries).forEach { chain ->
                        add(buildJsonObject {
                            put("label", "hot-search"); put("chain", chain.gmgnId); put("interval", options.period)
                            put("limit", 30); put("filters", buildJsonArray { chain.filters.forEach { add(it) } })
                        })
                    }
                })
            }.toString()
            return Request.Builder().url(url).header("X-APIKEY", key).header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType())).build()
        }
        fun okxRequest(options: TrendingOptions, credentials: OkxWalletCredentials, now: Long = System.currentTimeMillis()): Request {
            val frame = mapOf("5m" to "1", "1h" to "2", "4h" to "3", "24h" to "4")[options.period]
                ?: throw TrendingException(TrendingFailure.INVALID_DATA)
            val url = HttpUrl.Builder().scheme("https").host("web3.okx.com").addPathSegments("api/v6/dex/market/token/hot-token")
                .addQueryParameter("rankingType", "4").addQueryParameter("rankingTimeFrame", frame)
                .addQueryParameter("rankBy", if (options.mode == TrendingMode.HOT_SEARCH) "9" else "5")
                .addQueryParameter("riskFilter", "true").addQueryParameter("stableTokenFilter", "true").addQueryParameter("limit", "30")
            options.chain?.let { url.addQueryParameter("chainIndex", it.okxId) }
            val target = url.build()
            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(now))
            return Request.Builder().url(target).header("Accept", "application/json")
                .header("OK-ACCESS-KEY", credentials.apiKey).header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", credentials.passphrase)
                .header("OK-ACCESS-SIGN", OkxOnchainRequestSigner.signature(credentials.secretKey, timestamp, "GET", target.encodedPath + "?" + target.encodedQuery))
                .get().build()
        }
        fun validate(source: TrendingSource, status: Int, headers: Headers, body: JsonElement?, now: Long) {
            val root = body as? JsonObject
            val nested = root?.get("data") as? JsonObject
            val codes = listOfNotNull(root?.text("code"), nested?.text("code"))
            if (status == 429 || "429" in codes || (source == TrendingSource.OKX && "50011" in codes)) {
                val retry = retryAfter(headers["Retry-After"], now)
                val resets = listOfNotNull(headers["X-RateLimit-Reset"]?.toDoubleOrNull(), root?.number("reset_at"), nested?.number("reset_at"))
                    .filter { it.isFinite() && it > 0 && it < Long.MAX_VALUE / 1000.0 }.map { (it * 1000).toLong() + 1000 }
                val fallback = now + if (source == TrendingSource.GMGN) 300_000 else 60_000
                throw TrendingException(TrendingFailure.RATE_LIMIT, maxOf(now + 2000, (resets + listOfNotNull(retry)).maxOrNull() ?: fallback))
            }
            if (status == 402) throw TrendingException(TrendingFailure.QUOTA)
            if (status in listOf(401, 403) || codes.any { it == "401" || it == "403" || (source == TrendingSource.OKX && it.toIntOrNull() in 50100..50199) })
                throw TrendingException(TrendingFailure.AUTHENTICATION)
            if (status !in 200..299) throw TrendingException(TrendingFailure.NETWORK)
            if (source != TrendingSource.PUMP && (root?.text("code") != "0" || codes.any { it != "0" }))
                throw TrendingException(TrendingFailure.INVALID_DATA)
        }
        private fun retryAfter(value: String?, now: Long): Long? {
            val seconds = value?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE / 1000.0 }
            if (seconds != null) return now + (seconds * 1000).toLong()
            return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        }
    }
}

object TrendingDecoder {
    fun gmgn(element: JsonElement, chain: TrendingChain): List<TrendingToken> {
        val root = element as? JsonObject ?: invalid()
        if (root.text("code") != "0") invalid()
        val payload = root["data"] as? JsonObject ?: invalid()
        val body = if (payload["rank"] != null) payload else {
            if (payload.text("code") != "0") invalid()
            payload["data"] as? JsonObject ?: invalid()
        }
        val rows = body["rank"] as? JsonArray ?: invalid()
        return rows.mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val address = row.text("address") ?: return@mapNotNull null
            if (!chain.validAddress(address) || row.text("chain") != chain.gmgnId) return@mapNotNull null
            TrendingToken(chain.okxId, chain.title, address, row.text("symbol") ?: "--", row.text("name").orEmpty(),
                logo(row.text("logo")), row.number("price"), row.number("market_cap"), row.number("price_change_percent"),
                row.number("volume"), row.number("liquidity"), row.number("smart_degen_count"), row.number("visiting_count"),
                (row.number("rug_ratio") ?: 0.0) > 0.3 || row.flag("is_wash_trading") || (chain != TrendingChain.SOLANA && row.flag("is_honeypot")),
                mapOf(TrendingSource.GMGN to "https://gmgn.ai/${chain.gmgnId}/token/$address"))
        }.distinctBy(TrendingToken::id)
    }
    fun hotSearch(element: JsonElement, options: TrendingOptions): List<TrendingToken> {
        val root = element as? JsonObject ?: invalid()
        if (root.text("code") != "0") invalid()
        val payload = root["data"]
        val blocks = payload as? JsonArray ?: (payload as? JsonObject)?.let {
            if (it.text("code") != "0") invalid()
            it["data"] as? JsonArray
        } ?: invalid()
        return blocks.flatMap { element ->
            val block = element as? JsonObject ?: return@flatMap emptyList()
            val chain = TrendingChain.entries.firstOrNull { it.gmgnId == block.text("chain") } ?: return@flatMap emptyList()
            if ((options.chain != null && chain != options.chain) || block.text("interval") != options.period) return@flatMap emptyList()
            val tokens = block["tokens"] as? JsonArray ?: return@flatMap emptyList()
            val normalized = tokens.mapNotNull { it as? JsonObject }.filter { it.text("chain") == null || it.text("chain") == chain.gmgnId }
                .map { JsonObject(it + ("chain" to JsonPrimitive(chain.gmgnId))) }
            gmgn(buildJsonObject { put("code", 0); put("data", buildJsonObject { put("rank", JsonArray(normalized)) }) }, chain)
        }.distinctBy(TrendingToken::id).sortedByDescending { it.visitingCount ?: Double.NEGATIVE_INFINITY }.take(30)
    }
    fun okx(element: JsonElement, selectedChain: TrendingChain?): List<TrendingToken> {
        val root = element as? JsonObject ?: invalid()
        if (root.text("code") != "0") invalid()
        val rows = root["data"] as? JsonArray ?: invalid()
        return rows.mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val chainId = row.text("chainIndex")?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: return@mapNotNull null
            val chain = TrendingChain.entries.firstOrNull { it.okxId == chainId }
            if (selectedChain != null && chain != selectedChain) return@mapNotNull null
            val address = row.text("tokenContractAddress") ?: return@mapNotNull null
            if (chain?.validAddress(address) == false || (chain == null && !TrendingChain.ETHEREUM.validAddress(address))) return@mapNotNull null
            TrendingToken(chainId, chain?.title ?: "Chain #$chainId", address, row.text("tokenSymbol") ?: "--",
                iconUrl = logo(row.text("tokenLogoUrl")), price = row.number("price"), marketCap = row.number("marketCap"),
                change = row.number("change"), volume = row.number("volume"), liquidity = row.number("liquidity"),
                risk = (row.number("riskLevelControl") ?: 0.0) >= 3,
                links = mapOf(TrendingSource.OKX to "https://web3.okx.com/token/$chainId/$address"))
        }.distinctBy(TrendingToken::id)
    }
    fun pump(element: JsonElement): List<TrendingToken> {
        val rows = element as? JsonArray ?: invalid()
        return rows.mapNotNull { it as? JsonObject }.mapNotNull { row ->
            if (row.text("chain_id") != null && row.text("chain_id") != "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp") return@mapNotNull null
            val address = row.text("mint") ?: invalid()
            if (!TrendingChain.SOLANA.validAddress(address)) invalid()
            val marketCap = (row.number("usd_market_cap") ?: row.number("market_cap_usd"))?.takeIf { it >= 0 }
            TrendingToken("501", "Solana", address, row.text("symbol") ?: invalid(), row.text("name") ?: invalid(),
                iconUrl = logo(row.text("image_uri")),
                price = pumpPrice(row, marketCap), marketCap = marketCap,
                liquidity = row.number("canonical_pool_liquidity_usd")?.takeIf { it >= 0 },
                links = mapOf(TrendingSource.PUMP to "https://pump.fun/coin/$address"))
        }.distinctBy(TrendingToken::id)
    }
    private fun pumpPrice(row: JsonObject, marketCap: Double?): Double? {
        val cap = marketCap ?: return null
        val decimals = row.text("base_decimals")?.toIntOrNull()?.takeIf { it in 0..18 } ?: return null
        val rawSupply = row.text("circulating_supply_str")?.takeIf { it.matches(Regex("[0-9]+")) }
            ?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        // The API's circulating supply is in atomic units; never assume a fixed 1B supply.
        return java.math.BigDecimal.valueOf(cap).movePointRight(decimals)
            .divide(rawSupply, java.math.MathContext.DECIMAL64).toDouble().takeIf(Double::isFinite)
    }
    private fun logo(value: String?): String? = value?.takeIf { it.startsWith("https://") && runCatching { java.net.URI(it).host != null }.getOrDefault(false) }
    private fun invalid(): Nothing = throw TrendingException(TrendingFailure.INVALID_DATA)
}

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(name: String): Double? = text(name)?.toDoubleOrNull()?.takeIf(Double::isFinite)
private fun JsonObject.flag(name: String): Boolean = text(name) in listOf("true", "1")
