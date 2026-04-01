package io.baiyanwu.coinmonitor.domain.model

import kotlinx.serialization.Serializable

/**
 * K 线指标线型配置。
 *
 * 当前只负责在设置页展示与保存，首版不驱动图表真实线型。
 */
@Serializable
enum class IndicatorLineStyle(val label: String) {
    THIN("细线"),
    MEDIUM("中线")
}

/**
 * 柱体填充样式。
 *
 * 当前用于 VOL 与 MACD 设置页，首版只对柱体颜色生效，
 * 填充样式本身先保留为可持久化字段。
 */
@Serializable
enum class IndicatorFillStyle(val label: String) {
    SOLID("实心"),
    HOLLOW("空心")
}

/**
 * 颜色预设项。
 */
@Serializable
enum class IndicatorColorPreset(val label: String, val colorInt: Int) {
    YELLOW("黄", 0xFFF7C41D.toInt()),
    MAGENTA("洋红", 0xFFE046C2.toInt()),
    PURPLE("紫", 0xFF8D67D8.toInt()),
    GREEN("绿", 0xFF49D462.toInt()),
    PINK("粉", 0xFFC91D79.toInt()),
    CYAN("青", 0xFF77E1D3.toInt()),
    INDIGO("靛", 0xFFA9B1F9.toInt()),
    LIME("青柠", 0xFFD0E35A.toInt()),
    ORANGE("橙", 0xFFF27B53.toInt()),
    BLUE("蓝", 0xFF5A6BFF.toInt()),
    BULL_GREEN("多头绿", 0xFF35D08B.toInt()),
    BEAR_RED("空头红", 0xFFFF4D6D.toInt())
}

/**
 * 通用折线配置项。
 */
@Serializable
data class IndicatorLineConfig(
    val enabled: Boolean = true,
    val period: Int? = null,
    val lineStyle: IndicatorLineStyle = IndicatorLineStyle.THIN,
    val colorPreset: IndicatorColorPreset = IndicatorColorPreset.YELLOW
)

/**
 * 通用柱体样式配置项。
 */
@Serializable
data class IndicatorHistogramConfig(
    val fillStyle: IndicatorFillStyle = IndicatorFillStyle.SOLID,
    val colorPreset: IndicatorColorPreset = IndicatorColorPreset.BULL_GREEN
)

/**
 * MA 配置。
 */
@Serializable
data class MaIndicatorSettings(
    val lines: List<IndicatorLineConfig> = defaultMaLines()
)

/**
 * EMA 配置。
 */
@Serializable
data class EmaIndicatorSettings(
    val lines: List<IndicatorLineConfig> = defaultEmaLines()
)

/**
 * BOLL 配置。
 */
@Serializable
data class BollIndicatorSettings(
    val period: Int = 20,
    val width: Double = 2.0,
    val upperLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.YELLOW
    ),
    val middleLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.MAGENTA
    ),
    val lowerLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.PURPLE
    )
)

/**
 * VOL 配置。
 */
@Serializable
data class VolIndicatorSettings(
    val bullStyle: IndicatorFillStyle = IndicatorFillStyle.SOLID,
    val bearStyle: IndicatorFillStyle = IndicatorFillStyle.SOLID,
    val mavolLines: List<IndicatorLineConfig> = listOf(
        IndicatorLineConfig(
            enabled = true,
            period = 5,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = IndicatorColorPreset.YELLOW
        ),
        IndicatorLineConfig(
            enabled = true,
            period = 10,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = IndicatorColorPreset.PURPLE
        )
    )
)

/**
 * MACD 配置。
 */
@Serializable
data class MacdIndicatorSettings(
    val shortPeriod: Int = 12,
    val longPeriod: Int = 26,
    val signalPeriod: Int = 9,
    val difLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.YELLOW
    ),
    val deaLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.MAGENTA
    ),
    val bullGrowHistogram: IndicatorHistogramConfig = IndicatorHistogramConfig(
        fillStyle = IndicatorFillStyle.HOLLOW,
        colorPreset = IndicatorColorPreset.BULL_GREEN
    ),
    val bullShrinkHistogram: IndicatorHistogramConfig = IndicatorHistogramConfig(
        fillStyle = IndicatorFillStyle.SOLID,
        colorPreset = IndicatorColorPreset.BULL_GREEN
    ),
    val bearGrowHistogram: IndicatorHistogramConfig = IndicatorHistogramConfig(
        fillStyle = IndicatorFillStyle.HOLLOW,
        colorPreset = IndicatorColorPreset.BEAR_RED
    ),
    val bearShrinkHistogram: IndicatorHistogramConfig = IndicatorHistogramConfig(
        fillStyle = IndicatorFillStyle.SOLID,
        colorPreset = IndicatorColorPreset.BEAR_RED
    )
)

/**
 * RSI 配置。
 */
@Serializable
data class RsiIndicatorSettings(
    val lines: List<IndicatorLineConfig> = listOf(
        IndicatorLineConfig(
            enabled = true,
            period = 6,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = IndicatorColorPreset.YELLOW
        ),
        IndicatorLineConfig(
            enabled = false,
            period = 14,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = IndicatorColorPreset.MAGENTA
        ),
        IndicatorLineConfig(
            enabled = false,
            period = 24,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = IndicatorColorPreset.PURPLE
        )
    )
)

/**
 * KDJ 配置。
 */
@Serializable
data class KdjIndicatorSettings(
    val period: Int = 9,
    val kSmoothing: Int = 3,
    val dSmoothing: Int = 3,
    val kLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.YELLOW
    ),
    val dLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.MAGENTA
    ),
    val jLine: IndicatorLineConfig = IndicatorLineConfig(
        enabled = true,
        lineStyle = IndicatorLineStyle.THIN,
        colorPreset = IndicatorColorPreset.PURPLE
    )
)

/**
 * K 线指标设置聚合模型。
 */
@Serializable
data class KlineIndicatorSettings(
    val selectedMainIndicator: KlineIndicator = KlineIndicator.MA,
    val selectedSubIndicator: KlineIndicator = KlineIndicator.VOL,
    val ma: MaIndicatorSettings = MaIndicatorSettings(),
    val ema: EmaIndicatorSettings = EmaIndicatorSettings(),
    val boll: BollIndicatorSettings = BollIndicatorSettings(),
    val vol: VolIndicatorSettings = VolIndicatorSettings(),
    val macd: MacdIndicatorSettings = MacdIndicatorSettings(),
    val rsi: RsiIndicatorSettings = RsiIndicatorSettings(),
    val kdj: KdjIndicatorSettings = KdjIndicatorSettings()
)

/**
 * MA 默认 10 条均线配置。
 */
private fun defaultMaLines(): List<IndicatorLineConfig> {
    val defaults = listOf(
        5 to IndicatorColorPreset.YELLOW,
        10 to IndicatorColorPreset.MAGENTA,
        20 to IndicatorColorPreset.PURPLE,
        30 to IndicatorColorPreset.GREEN,
        60 to IndicatorColorPreset.PINK,
        120 to IndicatorColorPreset.CYAN,
        null to IndicatorColorPreset.INDIGO,
        null to IndicatorColorPreset.LIME,
        null to IndicatorColorPreset.ORANGE,
        null to IndicatorColorPreset.BLUE
    )
    return defaults.mapIndexed { index, (period, color) ->
        IndicatorLineConfig(
            enabled = index <= 5,
            period = period,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = color
        )
    }
}

/**
 * EMA 默认 10 条指数均线配置。
 */
private fun defaultEmaLines(): List<IndicatorLineConfig> {
    val defaults = listOf(
        7 to IndicatorColorPreset.YELLOW,
        25 to IndicatorColorPreset.MAGENTA,
        99 to IndicatorColorPreset.PURPLE,
        144 to IndicatorColorPreset.GREEN,
        169 to IndicatorColorPreset.PINK,
        200 to IndicatorColorPreset.CYAN,
        377 to IndicatorColorPreset.INDIGO,
        null to IndicatorColorPreset.LIME,
        null to IndicatorColorPreset.ORANGE,
        null to IndicatorColorPreset.BLUE
    )
    return defaults.mapIndexed { index, (period, color) ->
        IndicatorLineConfig(
            enabled = index <= 6,
            period = period,
            lineStyle = IndicatorLineStyle.THIN,
            colorPreset = color
        )
    }
}
