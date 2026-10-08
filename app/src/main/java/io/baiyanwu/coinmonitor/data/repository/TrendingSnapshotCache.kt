package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.TrendingSource
import io.baiyanwu.coinmonitor.domain.model.TrendingToken
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedTrendingSnapshot(
    val tokens: List<TrendingToken>,
    val updatedAt: Long,
    val newDates: Map<String, Long> = emptyMap(),
    val sourceDates: Map<TrendingSource, Long> = emptyMap()
)

@Serializable
data class CachedTrendingLimit(
    val next: Long = 0,
    val failure: String? = null,
    val blocked: Boolean = false
)

data class TrendingCacheState(
    val loaded: Boolean = false,
    val snapshots: Map<String, CachedTrendingSnapshot> = emptyMap(),
    val limits: Map<TrendingSource, CachedTrendingLimit> = emptyMap()
)

/** App-owned public ranking cache, warmed before opening the page and retained across navigation. */
class TrendingSnapshotCache(private val file: File, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val writeMutex = Mutex()
    private val _state = MutableStateFlow(TrendingCacheState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            val document = withContext(Dispatchers.IO) {
                runCatching {
                    json.decodeFromString<CacheDocument>(file.readText()).takeIf { it.version == 1 }
                }.getOrNull()
            }
            _state.value = TrendingCacheState(true, document?.snapshots.orEmpty(), document?.limits.orEmpty())
        }
    }

    suspend fun awaitLoaded(): TrendingCacheState = state.first { it.loaded }

    fun put(query: String, snapshot: CachedTrendingSnapshot) {
        check(_state.value.loaded)
        _state.update { current ->
            val entries = (current.snapshots + (query to snapshot)).entries
                .sortedByDescending { it.value.updatedAt }.take(MAX_SNAPSHOTS)
                .associate { it.toPair() }
            current.copy(snapshots = entries)
        }
        scheduleWrite()
    }

    fun putLimit(source: TrendingSource, limit: CachedTrendingLimit) {
        check(_state.value.loaded)
        _state.update { it.copy(limits = it.limits + (source to limit)) }
        scheduleWrite()
    }

    fun removeSource(source: TrendingSource) {
        check(_state.value.loaded)
        _state.update { current ->
            current.copy(snapshots = current.snapshots.filterKeys { !belongsToSource(it, source) },
                limits = current.limits - source)
        }
        scheduleWrite()
    }

    private fun scheduleWrite() {
        scope.launch { runCatching { flush() } }
    }

    /** Serialize the latest state under the lock, so older queued writes cannot replace newer data. */
    suspend fun flush() = writeMutex.withLock {
        val current = _state.value
        if (!current.loaded) return@withLock
        withContext(Dispatchers.IO) {
            requireNotNull(file.parentFile).mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(json.encodeToString(CacheDocument(snapshots = current.snapshots, limits = current.limits)))
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        private const val MAX_SNAPSHOTS = 64
        fun belongsToSource(query: String, source: TrendingSource): Boolean {
            val marker = if (source == TrendingSource.PUMP) "pump" else source.name + ":"
            return query.startsWith(marker) || (query.startsWith("SHARED:") && query.contains(marker))
        }
    }
}

@Serializable
private data class CacheDocument(
    val version: Int = 1,
    val snapshots: Map<String, CachedTrendingSnapshot> = emptyMap(),
    val limits: Map<TrendingSource, CachedTrendingLimit> = emptyMap()
)
