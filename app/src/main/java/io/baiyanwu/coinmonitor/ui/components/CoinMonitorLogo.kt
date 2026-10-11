package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 页面与启动图标复用原圆环/上涨折线；品牌名已由相邻文字提供，不重复朗读。 */
@Composable
fun CoinMonitorLogo(modifier: Modifier = Modifier) {
    val design = CoinMonitorThemeTokens.design
    val artwork = ImageVector.vectorResource(R.drawable.ic_launcher_foreground)
    val ringColor = design.colors.logoRing
    val configs = remember(ringColor) {
        mapOf(LOGO_RING_PATH to object : VectorConfig {
            @Suppress("UNCHECKED_CAST")
            override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
                if (property == VectorProperty.Fill) SolidColor(ringColor) as T else defaultValue
        })
    }
    val painter = rememberVectorPainter(
        defaultWidth = artwork.defaultWidth,
        defaultHeight = artwork.defaultHeight,
        viewportWidth = artwork.viewportWidth,
        viewportHeight = artwork.viewportHeight,
        name = artwork.name,
        autoMirror = artwork.autoMirror
    ) { _, _ ->
        // 仅覆盖原圆环的填色，折线、圆点和所有路径继续直接读取同一份矢量。
        RenderVectorGroup(artwork.root, configs)
    }
    Box(modifier.size(design.sizes.brandMark).clipToBounds()) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // 原矢量 viewport 为 108，圆环直径为 68；裁掉自适应图标的留白，保持原轮廓。
                scaleX = LOGO_ARTWORK_SCALE
                scaleY = LOGO_ARTWORK_SCALE
            }
        )
    }
}

private const val LOGO_ARTWORK_SCALE = 108f / 68f
private const val LOGO_RING_PATH = "logo_ring"
