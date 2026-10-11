package io.baiyanwu.coinmonitor.ui.trending

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.TrendingSource
import io.baiyanwu.coinmonitor.domain.model.TrendingToken
import io.baiyanwu.coinmonitor.domain.model.TrendingValue
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import java.util.Locale

@Composable
internal fun TrendingTokenRow(
    token: TrendingToken, rank: Int, isNew: Boolean, value: TrendingValue,
    showSmartMoney: Boolean, onToken: (TrendingToken) -> Unit, onCopyAddress: (String) -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = design.sizes.listRowMinimumHeight)
            .clip(design.shapes.card)
            .then(if (rank == 1) Modifier.background(
                Brush.horizontalGradient(listOf(design.colors.selectedContainer, design.colors.surface))
            ) else Modifier)
            .clickable { onToken(token) }
            .padding(horizontal = design.spacing.inline, vertical = design.spacing.tiny)
    ) {
        // 大字体/窄屏把数值放到下一行，避免固定图标和复制按钮挤掉币种名称。
        val stackedValues = fontScale > 1.2f || maxWidth < 280.dp
        Column {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
            ) {
                Text(
                    rank.toString().padStart(2, '0'), Modifier.width(design.sizes.rankWidth),
                    style = design.typography.caption,
                    color = if (rank == 1) design.colors.selectedContent else design.colors.secondaryText
                )
                TrendingTokenIcon(token)
                TrendingTokenIdentity(token, isNew, showSmartMoney, Modifier.weight(1f))
                IconButton(
                    onClick = { onCopyAddress(token.address) },
                    modifier = Modifier.size(design.sizes.minimumTouchTarget)
                ) {
                    Icon(
                        CoinMonitorIcons.Copy, stringResource(R.string.trending_copy_address),
                        Modifier.size(design.sizes.inlineActionIcon), tint = design.colors.secondaryText
                    )
                }
                if (!stackedValues) Column(Modifier.width(design.sizes.trendingValueColumnWidth), horizontalAlignment = Alignment.End) {
                    TrendingTokenValue(token, value)
                    TrendingTokenChange(token)
                }
            }
            if (stackedValues) Row(
                Modifier.fillMaxWidth().padding(
                    start = design.sizes.rankWidth + design.sizes.coinIcon + design.spacing.inline * 2,
                    bottom = design.spacing.small
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
            ) {
                TrendingTokenValue(token, value, Modifier.weight(1f))
                TrendingTokenChange(token, Modifier.weight(1f), TextAlign.End)
            }
        }
    }
}

@Composable
private fun TrendingTokenIdentity(token: TrendingToken, isNew: Boolean, showSmartMoney: Boolean, modifier: Modifier) {
    val design = CoinMonitorThemeTokens.design
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.tiny)) {
            Text(
                token.symbol, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = design.typography.rowTitle, color = design.colors.primaryText
            )
            if (showSmartMoney) TrendingSmartMoney(token.smartMoney)
            TrendingSourceDots(token.links.keys)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.small)) {
            Text(
                token.address.take(4) + "…" + token.address.takeLast(4), Modifier.weight(1f),
                style = design.typography.caption, color = design.colors.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (isNew) Text("NEW", style = design.typography.caption, color = design.colors.accent)
            if (token.risk) Text(stringResource(R.string.trending_risk), style = design.typography.caption, color = design.colors.error)
        }
    }
}

@Composable
private fun TrendingTokenValue(token: TrendingToken, value: TrendingValue, modifier: Modifier = Modifier) {
    val design = CoinMonitorThemeTokens.design
    Text(
        if (value == TrendingValue.MARKET_CAP) compactUsd(token.marketCap) else priceUsd(token.price), modifier,
        style = design.typography.value.copy(fontFeatureSettings = "tnum"), color = design.colors.primaryText
    )
}

@Composable
private fun TrendingTokenChange(token: TrendingToken, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val design = CoinMonitorThemeTokens.design
    val change = token.change?.takeIf(Double::isFinite)
    if (change != null) Text(
        String.format(Locale.US, "%+.2f%%", change), modifier, style = design.typography.caption, textAlign = textAlign,
        color = when {
            change > 0 -> design.colors.positive
            change < 0 -> design.colors.negative
            else -> design.colors.secondaryText
        }
    ) else Text(
        stringResource(R.string.trending_liquidity_short, compactUsd(token.liquidity)), modifier,
        style = design.typography.caption, color = design.colors.secondaryText, textAlign = textAlign
    )
}

@Composable
internal fun TrendingSourceDots(sources: Set<TrendingSource>, prominent: Boolean = false) {
    val design = CoinMonitorThemeTokens.design
    val description = sources.joinToString { it.title }
    Row(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(design.spacing.tiny), verticalAlignment = Alignment.CenterVertically
    ) {
        TrendingSource.entries.filter { it in sources }.forEach { source ->
            Box(
                Modifier.size(if (prominent) design.sizes.sourceSummaryDot else design.sizes.sourceDot)
                    .background(when (source) {
                        TrendingSource.GMGN -> design.colors.sourceGmgn
                        TrendingSource.PUMP -> design.colors.sourcePump
                        TrendingSource.OKX -> design.colors.sourceOkx
                    }, design.shapes.pill)
            )
        }
    }
}

@Composable
internal fun TrendingTokenIcon(token: TrendingToken) {
    val design = CoinMonitorThemeTokens.design
    val chainLogo = when (token.chainIndex) {
        "501" -> R.drawable.trending_chain_sol
        "56" -> R.drawable.trending_chain_bsc
        "8453" -> R.drawable.trending_chain_base
        "1" -> R.drawable.trending_chain_eth
        "42161" -> R.drawable.trending_chain_arbitrum
        "999" -> R.drawable.trending_chain_hyperevm
        "4663" -> R.drawable.trending_chain_robinhood
        "5042" -> R.drawable.trending_chain_arc
        "988" -> R.drawable.trending_chain_stable
        else -> null
    }
    Box(Modifier.size(design.sizes.coinIcon)) {
        if (LocalInspectionMode.current) {
            Box(Modifier.fillMaxSize().background(design.colors.surfaceMuted, design.shapes.pill), contentAlignment = Alignment.Center) {
                Text(token.symbol.take(1), style = design.typography.label, color = design.colors.accent)
            }
        } else CoilCoinSymbolIcon(
            token.symbol, iconUrl = token.iconUrl, allowSymbolLookup = false, size = design.sizes.coinIcon,
            contentScale = ContentScale.Fit
        )
        val badge = Modifier.align(Alignment.BottomEnd).size(design.sizes.chainBadge)
            .background(design.colors.surface, design.shapes.pill).padding(design.spacing.tiny).clip(design.shapes.pill)
        if (chainLogo != null) {
            val context = LocalContext.current
            val pixels = with(LocalDensity.current) { (design.sizes.chainBadge - design.spacing.tiny * 2).roundToPx().coerceAtLeast(1) }
            val request = remember(context, chainLogo, pixels) {
                ImageRequest.Builder(context).data(chainLogo).size(pixels, pixels).precision(Precision.EXACT).crossfade(false).build()
            }
            AsyncImage(request, token.chainTitle, modifier = badge, contentScale = ContentScale.Fit)
        }
        else Icon(Icons.Rounded.Language, token.chainTitle, modifier = badge, tint = design.colors.secondaryText)
    }
}

@Composable
private fun TrendingSmartMoney(count: Double?) {
    val design = CoinMonitorThemeTokens.design
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.tiny)) {
        Icon(CoinMonitorIcons.SmartMoney, stringResource(R.string.trending_smart_money), Modifier.size(design.sizes.chainBadge), tint = design.colors.accent)
        Text(
            count?.takeIf(Double::isFinite)?.let { java.text.NumberFormat.getIntegerInstance(Locale.US).format(it) } ?: "--",
            style = design.typography.caption, color = design.colors.accent
        )
    }
}
