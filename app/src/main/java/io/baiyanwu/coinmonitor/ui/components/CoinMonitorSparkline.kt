package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Immutable
data class CoinMonitorChartPoint(val timestampMillis: Long, val value: Double)

/** 只绘制调用方提供的真实数据；不足两个有效点时不生成趋势。 */
@Composable
fun CoinMonitorSparkline(
    points: List<CoinMonitorChartPoint>,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val normalized = remember(points) { normalizeSparklinePoints(points) }
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        if (normalized.size < 2) return@Canvas
        val inset = 2.dp.toPx()
        val chartWidth = (size.width - inset * 2).coerceAtLeast(0f)
        val chartHeight = (size.height - inset * 2).coerceAtLeast(0f)
        val path = Path().apply {
            normalized.forEachIndexed { index, point ->
                val x = inset + point.x * chartWidth
                val y = inset + point.y * chartHeight
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

internal fun normalizeSparklinePoints(points: List<CoinMonitorChartPoint>): List<Offset> {
    val valid = points.filter { it.value.isFinite() && it.value > 0 && it.timestampMillis > 0 }
        .distinctBy { it.timestampMillis }.sortedBy { it.timestampMillis }
    if (valid.size < 2) return emptyList()
    val firstTime = valid.first().timestampMillis.toDouble()
    val timeRange = valid.last().timestampMillis.toDouble() - firstTime
    if (timeRange <= 0) return emptyList()
    val maxValue = valid.maxOf { it.value }
    val minRatio = valid.minOf { it.value } / maxValue
    val valueRange = 1.0 - minRatio
    return valid.map { point ->
        val x = (point.timestampMillis.toDouble() - firstTime) / timeRange
        val y = if (valueRange == 0.0) 0.5 else 1.0 - (point.value / maxValue - minRatio) / valueRange
        Offset(x.toFloat(), y.coerceIn(0.0, 1.0).toFloat())
    }
}
