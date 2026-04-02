package io.baiyanwu.coinmonitor.domain.model

/**
 * 描述 AI 分析弹框中允许用户启停的分析输入项。
 */
enum class AiAnalysisOption(
    val sourceId: String? = null
) {
    INDICATOR_INFO,
    BINANCE_ANNOUNCEMENT("binance-announcements"),
    OKX_ANNOUNCEMENT("okx-announcements"),
    PROJECT_INFO("project-info");

    /**
     * 当前选项是否代表指标分析。
     */
    val isIndicator: Boolean
        get() = this == INDICATOR_INFO

    /**
     * 当前选项是否映射到某个 market source。
     */
    val isMarketSource: Boolean
        get() = sourceId != null

    companion object {
        /**
         * AI 弹框首次打开时的默认全选集合。
         */
        val defaultSelection: Set<AiAnalysisOption> = entries.toSet()
    }
}
