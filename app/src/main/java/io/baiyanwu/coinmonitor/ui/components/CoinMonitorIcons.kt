package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
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
    val Refresh get() = Icons.Rounded.Refresh
    val Filters get() = Icons.Rounded.Tune
    val Copy get() = Icons.Rounded.ContentCopy
    val Expand get() = Icons.Rounded.ExpandMore
    val Cycle get() = Icons.Rounded.Autorenew
    val SmartMoney get() = Icons.Rounded.Person
    val Check get() = Icons.Rounded.CheckCircle

    val ChevronRight: ImageVector by lazy {
        roundedOutline("ChevronRight", autoMirror = true) {
            moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
        }
    }
    val Preferences: ImageVector by lazy {
        roundedOutline("Preferences") {
            moveTo(3f, 7f); lineTo(6f, 7f); moveTo(10f, 7f); lineTo(21f, 7f)
            moveTo(3f, 17f); lineTo(14f, 17f); moveTo(18f, 17f); lineTo(21f, 17f)
            outlineCircle(8f, 7f, 2f); outlineCircle(16f, 17f, 2f)
        }
    }
    val Security: ImageVector by lazy {
        roundedOutline("Security") {
            moveTo(12f, 2.5f); lineTo(20f, 6f); lineTo(20f, 12f)
            curveTo(20f, 17f, 16.5f, 20f, 12f, 22f)
            curveTo(7.5f, 20f, 4f, 17f, 4f, 12f); lineTo(4f, 6f); close()
            moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 10f)
        }
    }
    val Overlay: ImageVector by lazy {
        roundedOutline("Overlay") {
            moveTo(5f, 4f); lineTo(19f, 4f)
            curveTo(20.1f, 4f, 21f, 4.9f, 21f, 6f); lineTo(21f, 15f)
            curveTo(21f, 16.1f, 20.1f, 17f, 19f, 17f); lineTo(5f, 17f)
            curveTo(3.9f, 17f, 3f, 16.1f, 3f, 15f); lineTo(3f, 6f)
            curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f); close()
            moveTo(12f, 17f); lineTo(12f, 21f); moveTo(8f, 21f); lineTo(16f, 21f)
        }
    }
    val DataSources: ImageVector by lazy {
        roundedOutline("DataSources") {
            moveTo(20f, 6f)
            curveTo(20f, 1.3f, 4f, 1.3f, 4f, 6f)
            curveTo(4f, 10.7f, 20f, 10.7f, 20f, 6f); close()
            moveTo(4f, 6f); lineTo(4f, 18f)
            curveTo(4f, 22.7f, 20f, 22.7f, 20f, 18f); lineTo(20f, 6f)
            moveTo(4f, 12f); curveTo(4f, 16.7f, 20f, 16.7f, 20f, 12f)
        }
    }
    val Info: ImageVector by lazy {
        roundedOutline("Info") {
            outlineCircle(12f, 12f, 9.5f)
            moveTo(12f, 11f); lineTo(12f, 17f)
            moveTo(12f, 7f); lineTo(12f, 7.2f)
        }
    }
    val LightMode: ImageVector by lazy {
        roundedOutline("LightMode") {
            outlineCircle(12f, 12f, 4f)
            moveTo(12f, 2f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(4f, 12f); moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(4.9f, 4.9f); lineTo(6.3f, 6.3f); moveTo(17.7f, 17.7f); lineTo(19.1f, 19.1f)
            moveTo(4.9f, 19.1f); lineTo(6.3f, 17.7f); moveTo(17.7f, 6.3f); lineTo(19.1f, 4.9f)
        }
    }
    val DarkMode: ImageVector by lazy {
        roundedOutline("DarkMode") {
            moveTo(10f, 3f)
            curveTo(4.5f, 4.2f, 1.8f, 10.5f, 4f, 15.6f)
            curveTo(7f, 22.5f, 17f, 22.2f, 20.5f, 15.6f)
            curveTo(12.5f, 18f, 6.7f, 10.8f, 10f, 3f); close()
        }
    }
    val SystemMode: ImageVector by lazy {
        roundedOutline("SystemMode") {
            outlineCircle(12f, 12f, 9.5f)
            moveTo(12f, 2.5f); lineTo(12f, 21.5f)
        }
    }

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

private fun roundedOutline(name: String, autoMirror: Boolean = false, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw
        )
    }.build()

private fun PathBuilder.outlineCircle(x: Float, y: Float, radius: Float) {
    val control = radius * 0.552285f
    moveTo(x + radius, y)
    curveTo(x + radius, y + control, x + control, y + radius, x, y + radius)
    curveTo(x - control, y + radius, x - radius, y + control, x - radius, y)
    curveTo(x - radius, y - control, x - control, y - radius, x, y - radius)
    curveTo(x + control, y - radius, x + radius, y - control, x + radius, y)
    close()
}
