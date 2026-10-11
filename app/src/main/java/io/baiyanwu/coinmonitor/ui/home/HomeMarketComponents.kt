package io.baiyanwu.coinmonitor.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketPageUrlResolver
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.withQuote
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorListRow
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorLogo
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSegmentedTab
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSegmentedTabs
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.components.compactContractAddress
import io.baiyanwu.coinmonitor.ui.resolveChangeColor
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlinx.coroutines.delay

@Composable
internal fun HomeHeader(onSearch: () -> Unit, more: @Composable () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    Row(
        Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
    ) {
        CoinMonitorLogo()
        Text(stringResource(R.string.app_name), style = design.typography.rowTitle, color = design.colors.accent)
        Box(Modifier.size(1.dp, 16.dp).background(design.colors.divider))
        Text(stringResource(R.string.home_self_selected), style = design.typography.label, color = design.colors.primaryText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        CoinMonitorSmallIconButton(CoinMonitorIcons.Search, stringResource(R.string.home_open_search), onSearch, containerColor = design.colors.surface)
        more()
    }
}

@Composable
internal fun HomeModeSelector(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    indicatorPosition: (() -> Float)? = null
) {
    val design = CoinMonitorThemeTokens.design
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal)) {
        CoinMonitorSegmentedTabs(
            tabs = listOf(
                CoinMonitorSegmentedTab("0", stringResource(R.string.search_mode_exchange)),
                CoinMonitorSegmentedTab("1", stringResource(R.string.search_mode_onchain))
            ),
            selectedId = selectedPage.toString(),
            onSelect = { onSelectPage(it.toInt()) },
            modifier = Modifier.widthIn(max = design.sizes.homeModeSelectorWidth * fontScale).fillMaxWidth(),
            compactTrack = true,
            indicatorPosition = indicatorPosition
        )
    }
}

@Composable
internal fun HomeOverlayStatus(enabled: Boolean, onConfigure: () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        onClick = onConfigure, shape = design.shapes.pill,
        color = design.colors.surface, contentColor = design.colors.primaryText,
        modifier = Modifier.padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.small)
            .fillMaxWidth().testTag("home-overlay-status")
    ) {
        Row(
            Modifier.heightIn(min = design.sizes.minimumTouchTarget).padding(horizontal = design.spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
        ) {
            Box(Modifier.size(design.sizes.actionContainer).background(design.colors.selectedContainer, design.shapes.pill), contentAlignment = Alignment.Center) {
                Icon(CoinMonitorIcons.Copy, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.accent)
            }
            Text(stringResource(R.string.home_overlay_monitor), style = design.typography.label)
            Box(Modifier.size(5.dp).background(if (enabled) design.colors.positive else design.colors.tertiaryText, CircleShape))
            Text(stringResource(if (enabled) R.string.home_overlay_enabled else R.string.home_overlay_disabled), style = design.typography.caption,
                color = if (enabled) design.colors.positive else design.colors.secondaryText, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.home_configure), style = design.typography.caption, color = design.colors.accent)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.accent)
        }
    }
}

@Composable
internal fun HomeWatchlistContainer(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        modifier = modifier.fillMaxSize().padding(
            start = design.spacing.pageHorizontal,
            top = design.spacing.inline,
            end = design.spacing.pageHorizontal,
            bottom = design.spacing.group
        ).testTag("home-watchlist-container"),
        shape = design.shapes.card,
        color = design.colors.surface,
        contentColor = design.colors.primaryText
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home-watchlist"),
            state = state,
            contentPadding = PaddingValues(vertical = design.spacing.inline),
            content = content
        )
    }
}

@Composable
internal fun HomeAssetRow(item: WatchItem, quoteRepository: QuoteRepository, showOnchainMarketCap: Boolean, overlaySelected: Boolean) {
    val design = CoinMonitorThemeTokens.design
    val resolvedItem = rememberHomeQuote(item, quoteRepository)
    val uriHandler = LocalUriHandler.current
    val marketPageUrl = remember(item) { MarketPageUrlResolver.resolve(item) }
    val openMarketLabel = stringResource(R.string.home_open_market_page, item.symbol)
    val contract = homeContractAddress(item)
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.home_copy_ca_description)
    val copiedMessage = stringResource(R.string.home_ca_copied)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        CoinMonitorListRow(
            leading = { HomeCoinIcon(item) }, title = { HomePairTitle(item) },
            supporting = {
                Column(verticalArrangement = Arrangement.spacedBy(design.spacing.tiny)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.small)) {
                        Text(homeExchangeLabel(item.exchangeSource), style = design.typography.caption, color = design.colors.secondaryText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (item.marketType == MarketType.CEX_USDT_FUTURES) Text(stringResource(R.string.market_tag_usdt_futures), style = design.typography.caption, color = design.colors.secondaryText)
                        if (item.homePinned) Icon(Icons.Rounded.PushPin, stringResource(R.string.home_pinned_tag), Modifier.size(12.dp), tint = design.colors.accent)
                        if (overlaySelected) Icon(Icons.Rounded.Layers, stringResource(R.string.home_overlay_tag), Modifier.size(12.dp), tint = design.colors.secondaryText)
                    }
                    contract?.let { Text(compactContractAddress(it), style = design.typography.caption, color = design.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            },
            identityModifier = if (marketPageUrl != null) Modifier.clickable(onClickLabel = openMarketLabel, role = Role.Button) {
                runCatching { uriHandler.openUri(marketPageUrl) }
            } else Modifier,
            middle = when {
                contract != null -> ({
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Contract address", contract))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(design.sizes.minimumTouchTarget)) {
                        Icon(Icons.Rounded.ContentCopy, copyLabel, Modifier.size(design.sizes.actionIcon), tint = design.colors.accent)
                    }
                })
                else -> null
            },
            trailing = {
                Column(Modifier.widthIn(max = minOf(128.dp, maxWidth * 0.4f)), horizontalAlignment = Alignment.End) {
                    HomeQuoteTexts(resolvedItem, showOnchainMarketCap)
                }
            }
        )
    }
}

@Composable
private fun HomeCoinIcon(item: WatchItem) {
    val preview = LocalInspectionMode.current
    val design = CoinMonitorThemeTokens.design
    CoilCoinSymbolIcon(symbol = item.baseSymbol, iconUrl = item.iconUrl.takeUnless { preview },
        fallbackIconUrls = if (preview) emptyList() else OnchainChainIconRegistry.resolveIconUrls(item.chainIndex), allowSymbolLookup = !preview,
        size = design.sizes.coinIcon)
}

@Composable
private fun HomePairTitle(item: WatchItem) {
    val design = CoinMonitorThemeTokens.design
    val label = homePairLabel(item)
    val title = buildAnnotatedString {
        append(label.substringBefore(" / "))
        if (label.contains(" / ")) withStyle(SpanStyle(color = design.colors.secondaryText)) { append(" / "); append(label.substringAfter(" / ")) }
    }
    Text(title, style = design.typography.rowTitle, color = design.colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun homeExchangeLabel(source: ExchangeSource): String = stringResource(when (source) {
    ExchangeSource.BINANCE -> R.string.exchange_badge_binance
    ExchangeSource.BINANCE_ALPHA -> R.string.exchange_badge_binance_alpha
    ExchangeSource.OKX -> R.string.exchange_badge_okx
    ExchangeSource.ONCHAIN -> R.string.exchange_badge_onchain
})

@Composable
private fun HomeQuoteTexts(item: WatchItem, showOnchainMarketCap: Boolean) {
    val design = CoinMonitorThemeTokens.design
    val colors = CoinMonitorThemeTokens.colors
    val marketCapMode = showOnchainMarketCap && item.marketType == MarketType.ONCHAIN_TOKEN
    var lastObservedQuoteAt by remember(item.id) { mutableStateOf(item.lastUpdatedAt) }
    var valueFlashing by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.lastUpdatedAt, marketCapMode) {
        val changed = lastObservedQuoteAt != item.lastUpdatedAt
        lastObservedQuoteAt = item.lastUpdatedAt
        valueFlashing = false
        if (changed && item.previousPrice != null && item.lastPrice != null && item.lastPrice != item.previousPrice) {
            valueFlashing = true
            delay(260L)
            valueFlashing = false
        }
    }
    val valueColor by animateColorAsState(
        if (valueFlashing) item.resolveLivePriceColor(colors, colors.primaryText) else colors.primaryText,
        tween(if (valueFlashing) 80 else 420), label = "home-value-flash"
    )
    if (marketCapMode) Text(stringResource(R.string.home_market_cap_label), style = design.typography.caption, color = colors.secondaryText)
    Text(formatHomeWatchValue(item, showOnchainMarketCap), style = design.typography.value,
        color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(design.spacing.small)) {
        Text(QuoteFormatter.formatChange(item.change24hPercent?.takeIf { it.isFinite() }), style = design.typography.rowSubtitle, color = item.resolveChangeColor(colors, colors.secondaryText))
    }
}

@Composable
private fun rememberHomeQuote(item: WatchItem, repository: QuoteRepository): WatchItem {
    val flow = remember(item.id, repository) { repository.observeQuote(item.id) }
    val quote by flow.collectAsStateWithLifecycle(initialValue = repository.getQuote(item.id))
    return item.withQuote(quote)
}

internal fun homeContractAddress(item: WatchItem): String? = item.tokenAddress?.trim()?.takeIf { item.marketType == MarketType.ONCHAIN_TOKEN && it.isNotEmpty() }

internal fun homePairLabel(item: WatchItem): String = when {
    item.symbol.contains("/") -> item.symbol.substringBefore("/").trim() + " / " + item.symbol.substringAfter("/").trim()
    item.marketType == MarketType.CEX_USDT_FUTURES && item.symbol.uppercase().endsWith("USDT") && item.symbol.length > 4 -> item.baseSymbol + " / USDT"
    else -> item.symbol
}

internal fun formatHomeWatchValue(item: WatchItem, showOnchainMarketCap: Boolean): String {
    val formatted = QuoteFormatter.formatWatchValue(item, showOnchainMarketCap)
    return if (formatted == "--" || formatted.startsWith("$")) formatted else "$$formatted"
}
