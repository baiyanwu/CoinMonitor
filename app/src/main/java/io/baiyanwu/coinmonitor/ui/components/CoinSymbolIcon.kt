package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.CoinIconService
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision

private val iconFailures = CoinIconFailureCooldown()

@Composable
fun CoinSymbolIcon(
    symbol: String,
    iconUrl: String? = null,
    fallbackIconUrl: String? = null,
    fallbackIconUrls: List<String> = emptyList(),
    allowSymbolLookup: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val fallbacks = remember(fallbackIconUrl, fallbackIconUrls) {
        listOfNotNull(fallbackIconUrl) + fallbackIconUrls
    }
    // 旧调用接口只适配参数；所有 Compose 币种图片交给同一个图库加载。
    CoilCoinSymbolIcon(
        symbol, iconUrl, fallbacks, allowSymbolLookup, modifier, size,
        grayscaleFallback = fallbacks.isNotEmpty(), contentScale = ContentScale.Fit
    )
}

@Composable
fun CoilCoinSymbolIcon(
    symbol: String,
    iconUrl: String? = null,
    fallbackIconUrls: List<String> = emptyList(),
    allowSymbolLookup: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    grayscaleFallback: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var resolvedUrl by remember(symbol, allowSymbolLookup) { mutableStateOf<String?>(null) }
    var failedUrls by remember(symbol, iconUrl, fallbackIconUrls, allowSymbolLookup) {
        mutableStateOf(emptySet<String>())
    }

    val directUrls = remember(iconUrl, fallbackIconUrls) {
        coilIconCandidateUrls(iconUrl, fallbackIconUrls, null)
    }
    val directUrl = directUrls.firstOrNull { it !in failedUrls && iconFailures.canRequest(it) }
    val needsSymbolLookup = allowSymbolLookup && directUrl == null
    LaunchedEffect(context, symbol, needsSymbolLookup) {
        // 这里只查询符号对应 URL；显式图标和链回退均失败后才查，图片仍由 Coil 加载。
        resolvedUrl = if (needsSymbolLookup) CoinIconService.get(context).resolveIconUrl(symbol) else null
    }
    val iconModifier = modifier.size(size).clip(CircleShape)
    val url = directUrl ?: resolvedUrl?.takeIf { allowSymbolLookup && it !in failedUrls && iconFailures.canRequest(it) }
    if (url == null) {
        GenericCoinPlaceholder(modifier = iconModifier)
    } else {
        val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
        val request = remember(context, url, pixels) {
            ImageRequest.Builder(context).data(url).size(pixels, pixels).precision(Precision.EXACT).crossfade(false).build()
        }
        val background = CoinMonitorThemeTokens.colors.heroBackground
        val placeholder = remember(background) { ColorPainter(background) }
        val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = iconModifier,
            placeholder = placeholder, error = placeholder,
            contentScale = contentScale,
            colorFilter = gray.takeIf { grayscaleFallback && url != iconUrl && url in fallbackIconUrls },
            onSuccess = { iconFailures.clear(url) },
            onError = { error ->
                if (iconFailures.recordFailure(url, error.result.throwable)) {
                    failedUrls = failedUrls + url
                }
            }
        )
    }
}

internal fun coilIconCandidateUrls(
    preferredUrl: String?,
    fallbackUrls: List<String>,
    resolvedSymbolUrl: String?
): List<String> = buildList {
    preferredUrl?.takeIf(String::isNotBlank)?.let(::add)
    addAll(fallbackUrls.filter(String::isNotBlank))
    resolvedSymbolUrl?.takeIf(String::isNotBlank)?.let(::add)
}.distinct()

@Composable
fun CoinSymbolIcon(
    item: WatchItem,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    CoinSymbolIcon(
        symbol = item.baseSymbol,
        iconUrl = item.iconUrl,
        fallbackIconUrls = OnchainChainIconRegistry.resolveIconUrls(item.chainIndex),
        modifier = modifier,
        size = size
    )
}

@Composable
private fun GenericCoinPlaceholder(modifier: Modifier = Modifier) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.heroBackground,
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier
                .background(colors.heroBackground),
            contentAlignment = Alignment.Center
        ) {
            PlaceholderLayers()
        }
    }
}

@Composable
private fun BoxScope.PlaceholderLayers() {
    val colors = CoinMonitorThemeTokens.colors
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.22f))
            .align(Alignment.Center)
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.55f))
            .align(Alignment.Center)
    )
}
