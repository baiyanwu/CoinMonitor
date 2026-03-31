package io.baiyanwu.coinmonitor.ui.kline.chart

import android.content.Context
import android.view.View
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings

/**
 * K 线图渲染所需的主题颜色集合。
 *
 * 这里显式抽一层，是为了把页面主题和具体图表库隔离开，
 * 后续切换自研图表实现时可以直接复用同一份渲染输入。
 */
data class KlineChartPalette(
    val backgroundColor: Int,
    val textColor: Int,
    val gridColor: Int,
    val bullishColor: Int,
    val bearishColor: Int,
    val ma5Color: Int,
    val ma10Color: Int,
    val ma20Color: Int,
    val ema5Color: Int,
    val ema10Color: Int,
    val ema20Color: Int,
    val bollUpperColor: Int,
    val bollMiddleColor: Int,
    val bollLowerColor: Int,
    val auxiliaryPrimaryColor: Int,
    val auxiliarySecondaryColor: Int,
    val auxiliaryTertiaryColor: Int
)

/**
 * K 线图指标线条样式配置。
 *
 * 这里把主图和副图的折线粗细统一收口，避免线宽散落在具体图表引擎内部。
 * 后续如果迁移到自研 K 线库，可以继续沿用这份配置模型，快速完成视觉对齐。
 */
data class KlineChartStrokeStyle(
    val movingAverageLineWidth: Int,
    val exponentialMovingAverageLineWidth: Int,
    val bollLineWidth: Int,
    val macdSignalLineWidth: Int,
    val rsiLineWidth: Int,
    val kdjLineWidth: Int
)

/**
 * K 线样式默认值。
 *
 * 当前统一将 MA / EMA / MACD / RSI / KDJ 调整到与 BOLL 接近的细线表现，
 * 方便后续只改这一处就能全局生效。
 */
object KlineChartStyleDefaults {

    /**
     * 默认指标线宽配置。
     */
    val strokeStyle = KlineChartStrokeStyle(
        movingAverageLineWidth = 1,
        exponentialMovingAverageLineWidth = 1,
        bollLineWidth = 1,
        macdSignalLineWidth = 1,
        rsiLineWidth = 1,
        kdjLineWidth = 1
    )
}

/**
 * K 线图宿主传递给具体图表引擎的统一渲染模型。
 *
 * 这一层只表达“要画什么”，不表达“怎么画”，
 * 目的是保证后续替换成自研 K 线库时，业务层和页面层都不需要重做。
 */
data class KlineChartRenderModel(
    val candles: List<CandleEntry>,
    val mainIndicator: KlineIndicator,
    val subIndicator: KlineIndicator,
    val indicatorSettings: KlineIndicatorSettings,
    val palette: KlineChartPalette,
    val strokeStyle: KlineChartStrokeStyle
)

/**
 * K 线图引擎接口。
 *
 * 当前实现基于 TradingView Lightweight Charts，后续如需切换到自研库，
 * 只需要新增一个新的 Engine 实现并替换工厂即可。
 */
interface KlineChartEngine {
    val view: View

    /**
     * 根据统一渲染模型刷新图表内容。
     */
    fun render(model: KlineChartRenderModel)

    /**
     * 释放引擎内部持有的订阅和资源。
     */
    fun release()
}

/**
 * 当前应用使用的 K 线图引擎工厂。
 *
 * 这里单独保留一层工厂，方便未来把实现从第三方库切到自研库。
 */
object KlineChartEngineFactory {
    fun create(context: Context): KlineChartEngine = TradingViewKlineChartEngine(context)
}
