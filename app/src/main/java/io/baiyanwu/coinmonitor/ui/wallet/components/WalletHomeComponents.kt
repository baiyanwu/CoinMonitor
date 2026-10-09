package io.baiyanwu.coinmonitor.ui.wallet.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorCompactListRow
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorRoundedCard
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.format.AssetAmountFormatter
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.wallet.WalletHomeSummary
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.domain.model.WalletKind
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WalletGroupedListItem(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    val zero = CornerSize(0.dp)
    val shape = design.shapes.card.copy(
        topStart = if (first) design.shapes.card.topStart else zero,
        topEnd = if (first) design.shapes.card.topEnd else zero,
        bottomStart = if (last) design.shapes.card.bottomStart else zero,
        bottomEnd = if (last) design.shapes.card.bottomEnd else zero
    )
    Surface(shape = shape, color = design.colors.surface, contentColor = design.colors.primaryText) {
        Column {
            content()
            if (!last) HorizontalDivider(Modifier.padding(horizontal = design.spacing.cardPadding), color = design.colors.divider)
        }
    }
}

@Composable
internal fun WalletHomeHeader(onLock: () -> Unit, more: @Composable () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    Row(
        Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.app_name), color = design.colors.accent, style = design.typography.rowTitle)
        Text("│", color = design.colors.divider, modifier = Modifier.padding(horizontal = design.spacing.inline))
        Text(stringResource(R.string.tab_wallet), style = design.typography.rowTitle)
        Spacer(Modifier.weight(1f))
        CoinMonitorSmallIconButton(Icons.Rounded.LockOpen, stringResource(R.string.wallet_lock_now), onLock)
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
                    Icon(CoinMonitorIcons.Wallet, null, Modifier.size(design.sizes.coinIcon), tint = design.colors.accent)
                },
                headline = { Text(wallet.name, style = design.typography.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supporting = {
                    Text(
                        address?.let { if (it.length > 16) "${it.take(6)}…${it.takeLast(4)}" else it } ?: "—",
                        style = design.typography.rowSubtitle, color = design.colors.secondaryText
                    )
                },
                trailing = {
                    if (address != null) CoinMonitorSmallIconButton(
                        Icons.Rounded.ContentCopy, stringResource(R.string.wallet_copy_address),
                        onClick = { onCopyAddress(address) }
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
    CoinMonitorRoundedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wallet_visible_estimate), style = design.typography.rowSubtitle, color = design.colors.secondaryText)
                Text(
                    if (!valuesVisible) "••••" else summary.totalUsd?.let {
                        stringResource(R.string.wallet_watch_usd_value, AssetAmountFormatter.fiat(it))
                    } ?: "—",
                    style = design.typography.balance
                )
            }
            CoinMonitorSmallIconButton(
                if (valuesVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                stringResource(if (valuesVisible) R.string.wallet_hide_values else R.string.wallet_show_values),
                onToggleVisibility
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
            Text(
                stringResource(R.string.wallet_home_summary, summary.networkCount, summary.assetCount),
                style = design.typography.caption, color = design.colors.secondaryText, modifier = Modifier.weight(1f)
            )
            summary.updatedAtMillis?.let { updated ->
                Text(
                    stringResource(
                        if (refreshing) R.string.wallet_refreshing_previous else R.string.wallet_updated_time,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updated))
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
            Button(
                onClick = onSend, enabled = actionsEnabled,
                modifier = Modifier.weight(1f).heightIn(min = design.sizes.minimumTouchTarget),
                shape = design.shapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = design.colors.accent, contentColor = design.colors.onAccent,
                    disabledContainerColor = design.colors.disabledContainer, disabledContentColor = design.colors.disabledContent
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(design.sizes.actionIcon))
                Text(stringResource(R.string.wallet_send), Modifier.padding(start = design.spacing.small), style = design.typography.label)
            }
            Button(
                onClick = onReceive, enabled = actionsEnabled,
                modifier = Modifier.weight(1f).heightIn(min = design.sizes.minimumTouchTarget),
                shape = design.shapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = design.colors.selectedContainer, contentColor = design.colors.selectedContent,
                    disabledContainerColor = design.colors.disabledContainer, disabledContentColor = design.colors.disabledContent
                )
            ) {
                Icon(Icons.Rounded.Download, null, Modifier.size(design.sizes.actionIcon))
                Text(stringResource(R.string.wallet_receive), Modifier.padding(start = design.spacing.small), style = design.typography.label)
            }
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
    CoinMonitorCompactListRow(
        onClick = onClick,
        enabled = asset.transferable,
        leading = {
            if (leadingIcon != null) leadingIcon() else CoilCoinSymbolIcon(symbol = asset.symbol, iconUrl = asset.logoUrl, size = design.sizes.coinIcon)
        },
        headline = {
            Row(horizontalArrangement = Arrangement.spacedBy(design.spacing.small)) {
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
        },
        trailing = {
            Text(
                if (!valuesVisible) "••••" else asset.valueUsd?.let {
                    stringResource(R.string.wallet_watch_usd_value, AssetAmountFormatter.fiat(it))
                } ?: stringResource(R.string.wallet_price_unavailable),
                style = design.typography.value
            )
            if (asset.transferable) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.secondaryText)
        }
    )
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

@Composable
private fun WalletHomePreview(darkTheme: Boolean) {
    CoinMonitorTheme(darkTheme = darkTheme) {
        CoinMonitorDesignTheme {
            val design = CoinMonitorThemeTokens.design
            val wallet = WalletProfile("preview", "主钱包", WalletKind.MNEMONIC, "0x7b000000000000000000000000000000000004e21", createdAtMillis = 0)
            Surface(color = design.colors.pageBackground) {
                Column(verticalArrangement = Arrangement.spacedBy(design.spacing.group)) {
                    WalletHomeHeader(onLock = {}) { CoinMonitorSmallIconButton(CoinMonitorIcons.More, "更多", {}) }
                    Column(Modifier.padding(horizontal = design.spacing.pageHorizontal), verticalArrangement = Arrangement.spacedBy(design.spacing.group)) {
                        WalletSelectionCard(wallet, wallet.evmAddress, onSelect = {}, onCopyAddress = {}, menu = {})
                        WalletBalanceCard(WalletHomeSummary(BigDecimal("12480.52"), 7, 2, 0, null), true, false, {}, {}, {}, true)
                        val asset = SelfCustodyAsset(
                            "preview-sol", WalletNetwork.SOLANA, null, "Solana", "SOL", 9,
                            "30000000000", BigDecimal("30"), BigDecimal("148.5"), BigDecimal("4455"), null, true, true
                        )
                        WalletGroupedListItem(first = true, last = true) {
                            WalletHomeAssetRow(asset, true, leadingIcon = {
                                Icon(CoinMonitorIcons.Wallet, null, Modifier.size(design.sizes.coinIcon), tint = design.colors.accent)
                            }, onClick = {})
                        }
                    }
                }
            }
        }
    }
}
