package io.baiyanwu.coinmonitor.ui.kline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.KlineSelectionStore
import io.baiyanwu.coinmonitor.domain.model.AiChatMessage
import io.baiyanwu.coinmonitor.domain.model.AiChatRole
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.KlineSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OpenAiCompatibleConfig
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.AiChatRepository
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketKlineRepository
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val FIXED_TEST_INTERVAL = KlineInterval.FOUR_HOURS

/**
 * K 线页状态。
 */
data class KlineUiState(
    val availableItems: List<WatchItem> = emptyList(),
    val filteredItems: List<WatchItem> = emptyList(),
    val availableSources: List<KlineSource> = emptyList(),
    val selectedSource: KlineSource? = null,
    val selectedItem: WatchItem? = null,
    val selectedInterval: KlineInterval = FIXED_TEST_INTERVAL,
    val selectedMainIndicator: KlineIndicator = KlineIndicator.MA,
    val selectedSubIndicator: KlineIndicator = KlineIndicator.VOL,
    val indicatorSettings: KlineIndicatorSettings = KlineIndicatorSettings(),
    val candles: List<CandleEntry> = emptyList(),
    val chatMessages: List<AiChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAiSending: Boolean = false,
    val aiReady: Boolean = false,
    val hasOkxCredentials: Boolean = false,
    val errorMessage: String? = null
)

/**
 * K 线页 ViewModel。
 *
 * 这里统一收口“标的选择、周期切换、K 线加载、AI 聊天”四类状态，
 * 页面层只消费最终 UI 状态，不直接依赖仓库细节。
 */
class KlineViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val marketKlineRepository: MarketKlineRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val aiChatRepository: AiChatRepository,
    private val okxCredentialsRepository: OkxCredentialsRepository,
    private val selectionStore: KlineSelectionStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(KlineUiState())
    val uiState: StateFlow<KlineUiState> = _uiState.asStateFlow()

    private val sourcePreference = MutableStateFlow<KlineSource?>(null)
    private val intervalPreference = MutableStateFlow(FIXED_TEST_INTERVAL)
    private val chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    private val aiSending = MutableStateFlow(false)
    private var lastLoadedRequestKey: CandleRequestKey? = null

    init {
        viewModelScope.launch {
            combine(
                watchlistRepository.observeWatchlist(),
                selectionStore.selectedItemId,
                sourcePreference,
                intervalPreference,
                appPreferencesRepository.observePreferences()
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val items = values[0] as List<WatchItem>
                val selectedItemId = values[1] as String?
                val source = values[2] as KlineSource?
                val interval = values[3] as KlineInterval
                val preferences = values[4] as AppPreferences
                PrimarySnapshot(
                    items = items,
                    selectedItemId = selectedItemId,
                    sourcePreference = source,
                    interval = interval,
                    mainIndicator = preferences.klineMainIndicator,
                    subIndicator = preferences.klineSubIndicator,
                    indicatorSettings = preferences.klineIndicatorSettings
                )
            }.combine(
                aiConfigRepository.observeConfig()
            ) { primary, aiConfig ->
                Pair(primary, aiConfig)
            }.combine(
                okxCredentialsRepository.observeCredentials()
            ) { (primary, aiConfig), okxCredentials ->
                Triple(primary, aiConfig, okxCredentials)
            }.combine(
                chatMessages
            ) { (primary, aiConfig, okxCredentials), messages ->
                SnapshotWithMessages(primary, aiConfig, okxCredentials.enabled && okxCredentials.isReady, messages)
            }.combine(
                aiSending
            ) { withMessages, isAiSending ->
                Snapshot(
                    items = withMessages.primary.items,
                    selectedItemId = withMessages.primary.selectedItemId,
                    sourcePreference = withMessages.primary.sourcePreference,
                    interval = withMessages.primary.interval,
                    mainIndicator = withMessages.primary.mainIndicator,
                    subIndicator = withMessages.primary.subIndicator,
                    indicatorSettings = withMessages.primary.indicatorSettings,
                    aiConfig = withMessages.aiConfig,
                    hasOkxCredentials = withMessages.hasOkxCredentials,
                    messages = withMessages.messages,
                    isAiSending = isAiSending
                )
            }.collectLatest { snapshot ->
                refreshSnapshot(snapshot)
            }
        }
    }

    /**
     * 切换行情来源。
     */
    fun setSource(source: KlineSource) {
        sourcePreference.value = source
    }

    /**
     * 切换周期。
     */
    fun setInterval(interval: KlineInterval) {
        intervalPreference.value = FIXED_TEST_INTERVAL
    }

    /**
     * 切换当前主图指标。
     */
    fun setMainIndicator(indicator: KlineIndicator) {
        viewModelScope.launch {
            appPreferencesRepository.setKlineMainIndicator(indicator)
        }
    }

    /**
     * 切换当前副图指标。
     */
    fun setSubIndicator(indicator: KlineIndicator) {
        viewModelScope.launch {
            appPreferencesRepository.setKlineSubIndicator(indicator)
        }
    }

    /**
     * 切换当前标的，并同步回共享选择仓库。
     */
    fun selectItem(itemId: String) {
        val item = _uiState.value.availableItems.firstOrNull { it.id == itemId } ?: return
        sourcePreference.value = KlineSource.fromWatchItem(item)
        selectionStore.select(itemId)
    }

    /**
     * 手动重试当前 K 线加载。
     */
    fun retry() {
        refreshNow()
    }

    /**
     * 用户主动下拉时重新走一次完整的 K 线加载流程。
     */
    fun refreshNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                refreshSnapshot(
                    snapshot = buildSnapshot(),
                    showFullscreenLoading = false,
                    forceReload = true
                )
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * 发送一条 AI 对话消息，并把当前 K 线上下文一起带给后端。
     */
    fun sendMessage(prompt: String) {
        val content = prompt.trim()
        if (content.isBlank()) return
        val state = _uiState.value
        val item = state.selectedItem ?: return
        if (!state.aiReady) {
            _uiState.update { it.copy(errorMessage = "请先在设置页完成 AI 配置") }
            return
        }
        val updatedMessages = chatMessages.value + AiChatMessage(
            role = AiChatRole.USER,
            content = content
        )
        chatMessages.value = updatedMessages
        aiSending.value = true
        viewModelScope.launch {
            runCatching {
                aiChatRepository.sendMessage(
                    item = item,
                    interval = state.selectedInterval,
                    indicators = setOf(state.selectedMainIndicator, state.selectedSubIndicator),
                    candles = state.candles,
                    messages = updatedMessages
                )
            }.onSuccess { reply ->
                chatMessages.value = updatedMessages + AiChatMessage(
                    role = AiChatRole.ASSISTANT,
                    content = reply
                )
            }.onFailure { throwable ->
                chatMessages.value = updatedMessages + AiChatMessage(
                    role = AiChatRole.ASSISTANT,
                    content = throwable.message ?: "AI 请求失败"
                )
            }
            aiSending.value = false
        }
    }

    /**
     * 根据最新快照重算最终 UI 状态，并负责拉取当前选中标的的 K 线数据。
     */
    private suspend fun refreshSnapshot(
        snapshot: Snapshot,
        showFullscreenLoading: Boolean = true,
        forceReload: Boolean = false
    ) {
        val currentState = _uiState.value
        val availableSources = snapshot.items
            .map(KlineSource::fromWatchItem)
            .distinct()
        val selectedById = snapshot.items.firstOrNull { it.id == snapshot.selectedItemId }
        val selectedSource = snapshot.sourcePreference
            ?: selectedById?.let(KlineSource::fromWatchItem)
            ?: availableSources.firstOrNull()
        val filteredItems = snapshot.items.filter { KlineSource.fromWatchItem(it) == selectedSource }
        val selectedItem = when {
            selectedById != null && KlineSource.fromWatchItem(selectedById) == selectedSource -> selectedById
            else -> filteredItems.firstOrNull()
        }
        val aiReady = snapshot.aiConfig.enabled && snapshot.aiConfig.isReady
        val requestKey = selectedItem?.let {
            CandleRequestKey(
                itemId = it.id,
                interval = snapshot.interval
            )
        }
        val canReuseLoadedCandles = !forceReload &&
            requestKey != null &&
            requestKey == lastLoadedRequestKey &&
            currentState.candles.isNotEmpty()

        _uiState.update {
            it.copy(
                availableItems = snapshot.items,
                filteredItems = filteredItems,
                availableSources = availableSources,
                selectedSource = selectedSource,
                selectedItem = selectedItem,
                selectedInterval = snapshot.interval,
                selectedMainIndicator = snapshot.mainIndicator,
                selectedSubIndicator = snapshot.subIndicator,
                indicatorSettings = snapshot.indicatorSettings,
                chatMessages = snapshot.messages,
                isAiSending = snapshot.isAiSending,
                aiReady = aiReady,
                hasOkxCredentials = snapshot.hasOkxCredentials,
                isLoading = showFullscreenLoading && selectedItem != null && !canReuseLoadedCandles,
                errorMessage = null
            )
        }

        if (selectedItem == null) {
            lastLoadedRequestKey = null
            _uiState.update {
                it.copy(
                    candles = emptyList(),
                    isLoading = false
                )
            }
            return
        }

        if (selectedItem.marketType == MarketType.ONCHAIN_TOKEN && !snapshot.hasOkxCredentials) {
            lastLoadedRequestKey = null
            _uiState.update {
                it.copy(
                    candles = emptyList(),
                    isLoading = false,
                    errorMessage = "链上 K 线需要先配置 OKX Onchain 凭证"
                )
            }
            return
        }

        if (canReuseLoadedCandles) {
            _uiState.update {
                it.copy(
                    isLoading = false
                )
            }
            return
        }

        runCatching {
            marketKlineRepository.fetchCandles(
                item = selectedItem,
                interval = snapshot.interval
            )
        }.onSuccess { candles ->
            lastLoadedRequestKey = requestKey
            _uiState.update {
                it.copy(
                    candles = candles,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }.onFailure { throwable ->
            lastLoadedRequestKey = null
            _uiState.update {
                it.copy(
                    candles = emptyList(),
                    isLoading = false,
                    errorMessage = throwable.message ?: "K 线加载失败"
                )
            }
        }
    }

    /**
     * 把当前内存态重新组装成一个可重试的完整快照。
     */
    private fun buildSnapshot(): Snapshot {
        return Snapshot(
            items = _uiState.value.availableItems,
            selectedItemId = _uiState.value.selectedItem?.id,
            sourcePreference = _uiState.value.selectedSource,
            interval = _uiState.value.selectedInterval,
            mainIndicator = _uiState.value.selectedMainIndicator,
            subIndicator = _uiState.value.selectedSubIndicator,
            indicatorSettings = _uiState.value.indicatorSettings,
            aiConfig = aiConfigRepository.getConfig(),
            hasOkxCredentials = okxCredentialsRepository.getCredentials().run { enabled && isReady },
            messages = chatMessages.value,
            isAiSending = aiSending.value
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                KlineViewModel(
                    watchlistRepository = container.watchlistRepository,
                    marketKlineRepository = container.marketKlineRepository,
                    appPreferencesRepository = container.appPreferencesRepository,
                    aiConfigRepository = container.aiConfigRepository,
                    aiChatRepository = container.aiChatRepository,
                    okxCredentialsRepository = container.okxCredentialsRepository,
                    selectionStore = container.klineSelectionStore
                )
            }
        }
    }

    private data class Snapshot(
        val items: List<WatchItem>,
        val selectedItemId: String?,
        val sourcePreference: KlineSource?,
        val interval: KlineInterval,
        val mainIndicator: KlineIndicator,
        val subIndicator: KlineIndicator,
        val indicatorSettings: KlineIndicatorSettings,
        val aiConfig: OpenAiCompatibleConfig,
        val hasOkxCredentials: Boolean,
        val messages: List<AiChatMessage>,
        val isAiSending: Boolean
    )

    private data class PrimarySnapshot(
        val items: List<WatchItem>,
        val selectedItemId: String?,
        val sourcePreference: KlineSource?,
        val interval: KlineInterval,
        val mainIndicator: KlineIndicator,
        val subIndicator: KlineIndicator,
        val indicatorSettings: KlineIndicatorSettings
    )

    private data class SnapshotWithMessages(
        val primary: PrimarySnapshot,
        val aiConfig: OpenAiCompatibleConfig,
        val hasOkxCredentials: Boolean,
        val messages: List<AiChatMessage>
    )

    /**
     * 当前 K 线请求的唯一键。
     *
     * 只要标的或周期没变，就可以直接复用上一轮已经成功加载的 candles，
     * 避免指标切换、AI 状态变化等无关事件重复触发网络请求。
     */
    private data class CandleRequestKey(
        val itemId: String,
        val interval: KlineInterval
    )
}
