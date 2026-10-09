package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 业务调用图标含义，不在各页面维护独立的矢量或描边规则。 */
object CoinMonitorIcons {
    val Search get() = Icons.Rounded.Search
    val More get() = Icons.Rounded.MoreHoriz
    val Add get() = Icons.Rounded.Add
    val Close get() = Icons.Rounded.Close
    val Back get() = Icons.AutoMirrored.Rounded.ArrowBack

    val Market: ImageVector by lazy {
        roundedOutline("Market") {
            moveTo(2f, 13f); lineTo(6f, 13f)
            curveTo(7f, 13f, 7f, 6f, 8.5f, 6f)
            curveTo(10f, 6f, 10.5f, 20f, 12f, 20f)
            curveTo(13.5f, 20f, 14.5f, 3f, 16f, 3f)
            curveTo(17.5f, 3f, 18f, 13f, 19f, 13f)
            lineTo(22f, 13f)
        }
    }
    val Trending: ImageVector by lazy {
        roundedOutline("Trending") {
            moveTo(13f, 2.5f)
            curveTo(13.8f, 7.5f, 20f, 8f, 20f, 14f)
            curveTo(20f, 18.6f, 16.4f, 21.5f, 12f, 21.5f)
            curveTo(7.6f, 21.5f, 4f, 18.6f, 4f, 14f)
            curveTo(4f, 11.3f, 5.1f, 8.7f, 7.2f, 6.5f)
            curveTo(7.1f, 9.2f, 8.5f, 10f, 9.5f, 9.7f)
            curveTo(12.4f, 8.7f, 13.2f, 5.2f, 13f, 2.5f)
            close()
            moveTo(12f, 13f)
            curveTo(14.3f, 15f, 15.2f, 16.2f, 15.2f, 18f)
            curveTo(15.2f, 19.9f, 13.8f, 21.5f, 12f, 21.5f)
            curveTo(10.2f, 21.5f, 8.8f, 19.9f, 8.8f, 18f)
            curveTo(8.8f, 16.2f, 10.1f, 14.9f, 12f, 13f)
        }
    }
    val Wallet: ImageVector by lazy {
        roundedOutline("Wallet") {
            moveTo(5f, 7f); lineTo(19f, 7f)
            curveTo(20.1f, 7f, 21f, 7.9f, 21f, 9f)
            lineTo(21f, 19f); curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
            lineTo(5f, 21f); curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
            lineTo(3f, 9f); curveTo(3f, 7.9f, 3.9f, 7f, 5f, 7f); close()
            moveTo(8f, 7f); lineTo(8f, 4.5f)
            curveTo(8f, 3.7f, 8.7f, 3f, 9.5f, 3f)
            lineTo(14.5f, 3f); curveTo(15.3f, 3f, 16f, 3.7f, 16f, 4.5f); lineTo(16f, 7f)
            moveTo(17.8f, 14f)
            curveTo(17.8f, 15f, 16.2f, 15f, 16.2f, 14f)
            curveTo(16.2f, 13f, 17.8f, 13f, 17.8f, 14f); close()
        }
    }
    val Settings: ImageVector by lazy {
        roundedOutline("Settings") {
            moveTo(9.5f, 2f); lineTo(14.5f, 2f); lineTo(15.2f, 4.8f)
            lineTo(17.6f, 6.2f); lineTo(20.3f, 5.5f); lineTo(22f, 9.5f)
            lineTo(20f, 11f); lineTo(20f, 13f); lineTo(22f, 14.5f)
            lineTo(20.3f, 18.5f); lineTo(17.6f, 17.8f); lineTo(15.2f, 19.2f)
            lineTo(14.5f, 22f); lineTo(9.5f, 22f); lineTo(8.8f, 19.2f)
            lineTo(6.4f, 17.8f); lineTo(3.7f, 18.5f); lineTo(2f, 14.5f)
            lineTo(4f, 13f); lineTo(4f, 11f); lineTo(2f, 9.5f)
            lineTo(3.7f, 5.5f); lineTo(6.4f, 6.2f); lineTo(8.8f, 4.8f); close()
            moveTo(15.5f, 12f)
            curveTo(15.5f, 16.7f, 8.5f, 16.7f, 8.5f, 12f)
            curveTo(8.5f, 7.3f, 15.5f, 7.3f, 15.5f, 12f); close()
        }
    }
}

private fun roundedOutline(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw
        )
    }.build()
