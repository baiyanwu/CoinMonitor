package io.baiyanwu.coinmonitor.ui.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import io.baiyanwu.coinmonitor.domain.model.WalletActivity
import io.baiyanwu.coinmonitor.domain.model.WalletActivityDirection
import io.baiyanwu.coinmonitor.domain.model.WalletActivityStatus
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorCompactListRow
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorCompactActionButton
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorPageTopBar
import io.baiyanwu.coinmonitor.ui.components.OnchainChainIcon
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorListRow
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSegmentedTab
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSegmentedTabs
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorRoundedCard
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.format.AssetAmountFormatter
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.wallet.WalletHomeSummary
import io.baiyanwu.coinmonitor.ui.wallet.WalletContentTab
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.domain.model.WalletKind
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WalletHomeListContainer(
    state: LazyListState,
    tag: String,
    content: LazyListScope.() -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = design.spacing.pageHorizontal).padding(bottom = design.spacing.inline)
            .testTag(tag),
        shape = design.shapes.card, color = design.colors.surface, contentColor = design.colors.primaryText
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().testTag("$tag-list"),
            contentPadding = PaddingValues(vertical = design.spacing.inline),
            content = content
        )
    }
}

@Composable
internal fun WalletHomeHeader(onLock: () -> Unit, more: @Composable () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    CoinMonitorPageTopBar(stringResource(R.string.tab_wallet)) {
        CoinMonitorSmallIconButton(
            Icons.Rounded.Lock, stringResource(R.string.wallet_lock_now), onLock,
            containerColor = design.colors.surface
        )
        more()
    }
}

@Composable
internal fun WalletSelectionCard(
    wallet: WalletProfile,
    address: String?,
    onSelect: () -> Unit,
    onCopyAddress: (String) -> Unit,
    menu: @Composable () -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    Box {
        CoinMonitorRoundedCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            CoinMonitorCompactListRow(
                onClick = onSelect,
                leading = {
                    WalletHomeIcon(CoinMonitorIcons.Wallet)
                },
                headline = { Text(wallet.name, style = design.typography.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supporting = {
                    Text(
                        address?.let { if (it.length > 16) "${it.take(6)}…${it.takeLast(4)}" else it } ?: "—",
                        style = design.typography.rowSubtitle, color = design.colors.secondaryText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                trailing = {
                    if (address != null) CoinMonitorSmallIconButton(
                        Icons.Rounded.ContentCopy, stringResource(R.string.wallet_copy_address),
                        onClick = { onCopyAddress(address) }, containerColor = Color.Transparent
                    )
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.secondaryText)
                }
            )
        }
        menu()
    }
}

@Composable
internal fun WalletBalanceCard(
    summary: WalletHomeSummary,
    valuesVisible: Boolean,
    refreshing: Boolean,
    onToggleVisibility: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    actionsEnabled: Boolean
) {
    val design = CoinMonitorThemeTokens.design
    val configuration = LocalConfiguration.current
    val updatedLabel = remember(summary.updatedAtMillis, configuration) {
        summary.updatedAtMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    }
    CoinMonitorRoundedCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = design.spacing.cardPadding, vertical = design.spacing.inline)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wallet_visible_estimate), style = design.typography.rowSubtitle, color = design.colors.secondaryText)
                Text(
                    if (!valuesVisible) "••••" else summary.totalUsd?.let {
                        stringResource(R.string.wallet_watch_usd_value, AssetAmountFormatter.fiat(it))
                    } ?: "—",
                    style = design.typography.balance, color = design.colors.primaryText
                )
            }
            CoinMonitorSmallIconButton(
                if (valuesVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                stringResource(if (valuesVisible) R.string.wallet_hide_values else R.string.wallet_show_values),
                onToggleVisibility, containerColor = Color.Transparent
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
            Text(
                stringResource(R.string.wallet_home_summary, summary.networkCount, summary.assetCount),
                style = design.typography.caption, color = design.colors.secondaryText, modifier = Modifier.weight(1f)
            )
            updatedLabel?.let { updated ->
                Text(
                    stringResource(
                        if (refreshing) R.string.wallet_refreshing_previous else R.string.wallet_updated_time,
                        updated
                    ),
                    style = design.typography.caption, color = design.colors.secondaryText,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (summary.unpricedAssetCount > 0) Text(
            stringResource(R.string.wallet_unpriced_count, summary.unpricedAssetCount),
            style = design.typography.caption, color = design.colors.secondaryText
        )
        Spacer(Modifier.height(design.spacing.inline))
        Row(horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
            CoinMonitorCompactActionButton(
                stringResource(R.string.wallet_send), onSend, Modifier.weight(1f), enabled = actionsEnabled,
                leadingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                containerColor = design.colors.accent, contentColor = design.colors.onAccent
            )
            CoinMonitorCompactActionButton(
                stringResource(R.string.wallet_receive), onReceive, Modifier.weight(1f), enabled = actionsEnabled,
                leadingIcon = Icons.Rounded.Download
            )
        }
    }
}

@Composable
internal fun WalletHomeAssetRow(
    asset: SelfCustodyAsset,
    valuesVisible: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    val fontScale = LocalDensity.current.fontScale
    val estimate = if (!valuesVisible) "••••" else asset.valueUsd?.let {
        stringResource(R.string.wallet_watch_usd_value, AssetAmountFormatter.fiat(it))
    } ?: stringResource(R.string.wallet_price_unavailable)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = fontScale > 1.2f || maxWidth < design.sizes.walletStackedValueBreakpoint || estimate.length > 14
        CoinMonitorCompactListRow(
            onClick = onClick,
            enabled = asset.transferable,
            leading = {
                if (leadingIcon != null) leadingIcon() else CoilCoinSymbolIcon(symbol = asset.symbol, iconUrl = asset.logoUrl, size = design.sizes.coinIcon)
            },
            headline = {
                Column {
                    Text(asset.symbol, style = design.typography.compactRowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!asset.verified || !asset.transferable) Text(
                        stringResource(if (!asset.transferable) R.string.wallet_asset_not_transferable else R.string.wallet_unverified),
                        style = design.typography.compactRowSubtitle, color = design.colors.error
                    )
                }
            },
            supporting = {
                Text(asset.network.displayName, style = design.typography.compactRowSubtitle, color = design.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (valuesVisible) "${AssetAmountFormatter.token(asset.balance)} ${asset.symbol}" else "•••• ${asset.symbol}",
                    style = design.typography.compactRowSubtitle, color = design.colors.secondaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (stacked) Text(estimate, style = design.typography.value, color = design.colors.primaryText)
            },
            trailing = {
                if (!stacked) Column(Modifier.width(design.sizes.walletValueColumnWidth), horizontalAlignment = Alignment.End) {
                    Text(estimate, style = design.typography.value, color = design.colors.primaryText)
                }
                if (asset.transferable) Icon(CoinMonitorIcons.ChevronRight, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.secondaryText)
            }
        )
    }
}

@Composable
internal fun WalletHomeContentSelector(
    selectedTab: WalletContentTab, networkName: String,
    onSelect: (WalletContentTab) -> Unit, onNetwork: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPosition: (() -> Float)? = null
) {
    val design = CoinMonitorThemeTokens.design
    Row(
        modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
    ) {
        CoinMonitorSegmentedTabs(
            tabs = listOf(
                CoinMonitorSegmentedTab(WalletContentTab.ASSETS.name, stringResource(R.string.wallet_assets)),
                CoinMonitorSegmentedTab(WalletContentTab.ACTIVITY.name, stringResource(R.string.wallet_activity))
            ),
            selectedId = selectedTab.name, onSelect = { onSelect(WalletContentTab.valueOf(it)) },
            modifier = Modifier.weight(1.1f), compactTrack = true,
            indicatorPosition = indicatorPosition
        )
        CoinMonitorCompactActionButton(
            networkName, onNetwork, Modifier.weight(0.9f), leadingIcon = Icons.Rounded.Language,
            trailingIcon = CoinMonitorIcons.Expand, containerColor = design.colors.surface
        )
    }
}

@Composable
internal fun WalletHomeIcon(icon: ImageVector) {
    val design = CoinMonitorThemeTokens.design
    Box(
        Modifier.size(design.sizes.actionContainer).background(design.colors.surfaceMuted, design.shapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(design.sizes.tabIcon), tint = design.colors.accent)
    }
}

@Composable
internal fun WalletHomeWatchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        onClick = onClick,
        shape = design.shapes.pill,
        color = design.colors.surface,
        contentColor = design.colors.primaryText,
        modifier = modifier
            .padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.small)
            .fillMaxWidth().testTag("wallet-watch-entry")
    ) {
        Row(
            Modifier.heightIn(min = design.sizes.minimumTouchTarget)
                .padding(horizontal = design.spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
        ) {
            Box(
                Modifier.size(design.sizes.actionContainer)
                    .background(design.colors.selectedContainer, design.shapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Icon(CoinMonitorIcons.Search, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.accent)
            }
            Text(
                stringResource(R.string.wallet_watch_entry),
                modifier = Modifier.weight(1f),
                style = design.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(CoinMonitorIcons.ChevronRight, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.accent)
        }
    }
}

/** 主页网络筛选与收款网络共用布局；null 选中态表示直接进入的操作行。 */
@Composable
internal fun WalletHomeNetworkRow(
    name: String, onClick: () -> Unit, selected: Boolean? = null, onRemove: (() -> Unit)? = null,
    network: WalletNetwork? = null
) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        shape = design.shapes.menu,
        color = if (selected == true) design.colors.selectedContainer else design.colors.surface,
        contentColor = design.colors.primaryText
    ) {
        CoinMonitorListRow(
            leading = {
                if (network == null) WalletHomeIcon(Icons.Rounded.Language)
                else OnchainChainIcon(
                    chainIndex = if (network.isEvm) network.chainId?.toString()
                        else if (network.id == WalletNetwork.SOLANA.id) "501" else null,
                    name = network.displayName,
                    size = design.sizes.tabIcon
                )
            },
            title = { Text(name, style = design.typography.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supporting = {},
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected == true) Icon(CoinMonitorIcons.Check, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.selectedContent)
                    if (onRemove != null) CoinMonitorSmallIconButton(
                        CoinMonitorIcons.Close, stringResource(R.string.wallet_remove_network, name), onRemove,
                        containerColor = design.colors.surfaceMuted
                    )
                    if (selected == null) Icon(CoinMonitorIcons.ChevronRight, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.secondaryText)
                }
            },
            modifier = Modifier.clip(design.shapes.menu).then(
                if (selected == null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            )
        )
    }
}

@Composable
internal fun WalletHomeEmptyCard(text: String? = null, loading: Boolean = false) {
    val design = CoinMonitorThemeTokens.design
    CoinMonitorRoundedCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = design.sizes.walletEmptyBodyMinimumHeight),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
                if (loading) CircularProgressIndicator(Modifier.size(design.sizes.actionIcon), strokeWidth = 2.dp, color = design.colors.accent)
                if (text != null) Text(text, style = design.typography.rowSubtitle, color = design.colors.secondaryText)
            }
        }
    }
}

@Composable
internal fun WalletHomeMessageCard(
    body: String, modifier: Modifier = Modifier, title: String? = null, warning: Boolean = false,
    actionLabel: String? = null, onAction: (() -> Unit)? = null
) {
    val design = CoinMonitorThemeTokens.design
    CoinMonitorRoundedCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
            Icon(
                if (warning) CoinMonitorIcons.Security else CoinMonitorIcons.Info, null,
                Modifier.size(design.sizes.actionIcon), tint = if (warning) design.colors.error else design.colors.accent
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(design.spacing.small)) {
                if (title != null) Text(title, style = design.typography.rowTitle, color = if (warning) design.colors.error else design.colors.primaryText)
                Text(body, style = design.typography.caption, color = design.colors.secondaryText)
            }
        }
        if (onAction != null && actionLabel != null) TextButton(
            onClick = onAction, modifier = Modifier.align(Alignment.End).heightIn(min = design.sizes.minimumTouchTarget)
        ) { Text(actionLabel, style = design.typography.label) }
    }
}

@Composable
internal fun WalletHomeActivityRow(
    activity: WalletActivity, valuesVisible: Boolean, enabled: Boolean, onClick: () -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val dateLabel = remember(activity.timestampMillis, configuration) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(activity.timestampMillis))
    }
    val direction = stringResource(when (activity.direction) {
        WalletActivityDirection.INCOMING -> R.string.wallet_activity_received
        WalletActivityDirection.OUTGOING -> R.string.wallet_activity_sent
        WalletActivityDirection.SELF -> R.string.wallet_activity_self
        WalletActivityDirection.UNKNOWN -> R.string.wallet_activity_unknown
    })
    val status = stringResource(when (activity.status) {
        WalletActivityStatus.PENDING -> R.string.wallet_activity_pending
        WalletActivityStatus.CONFIRMED -> R.string.wallet_activity_confirmed
        WalletActivityStatus.FAILED -> R.string.wallet_activity_failed
        WalletActivityStatus.STALE -> R.string.wallet_activity_stale
    })
    val amount = if (valuesVisible) activity.amount?.let(AssetAmountFormatter::token) ?: "—" else "••••"
    val statusColor = when (activity.status) {
        WalletActivityStatus.FAILED -> design.colors.error
        WalletActivityStatus.PENDING -> design.colors.accent
        else -> design.colors.secondaryText
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = fontScale > 1.2f || maxWidth < design.sizes.walletStackedValueBreakpoint || amount.length > 14
        CoinMonitorCompactListRow(
            onClick = onClick, enabled = enabled,
            leading = {
                Box(Modifier.size(design.sizes.coinIcon), contentAlignment = Alignment.Center) {
                    Icon(
                        when (activity.direction) {
                            WalletActivityDirection.INCOMING -> Icons.Rounded.ArrowDownward
                            WalletActivityDirection.OUTGOING -> Icons.Rounded.ArrowUpward
                            WalletActivityDirection.SELF, WalletActivityDirection.UNKNOWN -> Icons.Rounded.SwapHoriz
                        }, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.secondaryText
                    )
                }
            },
            headline = { Text("$direction · ${activity.symbol}", style = design.typography.compactRowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supporting = {
                Text(
                    "${activity.network.displayName} · $dateLabel", style = design.typography.compactRowSubtitle,
                    color = design.colors.secondaryText, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis
                )
                if (stacked) {
                    Text(amount, style = design.typography.value, color = design.colors.primaryText)
                    Text(status, style = design.typography.compactRowSubtitle, color = statusColor)
                }
            },
            trailing = {
                if (!stacked) Column(Modifier.width(design.sizes.walletValueColumnWidth), horizontalAlignment = Alignment.End) {
                    Text(amount, style = design.typography.value, color = design.colors.primaryText)
                    Text(status, style = design.typography.compactRowSubtitle, color = statusColor)
                }
            }
        )
    }
}

@Preview(name = "钱包主页组件 · 浅色", widthDp = 360, showBackground = true)
@Composable
private fun WalletHomeLightPreview() = WalletHomePreview(false)

@Preview(name = "钱包主页组件 · 深色", widthDp = 360, showBackground = true)
@Composable
private fun WalletHomeDarkPreview() = WalletHomePreview(true)

@Preview(name = "钱包主页组件 · 大字体", widthDp = 320, fontScale = 1.5f, showBackground = true)
@Composable
private fun WalletHomeLargeFontPreview() = WalletHomePreview(false)

@Preview(name = "钱包主页组件 · 长金额与币名", widthDp = 320, showBackground = true)
@Composable
private fun WalletHomeLongValuePreview() = WalletHomePreview(false, longValue = true)

@Preview(name = "钱包主页组件 · 隐藏金额", widthDp = 360, showBackground = true)
@Composable
private fun WalletHomeHiddenValuePreview() = WalletHomePreview(false, valuesVisible = false)

@Composable
private fun WalletHomePreview(darkTheme: Boolean, longValue: Boolean = false, valuesVisible: Boolean = true) {
    CoinMonitorTheme(darkTheme = darkTheme) {
        CoinMonitorDesignTheme {
            val design = CoinMonitorThemeTokens.design
            val wallet = WalletProfile("preview", "主钱包", WalletKind.MNEMONIC, "0x7b000000000000000000000000000000000004e21", createdAtMillis = 0)
            Surface(color = design.colors.pageBackground) {
                Column(verticalArrangement = Arrangement.spacedBy(design.spacing.group)) {
                    WalletHomeHeader(onLock = {}) {
                        CoinMonitorSmallIconButton(CoinMonitorIcons.More, stringResource(R.string.wallet_more), {}, containerColor = design.colors.surface)
                    }
                    Column(Modifier.padding(horizontal = design.spacing.pageHorizontal), verticalArrangement = Arrangement.spacedBy(design.spacing.group)) {
                        WalletSelectionCard(wallet, wallet.evmAddress, onSelect = {}, onCopyAddress = {}, menu = {})
                        WalletBalanceCard(WalletHomeSummary(BigDecimal("12480.52"), 7, 2, 0, null), valuesVisible, false, {}, {}, {}, true)
                        WalletHomeContentSelector(WalletContentTab.ASSETS, stringResource(R.string.wallet_networks_all), {}, {})
                        val asset = SelfCustodyAsset(
                            "preview-sol", WalletNetwork.SOLANA, null, "Solana", "SOL", 9,
                            "30000000000", BigDecimal("30"), BigDecimal("148.5"), BigDecimal("4455"), null, true, true
                        )
                        CoinMonitorRoundedCard(contentPadding = PaddingValues(vertical = design.spacing.inline)) {
                            WalletHomeAssetRow(
                                if (longValue) asset.copy(symbol = "LONG_WRAPPED_TOKEN", valueUsd = BigDecimal("123456789012345.67")) else asset,
                                valuesVisible, leadingIcon = {
                                Icon(CoinMonitorIcons.Wallet, null, Modifier.size(design.sizes.coinIcon), tint = design.colors.accent)
                            }, onClick = {})
                        }
                        CoinMonitorRoundedCard(contentPadding = PaddingValues(vertical = design.spacing.inline)) {
                            WalletHomeActivityRow(
                                WalletActivity(
                                    id = "preview-activity", walletId = wallet.id, network = WalletNetwork.SOLANA, transactionHash = "preview-hash",
                                    direction = WalletActivityDirection.INCOMING, symbol = "SOL", amount = BigDecimal("1.25"),
                                    counterparty = null, timestampMillis = 0L, status = WalletActivityStatus.CONFIRMED, locallySubmitted = false
                                ), valuesVisible, enabled = true, onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "钱包选择面板与空状态 · 浅色", widthDp = 360, showBackground = true)
@Composable
private fun WalletHomeStatesPreview() {
    CoinMonitorTheme(darkTheme = false) {
        CoinMonitorDesignTheme {
            val design = CoinMonitorThemeTokens.design
            Column(
                Modifier.background(design.colors.pageBackground).padding(design.spacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(design.spacing.group)
            ) {
                WalletHomeNetworkRow(stringResource(R.string.wallet_networks_all), {}, selected = true)
                WalletHomeNetworkRow(WalletNetwork.ETHEREUM.displayName, {}, selected = false, onRemove = {}, network = WalletNetwork.ETHEREUM)
                WalletHomeNetworkRow(WalletNetwork.SOLANA.displayName, {}, network = WalletNetwork.SOLANA)
                WalletHomeEmptyCard(stringResource(R.string.wallet_no_assets))
                WalletHomeMessageCard(
                    title = stringResource(R.string.wallet_asset_provider_not_configured),
                    body = stringResource(R.string.wallet_asset_provider_not_configured_description),
                    actionLabel = stringResource(R.string.wallet_configure_network), onAction = {}
                )
            }
        }
    }
}
