package io.baiyanwu.coinmonitor.clipboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
internal fun ClipboardPanelView(
    state: ClipboardPanelState,
    settings: ClipboardSettings,
    watchlist: List<WatchItem>,
    anchor: () -> IntArray,
    onDismiss: () -> Unit,
    onRetryRead: () -> Unit,
    onFloatingChange: (Boolean, WatchItem?) -> Unit,
    onToggleWatchlist: (ClipboardMatch, WatchItem?) -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        Layout(content = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.selected == null) {
                        ClipboardPending(state, onRetryRead, onSettings)
                    } else {
                        ClipboardResult(
                            state = state,
                            settings = settings,
                            watchlist = watchlist,
                            onFloatingChange = onFloatingChange,
                            onToggleWatchlist = onToggleWatchlist,
                            onOpen = onOpen,
                            onSettings = onSettings
                        )
                    }
                }
            }
        }) { measurables, constraints ->
            val margin = 8.dp.roundToPx()
            val availableWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
            val availableHeight = (constraints.maxHeight - margin * 2).coerceAtLeast(0)
            // Use the current Android window constraints; compact screens keep their safety
            // margins while larger screens use a narrower, capped reading width.
            val measure = clipboardPanelMeasure(
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                fullWindowHeight = constraints.maxHeight,
                compactBreakpoint = 220.dp.roundToPx(),
                largeCardCap = 210.dp.roundToPx()
            )
            val child = measurables.single().measure(
                Constraints(minWidth = measure.width, maxWidth = measure.width, maxHeight = measure.maxHeight)
            )
            val coordinates = anchor()
            val position = clipboardPanelPosition(
                anchorX = coordinates[0] - margin,
                anchorTop = coordinates[1] - margin,
                anchorBottom = coordinates[2] - margin,
                width = child.width,
                height = child.height,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                gap = 4.dp.roundToPx()
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.place(position.x + margin, position.y + margin)
            }
        }
    }
}

@Composable
private fun ClipboardPending(
    state: ClipboardPanelState,
    onRetryRead: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.clipboard_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            CompactIconButton(Icons.Rounded.Settings, R.string.clipboard_settings, onSettings)
        }
        when {
            state.addresses.size > 1 -> {
                Text(
                    text = stringResource(R.string.clipboard_choose_address),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.addresses.forEach { candidate ->
                    TextButton(
                        onClick = { state.chooseAddress(candidate) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(shortClipboardAddress(candidate), fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }
            state.matches.size > 1 -> {
                Text(
                    text = stringResource(R.string.clipboard_choose_chain),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.matches.forEach { match ->
                        AssistChip(
                            onClick = { state.chooseChain(match) },
                            label = { Text(match.chain.displayName) },
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }
            }
            else -> {
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(state.message ?: R.string.clipboard_resolving),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!state.loading) {
                    TextButton(
                        onClick = { state.address?.let(state::chooseAddress) ?: onRetryRead() },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.clipboard_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardResult(
    state: ClipboardPanelState,
    settings: ClipboardSettings,
    watchlist: List<WatchItem>,
    onFloatingChange: (Boolean, WatchItem?) -> Unit,
    onToggleWatchlist: (ClipboardMatch, WatchItem?) -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    val match = requireNotNull(state.selected)
    val existing = watchlist.firstOrNull { it.semanticKey == match.watchItem(false).semanticKey }
    ClipboardSummary(
        state = state,
        match = match,
        existing = existing,
        addToFloating = settings.addToFloating,
        onFloatingChange = onFloatingChange,
        onToggleWatchlist = onToggleWatchlist
    )
    CompactDivider()
    ClipboardDestinations(
        destinations = settings.destinations.filter { it.enabled },
        match = match,
        links = match.links,
        onOpen = onOpen
    )
    CompactDivider()
    ClipboardChartSection(state, match)
    CompactDivider()
    Box(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 2.dp)) {
        CompactIconButton(
            icon = Icons.Rounded.Settings,
            label = R.string.clipboard_settings,
            onClick = onSettings,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ClipboardSummary(
    state: ClipboardPanelState,
    match: ClipboardMatch,
    existing: WatchItem?,
    addToFloating: Boolean,
    onFloatingChange: (Boolean, WatchItem?) -> Unit,
    onToggleWatchlist: (ClipboardMatch, WatchItem?) -> Unit
) {
    val floatingChecked = existing?.overlaySelected ?: addToFloating
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = match.selection.tokenSymbol.uppercase(),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$${QuoteFormatter.formatPrice(match.selection.priceUsd)}",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = trendColor(match.selection.change24hPercent)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = compactClipboardNumber(match.marketCap),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (state.actionBusy) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp)
                }
            } else {
                CompactIconButton(
                    icon = if (existing == null) Icons.Rounded.Add else Icons.Rounded.Remove,
                    label = if (existing == null) R.string.clipboard_add_watch else R.string.clipboard_remove_watch,
                    onClick = { onToggleWatchlist(match, existing) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(22.dp)) {
            Text(
                text = shortClipboardAddress(match.selection.tokenAddress),
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HolderCount(state)
            ChainSelector(state, match)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Switch) { onFloatingChange(!floatingChecked, existing) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.clipboard_add_floating),
                modifier = Modifier.weight(1f),
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(checked = floatingChecked, onCheckedChange = null, modifier = Modifier.scale(0.52f))
        }
        state.actionMessage?.let {
            Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun HolderCount(state: ClipboardPanelState) {
    when {
        state.holdersLoading -> Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.2.dp)
        }
        state.holderCount != null -> {
            val count = requireNotNull(state.holderCount)
            Row(
                modifier = Modifier.padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PeopleOutline,
                    contentDescription = stringResource(R.string.clipboard_holders_description, count),
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = java.text.NumberFormat.getIntegerInstance().format(count),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChainSelector(state: ClipboardPanelState, selected: ClipboardMatch) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { if (state.matches.size > 1) expanded = true },
            modifier = Modifier.height(26.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            Text(
                selected.chain.displayName,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.matches.size > 1) Icon(Icons.Rounded.ExpandMore, null, Modifier.size(11.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.matches.forEach { match ->
                DropdownMenuItem(
                    text = { Text(match.chain.displayName) },
                    leadingIcon = if (match == selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { expanded = false; state.chooseChain(match) }
                )
            }
        }
    }
}

@Composable
private fun ClipboardDestinations(
    destinations: List<ClipboardDestination>,
    match: ClipboardMatch,
    links: List<Pair<String, String>>,
    onOpen: (String) -> Unit
) {
    var projectsOpen by remember { mutableStateOf(false) }
    val projectName = stringResource(R.string.clipboard_projects)
    val entries = remember(destinations, match, links, projectName) {
        buildList {
            destinations.forEach { add(DestinationEntry(it.id, it.name, it.url(match))) }
            if (links.isNotEmpty()) add(DestinationEntry("project", projectName, null))
        }
    }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
        entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { entry ->
                    Box(Modifier.weight(1f)) {
                        TextButton(
                            onClick = {
                                if (entry.id == "project") projectsOpen = true else entry.url?.let(onOpen)
                            },
                            enabled = entry.url != null || entry.id == "project",
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DestinationIcon(entry.id, match.chain.dexScreenerId)
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = entry.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 8.5.sp,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                        if (entry.id == "project") {
                            DropdownMenu(expanded = projectsOpen, onDismissRequest = { projectsOpen = false }) {
                                links.forEach { (name, url) ->
                                    DropdownMenuItem(
                                        text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = { projectsOpen = false; onOpen(url) }
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class DestinationEntry(val id: String, val name: String, val url: String?)

@Composable
private fun DestinationIcon(id: String, chainId: String) {
    val drawable = destinationIconResource(id, chainId)
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
        )
    } else {
        Icon(
            imageVector = if (id == "project") Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.Public,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun destinationIconResource(id: String, chainId: String): Int? = when (id) {
    "dex" -> R.drawable.clipboard_logo_dexscreener
    "gmgn" -> R.drawable.clipboard_logo_gmgn
    "okx" -> R.drawable.clipboard_logo_okx
    "binance" -> R.drawable.clipboard_logo_binance
    "x" -> R.drawable.clipboard_logo_x
    "fomo" -> R.drawable.clipboard_logo_fomo
    "explorer" -> when (chainId) {
        "ethereum" -> R.drawable.clipboard_logo_etherscan
        "bsc" -> R.drawable.clipboard_logo_bscscan
        "base" -> R.drawable.clipboard_logo_basescan
        "solana" -> R.drawable.clipboard_logo_solscan
        "robinhood" -> R.drawable.clipboard_logo_blockscout
        else -> null
    }
    else -> null
}

@Composable
private fun ClipboardChartSection(state: ClipboardPanelState, match: ClipboardMatch) {
    val change = match.selection.change24hPercent
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.clipboard_chart_period),
                modifier = Modifier.weight(1f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = QuoteFormatter.formatChange(change),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = trendColor(change)
            )
        }
        Spacer(Modifier.height(2.dp))
        when {
            state.chartLoading -> Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp)
            }
            state.chartUnavailable -> Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.clipboard_chart_unavailable),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> ClipboardChart(state.chart, change)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.clipboard_chart_ago),
                Modifier.weight(1f),
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.clipboard_chart_source),
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.clipboard_chart_now),
                Modifier.weight(1f),
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun ClipboardChart(points: List<ClipboardChartPoint>, change: Double?) {
    val color = trendColor(change)
    Canvas(Modifier.fillMaxWidth().height(38.dp)) {
        if (points.size < 2) return@Canvas
        val minimum = points.minOf { it.price }
        val range = (points.maxOf { it.price } - minimum).coerceAtLeast(minimum * 0.001)
        val duration = (points.last().timestamp - points.first().timestamp).coerceAtLeast(1.0)
        val line = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.timestamp - points.first().timestamp) / duration * size.width).toFloat()
            val y = (size.height - 5.dp.toPx() - ((point.price - minimum) / range * (size.height - 10.dp.toPx()))).toFloat()
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f))))
        drawPath(line, color = color, style = Stroke(width = 1.3.dp.toPx()))
    }
}

@Composable
private fun trendColor(change: Double?): Color {
    val colors = CoinMonitorThemeTokens.colors
    return when {
        change == null -> MaterialTheme.colorScheme.onSurface
        change >= 0 -> colors.positive
        else -> colors.negative
    }
}

@Composable
private fun CompactDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(28.dp)) {
        Icon(icon, stringResource(label), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun shortClipboardAddress(value: String): String =
    if (value.length > 20) "${value.take(8)}…${value.takeLast(6)}" else value
