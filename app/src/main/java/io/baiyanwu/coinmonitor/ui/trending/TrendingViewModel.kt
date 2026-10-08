package io.baiyanwu.coinmonitor.ui.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.network.*
import io.baiyanwu.coinmonitor.data.repository.GmgnCredentialsRepository
import io.baiyanwu.coinmonitor.data.repository.TrendingPreferences
import io.baiyanwu.coinmonitor.data.repository.TrendingSnapshotCache
import io.baiyanwu.coinmonitor.data.repository.CachedTrendingSnapshot
import io.baiyanwu.coinmonitor.data.repository.CachedTrendingLimit
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.domain.repository.OkxWalletCredentialsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TrendingSnapshot(
    val tokens: List<TrendingToken> = emptyList(),
    val updatedAt: Long? = null,
    val newDates: Map<String, Long> = emptyMap(),
    val loading: Boolean = false,
    val failure: TrendingFailure? = null,
    val retryAt: Long = 0,
    val sourceDates: Map<TrendingSource, Long> = emptyMap()
)
data class TrendingUiState(
    val board: TrendingBoard = TrendingBoard.SHARED,
    val sources: Set<TrendingSource> = TrendingSource.entries.toSet(),
    val options: Map<TrendingSource, TrendingOptions> = TrendingSource.entries.associateWith { TrendingOptions() },
    val sharedOptions: TrendingOptions = TrendingOptions(),
    val snapshots: Map<TrendingSource, TrendingSnapshot> = emptyMap(),
    val ready: Set<TrendingSource> = setOf(TrendingSource.PUMP),
    val sharedSnapshot: TrendingSnapshot? = null,
    val cacheRestoring: Boolean = false,
    val now: Long = System.currentTimeMillis()
) {
    val demand: Set<TrendingSource> get() = TrendingRules.demand(board, sources)
    fun rows(board: TrendingBoard): List<TrendingToken> {
        val rows = if (board == TrendingBoard.SHARED) sharedSnapshot?.tokens ?: TrendingRules.intersection(sources,
            snapshots.filter { it.value.updatedAt != null }.mapValues { it.value.tokens })
        else snapshots[TrendingRules.demand(board, sources).first()]?.tokens.orEmpty()
        return TrendingRules.sorted(rows, displayOptions(board), newIds(board))
    }
    fun displayOptions(board: TrendingBoard): TrendingOptions = if (board == TrendingBoard.SHARED) sharedOptions
        else options.getValue(TrendingRules.demand(board, sources).first())
    fun newIds(board: TrendingBoard): Set<String> {
        val dates = if (board == TrendingBoard.SHARED) sharedSnapshot?.newDates.orEmpty()
            else snapshots[TrendingRules.demand(board, sources).first()]?.newDates.orEmpty()
        return dates.filter { now - it.value < 180_000 }.keys
    }
}

class TrendingViewModel(
    private val client: TrendingClient,
    private val preferences: TrendingPreferences,
    private val gmgn: GmgnCredentialsRepository,
    private val okx: OkxWalletCredentialsRepository,
    private val snapshotCache: TrendingSnapshotCache
) : ViewModel() {
    private val initialCacheState = snapshotCache.state.value
    private val cache = initialCacheState.snapshots.mapValues { it.value.toUi() }.toMutableMap()
    private var cacheLoaded = initialCacheState.loaded
    private val _state = MutableStateFlow(TrendingUiState(
        board = preferences.board(),
        sources = preferences.selectedSources(), options = TrendingSource.entries.associateWith(preferences::load),
        sharedOptions = preferences.loadShared(), cacheRestoring = !cacheLoaded,
        ready = buildSet {
            add(TrendingSource.PUMP)
            if (gmgn.apiKey.value.isNotBlank()) add(TrendingSource.GMGN)
            if (okx.getCredentials().isReady) add(TrendingSource.OKX)
        }))
    val state = _state.asStateFlow()
    private data class Limit(val next: Long = 0, val failure: TrendingFailure? = null, val blocked: Boolean = false)
    private val limits = initialCacheState.limits.mapValues { (_, value) ->
        Limit(value.next, TrendingFailure.entries.firstOrNull { it.name == value.failure }, value.blocked)
    }.toMutableMap()
    private val invalidatedWhileLoading = mutableSetOf<TrendingSource>()
    private val jobs = mutableMapOf<TrendingSource, Job>()
    private var ticker: Job? = null
    private var active = false
    private var lastGmgnKey = gmgn.apiKey.value
    private var lastOkx = okx.getCredentials()

    init {
        // Publish warmed app memory before starting any request or waiting for credential flows.
        publish()
        viewModelScope.launch {
            if (!cacheLoaded) {
                snapshotCache.awaitLoaded()
                invalidatedWhileLoading.forEach(snapshotCache::removeSource)
                invalidatedWhileLoading.clear()
                val restored = snapshotCache.state.value
                cache.putAll(restored.snapshots.mapValues { it.value.toUi() })
                limits.putAll(restored.limits.mapValues { (_, value) ->
                    Limit(value.next, TrendingFailure.entries.firstOrNull { it.name == value.failure }, value.blocked)
                })
                cacheLoaded = true
                publish()
                reconcile()
            }
        }
        viewModelScope.launch {
            combine(gmgn.apiKey, okx.observeCredentials()) { key, credentials -> key to credentials }
                .collect { (key, credentials) ->
                    val changed = buildSet {
                        if (key != lastGmgnKey) add(TrendingSource.GMGN)
                        if (credentials != lastOkx) add(TrendingSource.OKX)
                    }
                    lastGmgnKey = key; lastOkx = credentials
                    changed.forEach { source ->
                        jobs.remove(source)?.cancel()
                        cache.keys.filter { TrendingSnapshotCache.belongsToSource(it, source) }.forEach(cache::remove)
                        limits.remove(source)
                        if (cacheLoaded) snapshotCache.removeSource(source) else invalidatedWhileLoading.add(source)
                    }
                    _state.update { it.copy(ready = buildSet {
                        add(TrendingSource.PUMP)
                        if (key.isNotBlank()) add(TrendingSource.GMGN)
                        if (credentials.isReady) add(TrendingSource.OKX)
                    }) }
                    publish(); reconcile()
                }
        }
    }
    fun start() {
        if (active) return
        active = true
        reconcile()
        ticker = viewModelScope.launch { while (isActive) { publish(); delay(1000) } }
    }
    fun stop() {
        active = false
        jobs.values.forEach(Job::cancel); jobs.clear()
        ticker?.cancel(); ticker = null
    }
    fun selectBoard(board: TrendingBoard) {
        if (_state.value.board == board) return
        preferences.saveBoard(board)
        _state.update { it.copy(board = board) }
        reconcile()
    }
    fun toggleSource(source: TrendingSource) {
        val selected = _state.value.sources.toMutableSet()
        if (source in selected && selected.size <= 2) return
        if (!selected.add(source)) selected.remove(source)
        preferences.saveSources(selected)
        _state.update { it.copy(sources = selected, sharedOptions = if (TrendingSource.GMGN !in selected && it.sharedOptions.sort == TrendingSort.SMART_MONEY)
            it.sharedOptions.copy(sort = TrendingSort.DEFAULT) else it.sharedOptions) }
        preferences.saveShared(_state.value.sharedOptions)
        publish(); reconcile()
    }
    fun updateOptions(source: TrendingSource, options: TrendingOptions) {
        val old = _state.value.options.getValue(source)
        preferences.save(source, options)
        _state.update { it.copy(options = it.options + (source to options)) }
        if (old.queryKey(source) != options.queryKey(source)) jobs.remove(source)?.cancel()
        publish(); reconcile()
    }
    fun updateDisplay(board: TrendingBoard, options: TrendingOptions) {
        if (board == TrendingBoard.SHARED) {
            preferences.saveShared(options)
            _state.update { it.copy(sharedOptions = options) }
        }
        else updateOptions(TrendingRules.demand(board, _state.value.sources).first(), options)
    }
    fun refresh() {
        if (!cacheLoaded) return
        _state.value.demand.filter { it in _state.value.ready && _state.value.snapshots[it]?.loading != true }.forEach { source ->
            // A manual retry can recheck credentials/quota, but never bypasses a server cooldown.
            val limit = limits[source] ?: Limit()
            if (System.currentTimeMillis() >= limit.next) {
                if (limit.blocked) setLimit(source, limit.copy(blocked = false))
                jobs.remove(source)?.cancel()
                if (active) launchSource(source, force = true)
            }
        }
    }
    private fun reconcile() {
        val wanted = if (active && cacheLoaded) _state.value.demand else emptySet()
        jobs.keys.toList().filter { it !in wanted || it !in _state.value.ready }.forEach { jobs.remove(it)?.cancel() }
        wanted.filter { it in _state.value.ready && jobs[it]?.isActive != true }.forEach { launchSource(it) }
    }
    private fun launchSource(source: TrendingSource, force: Boolean = false) {
        jobs[source] = viewModelScope.launch {
            var requested = force
            while (isActive) {
                val options = _state.value.options.getValue(source)
                val key = options.queryKey(source)
                val snapshot = cache[key] ?: TrendingSnapshot()
                val limit = limits[source] ?: Limit()
                val now = System.currentTimeMillis()
                if (TrendingRefreshPolicy.isDue(snapshot.updatedAt, options, now, limit.next, limit.blocked, requested)) {
                    requested = false
                    setLimit(source, limit.copy(next = now + 2000))
                    cache[key] = snapshot.copy(loading = true)
                    publish()
                    try {
                        val rows = client.fetch(source, options, gmgn.apiKey.value, okx.getCredentials())
                        ensureActive()
                        val received = System.currentTimeMillis()
                        cache[key] = TrendingSnapshot(rows, received,
                            TrendingRules.newDates(rows.map { it.id }, snapshot.updatedAt?.let { snapshot.tokens.map { row -> row.id } }, snapshot.newDates, received))
                        snapshotCache.put(key, cache.getValue(key).toCached())
                        setLimit(source, Limit(received + 2000))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val failure = (error as? TrendingException)?.failure ?: TrendingFailure.NETWORK
                        val blocked = failure in setOf(TrendingFailure.CREDENTIALS, TrendingFailure.AUTHENTICATION, TrendingFailure.QUOTA)
                        val next = when {
                            failure == TrendingFailure.RATE_LIMIT -> (error as TrendingException).retryAt
                            blocked -> System.currentTimeMillis() + 2000
                            else -> System.currentTimeMillis() + 60_000
                        }
                        setLimit(source, Limit(next, failure, blocked))
                        cache[key] = snapshot.copy(failure = failure, retryAt = next)
                    } finally {
                        cache[key]?.let { cache[key] = it.copy(loading = false) }
                        publish()
                    }
                }
                delay(1000)
            }
        }
    }
    private fun publish() {
        val now = System.currentTimeMillis()
        val state = _state.value
        val snapshots = TrendingSource.entries.associateWith { source ->
            val item = cache[state.options.getValue(source).queryKey(source)] ?: TrendingSnapshot()
            val limit = limits[source]
            item.copy(failure = limit?.failure ?: item.failure, retryAt = limit?.next ?: item.retryAt)
        }
        val context = sharedQueryKey(state.sources, state.options)
        var shared = cache[context]
        val revision = state.sources.mapNotNull { source -> snapshots[source]?.updatedAt?.let { source to it } }.toMap()
        // Keep the last complete intersection while any source is refreshing or has failed.
        if (cacheLoaded && state.sources.all { snapshots[it]?.updatedAt != null && snapshots[it]?.loading == false && snapshots[it]?.failure == null }
            && revision != shared?.sourceDates) {
            val rows = TrendingRules.intersection(state.sources, snapshots.mapValues { it.value.tokens })
            shared = TrendingSnapshot(tokens = rows, updatedAt = revision.values.maxOrNull(),
                newDates = TrendingRules.newDates(rows.map { it.id }, shared?.tokens?.map { it.id }, shared?.newDates.orEmpty(), now),
                sourceDates = revision)
            cache[context] = shared
            snapshotCache.put(context, shared.toCached())
        }
        _state.update { it.copy(snapshots = snapshots, now = now, sharedSnapshot = shared, cacheRestoring = !cacheLoaded) }
    }

    private fun setLimit(source: TrendingSource, limit: Limit) {
        limits[source] = limit
        snapshotCache.putLimit(source, CachedTrendingLimit(limit.next, limit.failure?.name, limit.blocked))
    }
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TrendingViewModel(container.trendingClient, container.trendingPreferences,
                container.gmgnCredentialsRepository, container.okxWalletCredentialsRepository, container.trendingSnapshotCache) }
        }
    }
}

internal fun sharedQueryKey(sources: Set<TrendingSource>, options: Map<TrendingSource, TrendingOptions>): String =
    "SHARED:" + TrendingRules.priority.filter { it in sources }.joinToString("|") { options.getValue(it).queryKey(it) }

private fun CachedTrendingSnapshot.toUi() = TrendingSnapshot(tokens = tokens, updatedAt = updatedAt,
    newDates = newDates, sourceDates = sourceDates)

private fun TrendingSnapshot.toCached() = CachedTrendingSnapshot(tokens, requireNotNull(updatedAt), newDates, sourceDates)
