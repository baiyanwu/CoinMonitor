package io.baiyanwu.coinmonitor.ui.trending

import io.baiyanwu.coinmonitor.domain.model.TrendingBoard
import io.baiyanwu.coinmonitor.domain.model.TrendingChain
import io.baiyanwu.coinmonitor.domain.model.TrendingMode
import io.baiyanwu.coinmonitor.domain.model.TrendingRules
import io.baiyanwu.coinmonitor.domain.model.TrendingSource

/** 仅整理展示信息，不修改筛选、快照或请求策略。 */
internal data class TrendingBoardSummary(
    val sources: List<TrendingSource>,
    val chain: TrendingChain?,
    val mixedChains: Boolean,
    val period: String?,
    val mixedPeriods: Boolean,
    val mode: TrendingMode?,
    val updatedAt: Long?,
    val loading: Boolean,
    val missing: List<TrendingSource>,
    val failures: List<TrendingSource>
)

internal fun TrendingUiState.summary(board: TrendingBoard): TrendingBoardSummary {
    val demand = TrendingRules.demand(board, sources)
    val ordered = TrendingSource.entries.filter { it in demand }
    val chains = ordered.map { if (it == TrendingSource.PUMP) TrendingChain.SOLANA else options.getValue(it).chain }.distinct()
    // PumpFun 没有时间窗或热搜选项，不能给它伪造 24h/5m 标签。
    val configurable = ordered.filter { it != TrendingSource.PUMP }.map { options.getValue(it) }
    val periods = configurable.map { it.period }.distinct()
    val modes = configurable.map { it.mode }.distinct()
    return TrendingBoardSummary(
        sources = ordered,
        chain = chains.singleOrNull(),
        mixedChains = chains.size > 1,
        period = periods.singleOrNull(),
        mixedPeriods = periods.size > 1,
        mode = modes.singleOrNull(),
        // 刷新失败时使用正在展示的完整共同快照时间，避免把新来源时间当作旧榜单时间。
        updatedAt = if (board == TrendingBoard.SHARED) sharedSnapshot?.updatedAt
            ?: ordered.takeIf { required -> required.all { snapshots[it]?.updatedAt != null } }
                ?.mapNotNull { snapshots[it]?.updatedAt }?.minOrNull()
        else snapshots[ordered.first()]?.updatedAt,
        loading = ordered.any { snapshots[it]?.loading == true },
        missing = ordered.filter { it !in ready },
        failures = ordered.filter { it in ready && snapshots[it]?.failure != null }
    )
}
