package io.baiyanwu.coinmonitor.ui.kline.chart

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.HandleScaleOptions
import com.tradingview.lightweightcharts.api.options.models.HandleScrollOptions
import com.tradingview.lightweightcharts.api.options.models.PriceScaleMargins
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions
import com.tradingview.lightweightcharts.api.options.models.TimeScaleOptions
import com.tradingview.lightweightcharts.api.options.models.applyLineSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.candlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.chartOptions
import com.tradingview.lightweightcharts.api.options.models.histogramSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.layoutOptions
import com.tradingview.lightweightcharts.api.options.models.lineSeriesOptions
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.HistogramData
import com.tradingview.lightweightcharts.api.series.models.LineData
import com.tradingview.lightweightcharts.api.series.models.PriceFormat
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.view.ChartsView
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.IndicatorFillStyle
import io.baiyanwu.coinmonitor.domain.model.IndicatorLineStyle
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator

/**
 * 基于 TradingView Lightweight Charts 的 K 线渲染引擎。
 *
 * 当前主图和副图都改成“按配置驱动”的渲染模式，
 * 避免指标参数继续写死在图表引擎内部。
 */
class TradingViewKlineChartEngine(
    context: Context
) : KlineChartEngine {

    override val view: View
        get() = rootView

    private val rootView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val mainChartView = ChartsView(context)
    private val indicatorContainer = FrameLayout(context)
    private val indicatorChartView = ChartsView(context)
    private val indicatorTouchShield = View(context).apply {
        isClickable = true
        isFocusable = false
    }

    private val maSeries = MutableList(MAX_MA_LINE_COUNT) { null as SeriesApi? }
    private val emaSeries = MutableList(MAX_EMA_LINE_COUNT) { null as SeriesApi? }
    private val bollSeries = MutableList(BOLL_LINE_COUNT) { null as SeriesApi? }

    private var candleSeries: SeriesApi? = null
    private var indicatorHistogramSeries: SeriesApi? = null
    private var indicatorLinePrimarySeries: SeriesApi? = null
    private var indicatorLineSecondarySeries: SeriesApi? = null
    private var indicatorLineTertiarySeries: SeriesApi? = null

    private var isMainReady = false
    private var isIndicatorReady = false
    private var syncSubscribed = false
    private var released = false
    private var isIndicatorPaneActive = false
    private var pendingModel: KlineChartRenderModel? = null

    init {
        buildLayout()
        subscribeChartState()
    }

    override fun render(model: KlineChartRenderModel) {
        pendingModel = model
        renderIfPossible()
    }

    override fun release() {
        released = true
        pendingModel = null
    }

    /**
     * 把统一线宽映射成 TradingView 可识别的档位。
     */
    private fun resolveLineWidth(width: Int): LineWidth {
        return when {
            width <= 1 -> LineWidth.ONE
            else -> LineWidth.TWO
        }
    }

    private fun buildLayout() {
        rootView.addView(
            mainChartView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        indicatorContainer.addView(
            indicatorChartView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        indicatorContainer.addView(
            indicatorTouchShield,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        rootView.addView(
            indicatorContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(rootView.context, 122)
            )
        )
        indicatorContainer.visibility = View.GONE
    }

    /**
     * 监听两个图表的 ready 状态。
     */
    private fun subscribeChartState() {
        mainChartView.subscribeOnChartStateChange { state ->
            if (released || state !is ChartsView.State.Ready || isMainReady) return@subscribeOnChartStateChange
            isMainReady = true
            configureMainChart()
            createMainSeries()
            renderIfPossible()
        }
        indicatorChartView.subscribeOnChartStateChange { state ->
            if (released || state !is ChartsView.State.Ready || isIndicatorReady) return@subscribeOnChartStateChange
            isIndicatorReady = true
            configureIndicatorChart()
            createIndicatorSeries()
            bindVisibleRangeSync()
            renderIfPossible()
        }
    }

    private fun configureMainChart() {
        mainChartView.api.applyOptions(
            chartOptions {
                layout = layoutOptions { }
                handleScroll = HandleScrollOptions(
                    mouseWheel = true,
                    pressedMouseMove = true,
                    horzTouchDrag = true,
                    vertTouchDrag = false
                )
                handleScale = HandleScaleOptions(
                    mouseWheel = true,
                    pinch = true
                )
                rightPriceScale = PriceScaleOptions(
                    visible = true,
                    borderVisible = false
                )
                leftPriceScale = PriceScaleOptions(
                    visible = false
                )
                timeScale = TimeScaleOptions(
                    timeVisible = true,
                    secondsVisible = false,
                    borderVisible = false,
                    rightOffset = 6f
                )
            }
        )
    }

    private fun configureIndicatorChart() {
        indicatorChartView.api.applyOptions(
            chartOptions {
                layout = layoutOptions { }
                handleScroll = HandleScrollOptions(
                    mouseWheel = false,
                    pressedMouseMove = false,
                    horzTouchDrag = false,
                    vertTouchDrag = false
                )
                handleScale = HandleScaleOptions(
                    mouseWheel = false,
                    pinch = false
                )
                rightPriceScale = PriceScaleOptions(
                    visible = true,
                    borderVisible = false,
                    scaleMargins = PriceScaleMargins(
                        top = 0.15f,
                        bottom = 0.1f
                    )
                )
                leftPriceScale = PriceScaleOptions(
                    visible = false
                )
                timeScale = TimeScaleOptions(
                    visible = false,
                    borderVisible = false
                )
            }
        )
    }

    /**
     * 主图固定创建 1 组蜡烛 + 10 条 MA + 10 条 EMA + 3 条 BOLL。
     *
     * 这样能够覆盖首版配置页里的所有主图线条数量，同时避免运行期反复增删 series。
     */
    private fun createMainSeries() {
        val api = mainChartView.api
        val strokeStyle = KlineChartStyleDefaults.strokeStyle
        val maLineWidth = resolveLineWidth(strokeStyle.movingAverageLineWidth)
        val emaLineWidth = resolveLineWidth(strokeStyle.exponentialMovingAverageLineWidth)
        val bollLineWidth = resolveLineWidth(strokeStyle.bollLineWidth)

        api.addCandlestickSeries(candlestickSeriesOptions {
            lastValueVisible = true
            priceLineVisible = false
        }) {
            candleSeries = it
            renderIfPossible()
        }

        repeat(MAX_MA_LINE_COUNT) { index ->
            api.addLineSeries(lineSeriesOptions {
                lineWidth = maLineWidth
                lastValueVisible = false
                priceLineVisible = false
            }) {
                maSeries[index] = it
                renderIfPossible()
            }
        }

        repeat(MAX_EMA_LINE_COUNT) { index ->
            api.addLineSeries(lineSeriesOptions {
                lineWidth = emaLineWidth
                lastValueVisible = false
                priceLineVisible = false
            }) {
                emaSeries[index] = it
                renderIfPossible()
            }
        }

        repeat(BOLL_LINE_COUNT) { index ->
            api.addLineSeries(lineSeriesOptions {
                lineWidth = bollLineWidth
                lastValueVisible = false
                priceLineVisible = false
            }) {
                bollSeries[index] = it
                renderIfPossible()
            }
        }
    }

    private fun createIndicatorSeries() {
        val api = indicatorChartView.api
        val resolvedLineWidth = resolveLineWidth(KlineChartStyleDefaults.strokeStyle.macdSignalLineWidth)
        api.addHistogramSeries(histogramSeriesOptions {
            lastValueVisible = false
            priceLineVisible = false
            priceFormat = PriceFormat(type = PriceFormat.Type.VOLUME)
        }) {
            indicatorHistogramSeries = it
            renderIfPossible()
        }
        api.addLineSeries(lineSeriesOptions {
            lineWidth = resolvedLineWidth
            lastValueVisible = false
            priceLineVisible = false
        }) {
            indicatorLinePrimarySeries = it
            renderIfPossible()
        }
        api.addLineSeries(lineSeriesOptions {
            lineWidth = resolvedLineWidth
            lastValueVisible = false
            priceLineVisible = false
        }) {
            indicatorLineSecondarySeries = it
            renderIfPossible()
        }
        api.addLineSeries(lineSeriesOptions {
            lineWidth = resolvedLineWidth
            lastValueVisible = false
            priceLineVisible = false
        }) {
            indicatorLineTertiarySeries = it
            renderIfPossible()
        }
    }

    /**
     * 用主图时间范围驱动副图。
     */
    private fun bindVisibleRangeSync() {
        if (syncSubscribed || !isMainReady || !isIndicatorReady) return
        syncSubscribed = true
        mainChartView.api.timeScale.subscribeVisibleTimeRangeChange { range ->
            if (range == null || !isIndicatorPaneActive) return@subscribeVisibleTimeRangeChange
            runCatching {
                indicatorChartView.api.timeScale.setVisibleRange(range)
            }
        }
    }

    private fun renderIfPossible() {
        val model = pendingModel ?: return
        if (!isReadyToRender()) return
        applyPalette(model)
        applyMainChartData(model)
        applyIndicatorChartData(model)
        if (model.candles.isNotEmpty()) {
            mainChartView.api.timeScale.fitContent()
        }
        if (isIndicatorPaneActive) {
            indicatorChartView.api.timeScale.fitContent()
        }
    }

    private fun isReadyToRender(): Boolean {
        return isMainReady &&
            isIndicatorReady &&
            candleSeries != null &&
            maSeries.all { it != null } &&
            emaSeries.all { it != null } &&
            bollSeries.all { it != null } &&
            indicatorHistogramSeries != null &&
            indicatorLinePrimarySeries != null &&
            indicatorLineSecondarySeries != null &&
            indicatorLineTertiarySeries != null
    }

    private fun applyPalette(model: KlineChartRenderModel) {
        val palette = model.palette
        mainChartView.api.applyOptions(
            chartOptions {
                layout = layoutOptions {
                    background = SolidColor(palette.backgroundColor)
                    textColor = palette.textColor.toIntColor()
                }
                timeScale = TimeScaleOptions(
                    borderColor = palette.gridColor.toIntColor(),
                    timeVisible = true,
                    secondsVisible = false,
                    borderVisible = false,
                    rightOffset = 6f
                )
                rightPriceScale = PriceScaleOptions(
                    visible = true,
                    borderVisible = false,
                    borderColor = palette.gridColor.toIntColor(),
                    scaleMargins = PriceScaleMargins(top = 0.08f, bottom = 0.1f)
                )
            }
        )
        indicatorChartView.api.applyOptions(
            chartOptions {
                layout = layoutOptions {
                    background = SolidColor(palette.backgroundColor)
                    textColor = palette.textColor.toIntColor()
                }
                rightPriceScale = PriceScaleOptions(
                    visible = true,
                    borderVisible = false,
                    borderColor = palette.gridColor.toIntColor(),
                    scaleMargins = PriceScaleMargins(top = 0.15f, bottom = 0.08f)
                )
                timeScale = TimeScaleOptions(
                    visible = false,
                    borderVisible = false,
                    borderColor = palette.gridColor.toIntColor()
                )
            }
        )
    }

    private fun applyMainChartData(model: KlineChartRenderModel) {
        val palette = model.palette
        val settings = model.indicatorSettings
        candleSeries?.setData(
            model.candles.map { candle ->
                CandlestickData(
                    time = candle.toTradingViewTime(),
                    open = candle.open.toFloat(),
                    high = candle.high.toFloat(),
                    low = candle.low.toFloat(),
                    close = candle.close.toFloat(),
                    color = if (candle.close >= candle.open) palette.bullishColor.toIntColor() else palette.bearishColor.toIntColor(),
                    borderColor = if (candle.close >= candle.open) palette.bullishColor.toIntColor() else palette.bearishColor.toIntColor(),
                    wickColor = if (candle.close >= candle.open) palette.bullishColor.toIntColor() else palette.bearishColor.toIntColor()
                )
            }
        )

        maSeries.forEachIndexed { index, series ->
            val config = settings.ma.lines.getOrNull(index)
            series?.applyConfiguredLineStyle(config?.lineStyle)
            series?.setData(
                if (
                    model.mainIndicator == KlineIndicator.MA &&
                    config != null &&
                    config.enabled &&
                    (config.period ?: 0) > 0
                ) {
                    model.candles.toLineData(
                        values = KlineIndicatorCalculator.simpleMovingAverage(model.candles, config.period!!),
                        color = config.colorPreset.colorInt
                    )
                } else {
                    emptyList()
                }
            )
        }

        emaSeries.forEachIndexed { index, series ->
            val config = settings.ema.lines.getOrNull(index)
            series?.applyConfiguredLineStyle(config?.lineStyle)
            series?.setData(
                if (
                    model.mainIndicator == KlineIndicator.EMA &&
                    config != null &&
                    config.enabled &&
                    (config.period ?: 0) > 0
                ) {
                    model.candles.toLineData(
                        values = KlineIndicatorCalculator.exponentialMovingAverage(model.candles, config.period!!),
                        color = config.colorPreset.colorInt
                    )
                } else {
                    emptyList()
                }
            )
        }

        val boll = KlineIndicatorCalculator.bollingerBands(
            candles = model.candles,
            period = settings.boll.period.coerceAtLeast(1),
            multiplier = settings.boll.width
        )
        val bollConfigs = listOf(settings.boll.upperLine, settings.boll.middleLine, settings.boll.lowerLine)
        val bollValues = listOf(boll.upper, boll.middle, boll.lower)
        bollSeries.forEachIndexed { index, series ->
            val config = bollConfigs[index]
            series?.applyConfiguredLineStyle(config.lineStyle)
            series?.setData(
                if (model.mainIndicator == KlineIndicator.BOLL && config.enabled) {
                    model.candles.toLineData(
                        values = bollValues[index],
                        color = config.colorPreset.colorInt
                    )
                } else {
                    emptyList()
                }
            )
        }
    }

    private fun applyIndicatorChartData(model: KlineChartRenderModel) {
        val settings = model.indicatorSettings
        val shouldShowIndicatorPane = model.subIndicator in setOf(
            KlineIndicator.VOL,
            KlineIndicator.MACD,
            KlineIndicator.RSI,
            KlineIndicator.KDJ
        )
        isIndicatorPaneActive = shouldShowIndicatorPane
        indicatorContainer.visibility = if (shouldShowIndicatorPane) View.VISIBLE else View.GONE

        if (!shouldShowIndicatorPane) {
            clearIndicatorData()
            return
        }

        when (model.subIndicator) {
            KlineIndicator.VOL -> {
                indicatorHistogramSeries?.setData(
                    model.candles.map { candle ->
                        HistogramData(
                            time = candle.toTradingViewTime(),
                            value = candle.volume.toFloat(),
                            color = if (candle.close >= candle.open) {
                                settings.vol.bullStyle.applyHistogramStyle(model.palette.bullishColor).toIntColor()
                            } else {
                                settings.vol.bearStyle.applyHistogramStyle(model.palette.bearishColor).toIntColor()
                            }
                        )
                    }
                )
                val volumeValues = model.candles.map { it.volume }
                indicatorLinePrimarySeries?.applyConfiguredLineStyle(settings.vol.mavolLines.getOrNull(0)?.lineStyle)
                indicatorLinePrimarySeries?.setData(
                    settings.vol.mavolLines.getOrNull(0).toConfiguredLineData(model.candles, volumeValues)
                )
                indicatorLineSecondarySeries?.applyConfiguredLineStyle(settings.vol.mavolLines.getOrNull(1)?.lineStyle)
                indicatorLineSecondarySeries?.setData(
                    settings.vol.mavolLines.getOrNull(1).toConfiguredLineData(model.candles, volumeValues)
                )
                indicatorLineTertiarySeries?.setData(emptyList())
            }

            KlineIndicator.MACD -> {
                val macd = KlineIndicatorCalculator.macd(
                    candles = model.candles,
                    shortPeriod = settings.macd.shortPeriod.coerceAtLeast(1),
                    longPeriod = settings.macd.longPeriod.coerceAtLeast(1),
                    signalPeriod = settings.macd.signalPeriod.coerceAtLeast(1)
                )
                indicatorHistogramSeries?.setData(
                    model.candles.toMacdHistogramData(
                        values = macd.histogram,
                        previousValues = listOf(null) + macd.histogram.dropLast(1),
                        growPositiveColor = settings.macd.bullGrowHistogram.fillStyle.applyHistogramStyle(
                            settings.macd.bullGrowHistogram.colorPreset.colorInt
                        ),
                        shrinkPositiveColor = settings.macd.bullShrinkHistogram.fillStyle.applyHistogramStyle(
                            settings.macd.bullShrinkHistogram.colorPreset.colorInt
                        ),
                        growNegativeColor = settings.macd.bearGrowHistogram.fillStyle.applyHistogramStyle(
                            settings.macd.bearGrowHistogram.colorPreset.colorInt
                        ),
                        shrinkNegativeColor = settings.macd.bearShrinkHistogram.fillStyle.applyHistogramStyle(
                            settings.macd.bearShrinkHistogram.colorPreset.colorInt
                        )
                    )
                )
                indicatorLinePrimarySeries?.applyConfiguredLineStyle(settings.macd.difLine.lineStyle)
                indicatorLinePrimarySeries?.setData(
                    if (settings.macd.difLine.enabled) {
                        model.candles.toLineData(macd.dif, settings.macd.difLine.colorPreset.colorInt)
                    } else {
                        emptyList()
                    }
                )
                indicatorLineSecondarySeries?.applyConfiguredLineStyle(settings.macd.deaLine.lineStyle)
                indicatorLineSecondarySeries?.setData(
                    if (settings.macd.deaLine.enabled) {
                        model.candles.toLineData(macd.dea, settings.macd.deaLine.colorPreset.colorInt)
                    } else {
                        emptyList()
                    }
                )
                indicatorLineTertiarySeries?.setData(emptyList())
            }

            KlineIndicator.RSI -> {
                indicatorHistogramSeries?.setData(emptyList())
                val rsiLines = settings.rsi.lines
                indicatorLinePrimarySeries?.applyConfiguredLineStyle(rsiLines.getOrNull(0)?.lineStyle)
                indicatorLinePrimarySeries?.setData(
                    rsiLines.getOrNull(0).toConfiguredIndicatorData(model.candles) { period ->
                        KlineIndicatorCalculator.rsi(model.candles, period)
                    }
                )
                indicatorLineSecondarySeries?.applyConfiguredLineStyle(rsiLines.getOrNull(1)?.lineStyle)
                indicatorLineSecondarySeries?.setData(
                    rsiLines.getOrNull(1).toConfiguredIndicatorData(model.candles) { period ->
                        KlineIndicatorCalculator.rsi(model.candles, period)
                    }
                )
                indicatorLineTertiarySeries?.applyConfiguredLineStyle(rsiLines.getOrNull(2)?.lineStyle)
                indicatorLineTertiarySeries?.setData(
                    rsiLines.getOrNull(2).toConfiguredIndicatorData(model.candles) { period ->
                        KlineIndicatorCalculator.rsi(model.candles, period)
                    }
                )
            }

            KlineIndicator.KDJ -> {
                val kdj = KlineIndicatorCalculator.kdj(
                    candles = model.candles,
                    period = settings.kdj.period.coerceAtLeast(1),
                    kSmoothing = settings.kdj.kSmoothing.coerceAtLeast(1),
                    dSmoothing = settings.kdj.dSmoothing.coerceAtLeast(1)
                )
                indicatorHistogramSeries?.setData(emptyList())
                indicatorLinePrimarySeries?.applyConfiguredLineStyle(settings.kdj.kLine.lineStyle)
                indicatorLinePrimarySeries?.setData(
                    if (settings.kdj.kLine.enabled) {
                        model.candles.toLineData(kdj.k, settings.kdj.kLine.colorPreset.colorInt)
                    } else {
                        emptyList()
                    }
                )
                indicatorLineSecondarySeries?.applyConfiguredLineStyle(settings.kdj.dLine.lineStyle)
                indicatorLineSecondarySeries?.setData(
                    if (settings.kdj.dLine.enabled) {
                        model.candles.toLineData(kdj.d, settings.kdj.dLine.colorPreset.colorInt)
                    } else {
                        emptyList()
                    }
                )
                indicatorLineTertiarySeries?.applyConfiguredLineStyle(settings.kdj.jLine.lineStyle)
                indicatorLineTertiarySeries?.setData(
                    if (settings.kdj.jLine.enabled) {
                        model.candles.toLineData(kdj.j, settings.kdj.jLine.colorPreset.colorInt)
                    } else {
                        emptyList()
                    }
                )
            }

            else -> clearIndicatorData()
        }
    }

    private fun clearIndicatorData() {
        indicatorHistogramSeries?.setData(emptyList())
        indicatorLinePrimarySeries?.setData(emptyList())
        indicatorLineSecondarySeries?.setData(emptyList())
        indicatorLineTertiarySeries?.setData(emptyList())
    }

    companion object {
        private const val MAX_MA_LINE_COUNT = 10
        private const val MAX_EMA_LINE_COUNT = 10
        private const val BOLL_LINE_COUNT = 3
    }
}

/**
 * 把设置页中的线型选项映射成真实线宽。
 */
private fun SeriesApi.applyConfiguredLineStyle(style: IndicatorLineStyle?) {
    applyLineSeriesOptions {
        lineWidth = when (style ?: IndicatorLineStyle.THIN) {
            IndicatorLineStyle.THIN -> LineWidth.ONE
            IndicatorLineStyle.MEDIUM -> LineWidth.TWO
        }
    }
}

/**
 * 把柱体样式映射成真实渲染差异。
 *
 * 当前图表库没有直接提供空心柱体能力，首版通过透明度差异来表达。
 */
private fun IndicatorFillStyle.applyHistogramStyle(colorInt: Int): Int {
    val alpha = when (this) {
        IndicatorFillStyle.SOLID -> 0xFF
        IndicatorFillStyle.HOLLOW -> 0x66
    }
    return (colorInt and 0x00FFFFFF) or (alpha shl 24)
}

/**
 * 将折线配置映射成图表线数据。
 */
private fun io.baiyanwu.coinmonitor.domain.model.IndicatorLineConfig?.toConfiguredIndicatorData(
    candles: List<CandleEntry>,
    calculator: (Int) -> List<Double?>
): List<LineData> {
    val config = this ?: return emptyList()
    val period = config.period ?: return emptyList()
    if (!config.enabled || period <= 0) return emptyList()
    return candles.toLineData(
        values = calculator(period),
        color = config.colorPreset.colorInt
    )
}

/**
 * 将折线配置映射成基于任意原始数值序列的均线。
 */
private fun io.baiyanwu.coinmonitor.domain.model.IndicatorLineConfig?.toConfiguredLineData(
    candles: List<CandleEntry>,
    values: List<Double>
): List<LineData> {
    val config = this ?: return emptyList()
    val period = config.period ?: return emptyList()
    if (!config.enabled || period <= 0) return emptyList()
    return candles.toLineData(
        values = KlineIndicatorCalculator.simpleMovingAverageValues(values, period),
        color = config.colorPreset.colorInt
    )
}

/**
 * 把应用内 CandleEntry 映射成 TradingView 使用的 UTC 秒级时间。
 */
private fun CandleEntry.toTradingViewTime(): Time = Time.Utc(openTimeMillis / 1000)

/**
 * 把一组指标值映射成图表折线数据。
 */
private fun List<CandleEntry>.toLineData(
    values: List<Double?>,
    color: Int
): List<LineData> {
    return mapIndexedNotNull { index, candle ->
        val value = values.getOrNull(index) ?: return@mapIndexedNotNull null
        LineData(
            time = candle.toTradingViewTime(),
            value = value.toFloat(),
            color = color.toIntColor()
        )
    }
}

/**
 * 把 MACD 柱体映射成四色柱状图数据。
 */
private fun List<CandleEntry>.toMacdHistogramData(
    values: List<Double?>,
    previousValues: List<Double?>,
    growPositiveColor: Int,
    shrinkPositiveColor: Int,
    growNegativeColor: Int,
    shrinkNegativeColor: Int
): List<HistogramData> {
    return mapIndexedNotNull { index, candle ->
        val value = values.getOrNull(index) ?: return@mapIndexedNotNull null
        val previous = previousValues.getOrNull(index)
        val resolvedColor = when {
            value >= 0f && previous != null && value >= previous -> growPositiveColor
            value >= 0f -> shrinkPositiveColor
            previous != null && value <= previous -> growNegativeColor
            else -> shrinkNegativeColor
        }
        HistogramData(
            time = candle.toTradingViewTime(),
            value = value.toFloat(),
            color = resolvedColor.toIntColor()
        )
    }
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
