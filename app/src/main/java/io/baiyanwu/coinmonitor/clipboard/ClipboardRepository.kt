package io.baiyanwu.coinmonitor.clipboard

import android.content.Context
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ClipboardSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("clipboard_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableSettings = MutableStateFlow(runCatching {
        json.decodeFromString<ClipboardSettings>(preferences.getString("settings", null) ?: "{}")
    }.getOrDefault(ClipboardSettings()))
    val settings = mutableSettings.asStateFlow()

    @Synchronized
    fun update(change: (ClipboardSettings) -> ClipboardSettings) {
        val next = change(mutableSettings.value)
        preferences.edit().putString("settings", json.encodeToString(next)).apply()
        mutableSettings.value = next
    }
}

data class ClipboardChartPoint(val timestamp: Double, val price: Double)

fun clipboardChartPoints(rows: List<List<Double>>): List<ClipboardChartPoint> = rows.mapNotNull { row ->
    val timestamp = row.getOrNull(0) ?: return@mapNotNull null
    val close = row.getOrNull(4) ?: return@mapNotNull null
    if (!timestamp.isFinite() || !close.isFinite() || close <= 0) null else ClipboardChartPoint(timestamp, close)
}.distinctBy { it.timestamp }.sortedBy { it.timestamp }

internal interface ClipboardDataSource {
    suspend fun lookup(address: String): List<ClipboardMatch>
    suspend fun chart(match: ClipboardMatch): List<ClipboardChartPoint>
    suspend fun holders(match: ClipboardMatch): Int?
}

class ClipboardRepository(
    private val dex: DexScreenerClient,
    private val gecko: GeckoTerminalClient
) : ClipboardDataSource {
    private val lookupMutex = Mutex()
    private val chartMutex = Mutex()
    private val holdersMutex = Mutex()
    private val matches = LinkedHashMap<String, Pair<Long, List<ClipboardMatch>>>()
    private val charts = LinkedHashMap<String, Pair<Long, List<ClipboardChartPoint>>>()
    private val holderCounts = LinkedHashMap<String, Pair<Long, Int?>>()

    override suspend fun lookup(address: String): List<ClipboardMatch> = lookupMutex.withLock {
        val key = if (address.startsWith("0x")) address.lowercase() else address
        cached(matches, key)?.let { return@withLock it }
        clipboardMatches(address, dex.searchPairs(address)).also { put(matches, key, it) }
    }

    override suspend fun chart(match: ClipboardMatch): List<ClipboardChartPoint> = chartMutex.withLock {
        val selection = match.selection
        val key = "${match.chain.dexScreenerId}:${selection.pair.pairAddress}:${selection.tokenSide}"
        cached(charts, key)?.let { return@withLock it }
        val response = gecko.getPoolOhlcv(match.chain.geckoTerminalId, selection.pair.pairAddress,
            "hour", 1, 24, selection.tokenSide)
        val now = System.currentTimeMillis() / 1000.0
        clipboardChartPoints(response.data.attributes.ohlcvList)
            .filter { it.timestamp in (now - 86_400)..now }.also { put(charts, key, it) }
    }

    override suspend fun holders(match: ClipboardMatch): Int? = holdersMutex.withLock {
        val address = match.selection.tokenAddress
        val key = "${match.chain.geckoTerminalId}:${address.lowercase()}"
        cached(holderCounts, key)?.let { return@withLock it }
        val attributes = gecko.getTokenHolders(match.chain.geckoTerminalId, address).data.attributes
        val matchesAddress = if (match.chain.family == io.baiyanwu.coinmonitor.domain.model.ChainFamily.EVM) {
            attributes.address.equals(address, ignoreCase = true)
        } else {
            attributes.address == address
        }
        val count = attributes.holders?.count?.takeIf { matchesAddress && it >= 0 }
        put(holderCounts, key, count)
        count
    }

    private fun <T> cached(cache: Map<String, Pair<Long, T>>, key: String): T? = cache[key]
        ?.takeIf { System.currentTimeMillis() - it.first in 0 until 60_000 }?.second

    private fun <T> put(cache: LinkedHashMap<String, Pair<Long, T>>, key: String, value: T) {
        cache.remove(key)
        cache[key] = System.currentTimeMillis() to value
        if (cache.size > 32) cache.remove(cache.keys.first())
    }
}
