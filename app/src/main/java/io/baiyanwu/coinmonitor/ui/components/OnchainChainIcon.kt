package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry

/** 本地图标优先，其他链沿用已有的链图标来源；与代币自身图标分开显示。 */
@Composable
internal fun OnchainChainIcon(
    chainIndex: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val local = localChainIconResource(chainIndex)
    if (local != null) {
        val context = LocalContext.current
        val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
        val request = remember(context, local, pixels) { coinIconRequest(context, local, pixels) }
        AsyncImage(request, name, modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        val urls = remember(chainIndex) { OnchainChainIconRegistry.resolveIconUrls(chainIndex) }
        Box(modifier.size(size).semantics { contentDescription = name }) {
            CoilCoinSymbolIcon(
                symbol = name,
                iconUrl = urls.firstOrNull(),
                fallbackIconUrls = urls.drop(1),
                allowSymbolLookup = false,
                modifier = Modifier.fillMaxSize(),
                size = size,
                contentScale = ContentScale.Fit,
                placeholderContent = { CoinInitialPlaceholder(name, it, iconSize = size) }
            )
        }
    }
}

internal fun localChainIconResource(chainIndex: String?): Int? {
    val raw = chainIndex?.trim()
    val canonical = OnchainChainRegistry.find(raw)?.chainIndex
        ?: OnchainChainRegistry.findByDexScreenerId(raw)?.chainIndex ?: raw
    return when (canonical) {
        "501" -> R.drawable.trending_chain_sol
        "56" -> R.drawable.trending_chain_bsc
        "8453" -> R.drawable.trending_chain_base
        "1" -> R.drawable.trending_chain_eth
        "42161" -> R.drawable.trending_chain_arbitrum
        "999", "hyperevm" -> R.drawable.trending_chain_hyperevm
        "4663", "robinhood" -> R.drawable.trending_chain_robinhood
        "5042", "arc" -> R.drawable.trending_chain_arc
        "988", "stable" -> R.drawable.trending_chain_stable
        else -> null
    }
}
