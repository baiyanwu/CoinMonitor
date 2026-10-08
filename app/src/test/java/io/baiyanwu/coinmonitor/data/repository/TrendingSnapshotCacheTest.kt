package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.TrendingSource
import io.baiyanwu.coinmonitor.domain.model.TrendingToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrendingSnapshotCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val row = TrendingToken("501", "Solana", "So11111111111111111111111111111111111111112", "SOL",
        price = 0.0001234, marketCap = 1234.0, smartMoney = 12.0,
        links = mapOf(TrendingSource.GMGN to "https://gmgn.ai/sol/token/test"))
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test fun `source snapshots and complete shared rows survive reopening the cache`() = runBlocking {
        val file = File(temporary.root, "cache.json")
        val firstScope = scope()
        val secondScope = scope()
        try {
            val first = TrendingSnapshotCache(file, firstScope)
            first.awaitLoaded()
            val source = CachedTrendingSnapshot(listOf(row), 1000, mapOf(row.id to 900L))
            val shared = source.copy(sourceDates = mapOf(TrendingSource.GMGN to 1000L, TrendingSource.OKX to 950L))
            first.put("GMGN:SOLANA:TRENDING:5m", source)
            first.put("SHARED:GMGN:SOLANA:TRENDING:5m|OKX:SOLANA:TRENDING:5m", shared)
            first.flush()
            val reopened = TrendingSnapshotCache(file, secondScope).awaitLoaded()
            assertEquals(source, reopened.snapshots["GMGN:SOLANA:TRENDING:5m"])
            assertEquals(shared, reopened.snapshots["SHARED:GMGN:SOLANA:TRENDING:5m|OKX:SOLANA:TRENDING:5m"])
            assertEquals(12.0, reopened.snapshots.values.first().tokens.first().smartMoney!!, 0.0)
        } finally { firstScope.cancel(); secondScope.cancel() }
    }
    @Test fun `memory cache is updated before asynchronous disk persistence`() = runBlocking {
        val taskScope = scope()
        try {
            val cache = TrendingSnapshotCache(File(temporary.root, "cache.json"), taskScope)
            cache.awaitLoaded()
            val snapshot = CachedTrendingSnapshot(listOf(row), 1000)
            cache.put("pump", snapshot)
            assertEquals(snapshot, cache.state.value.snapshots["pump"])
        } finally { taskScope.cancel() }
    }
    @Test fun `failed request cooldown survives a new page and process cache`() = runBlocking {
        val file = File(temporary.root, "cache.json")
        val firstScope = scope(); val nextScope = scope()
        try {
            val first = TrendingSnapshotCache(file, firstScope)
            first.awaitLoaded()
            val snapshot = CachedTrendingSnapshot(listOf(row), 1000)
            val limit = CachedTrendingLimit(900000, "RATE_LIMIT", false)
            first.put("GMGN:SOLANA:TRENDING:5m", snapshot)
            first.putLimit(TrendingSource.GMGN, limit)
            first.flush()
            val restored = TrendingSnapshotCache(file, nextScope).awaitLoaded()
            assertEquals(limit, restored.limits[TrendingSource.GMGN])
            assertEquals(snapshot, restored.snapshots["GMGN:SOLANA:TRENDING:5m"])
        } finally { firstScope.cancel(); nextScope.cancel() }
    }
    @Test fun `invalidating one source drops its shared rows and keeps other sources`() = runBlocking {
        val taskScope = scope()
        try {
            val cache = TrendingSnapshotCache(File(temporary.root, "cache.json"), taskScope)
            cache.awaitLoaded()
            val snapshot = CachedTrendingSnapshot(listOf(row), 1000)
            cache.put("GMGN:SOLANA:TRENDING:5m", snapshot)
            cache.put("OKX:SOLANA:TRENDING:5m", snapshot)
            cache.put("pump", snapshot)
            cache.put("SHARED:GMGN:SOLANA:TRENDING:5m|pump", snapshot)
            cache.putLimit(TrendingSource.GMGN, CachedTrendingLimit(2000, "AUTHENTICATION", true))
            cache.removeSource(TrendingSource.GMGN)
            assertEquals(setOf("OKX:SOLANA:TRENDING:5m", "pump"), cache.state.value.snapshots.keys)
            assertFalse(TrendingSource.GMGN in cache.state.value.limits)
        } finally { taskScope.cancel() }
    }
    @Test fun `cache corruption does not block first network loading`() = runBlocking {
        val file = File(temporary.root, "cache.json").apply { writeText("broken json") }
        val taskScope = scope()
        try {
            val restored = TrendingSnapshotCache(file, taskScope).awaitLoaded()
            assertTrue(restored.loaded)
            assertTrue(restored.snapshots.isEmpty())
        } finally { taskScope.cancel() }
    }
    @Test fun `queued saves never overwrite the newest successful snapshot`() = runBlocking {
        val file = File(temporary.root, "cache.json")
        val taskScope = scope(); val nextScope = scope()
        try {
            val cache = TrendingSnapshotCache(file, taskScope)
            cache.awaitLoaded()
            repeat(10) { cache.put("pump", CachedTrendingSnapshot(listOf(row.copy(marketCap = it.toDouble())), it.toLong() + 1000)) }
            cache.flush()
            val restored = TrendingSnapshotCache(file, nextScope).awaitLoaded().snapshots.getValue("pump")
            assertEquals(1009L, restored.updatedAt)
            assertEquals(9.0, restored.tokens.single().marketCap!!, 0.0)
        } finally { taskScope.cancel(); nextScope.cancel() }
    }
}
