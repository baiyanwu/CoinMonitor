package io.baiyanwu.coinmonitor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.CoinIconService
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

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
    val context = LocalContext.current
    val iconService = remember(context) { CoinIconService.get(context) }
    val hasFallbackIcons = fallbackIconUrl != null || fallbackIconUrls.isNotEmpty()
    var bitmap by remember(symbol, iconUrl, fallbackIconUrl, fallbackIconUrls) {
        mutableStateOf(
            iconService.peekBitmap(
                symbol = symbol,
                preferredIconUrl = iconUrl,
                fallbackIconUrl = fallbackIconUrl,
                fallbackIconUrls = fallbackIconUrls,
                grayscaleFallback = hasFallbackIcons,
                allowSymbolLookup = allowSymbolLookup
            )
        )
    }
    val iconModifier = modifier.size(size)

    LaunchedEffect(symbol, iconUrl, fallbackIconUrl, fallbackIconUrls, allowSymbolLookup) {
        val loadedBitmap = iconService.loadBitmap(
            symbol = symbol,
            preferredIconUrl = iconUrl,
            fallbackIconUrl = fallbackIconUrl,
            fallbackIconUrls = fallbackIconUrls,
            grayscaleFallback = hasFallbackIcons,
            allowSymbolLookup = allowSymbolLookup
        )
        if (loadedBitmap != null) {
            bitmap = loadedBitmap
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = iconModifier.clip(CircleShape)
        )
    } else {
        GenericCoinPlaceholder(modifier = iconModifier)
    }
}

@Composable
fun CoilCoinSymbolIcon(
    symbol: String,
    iconUrl: String? = null,
    fallbackIconUrls: List<String> = emptyList(),
    allowSymbolLookup: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val context = LocalContext.current
    val iconService = remember(context) { CoinIconService.get(context) }
    var resolvedUrl by remember(symbol, allowSymbolLookup) { mutableStateOf<String?>(null) }
    var failedUrls by remember(symbol, iconUrl, fallbackIconUrls, allowSymbolLookup) {
        mutableStateOf(emptySet<String>())
    }

    LaunchedEffect(symbol, allowSymbolLookup) {
        resolvedUrl = if (allowSymbolLookup) iconService.resolveIconUrl(symbol) else null
    }

    val candidateUrls = remember(iconUrl, fallbackIconUrls, resolvedUrl) {
        coilIconCandidateUrls(iconUrl, fallbackIconUrls, resolvedUrl)
    }
    val iconModifier = modifier.size(size).clip(CircleShape)
    val url = candidateUrls.firstOrNull { it !in failedUrls }
    if (url == null) {
        GenericCoinPlaceholder(modifier = iconModifier)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(false).build(),
            contentDescription = null,
            modifier = iconModifier,
            contentScale = ContentScale.Crop,
            onError = { failedUrls = failedUrls + url }
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
