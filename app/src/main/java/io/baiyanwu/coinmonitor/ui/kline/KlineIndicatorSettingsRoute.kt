package io.baiyanwu.coinmonitor.ui.kline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.BollIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.EmaIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.IndicatorColorPreset
import io.baiyanwu.coinmonitor.domain.model.IndicatorFillStyle
import io.baiyanwu.coinmonitor.domain.model.IndicatorHistogramConfig
import io.baiyanwu.coinmonitor.domain.model.IndicatorLineConfig
import io.baiyanwu.coinmonitor.domain.model.IndicatorLineStyle
import io.baiyanwu.coinmonitor.domain.model.KdjIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.MaIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.MacdIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.RsiIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.VolIndicatorSettings
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/**
 * K 线指标配置页路由入口。
 */
@Composable
fun KlineIndicatorSettingsRoute(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: KlineIndicatorSettingsViewModel = viewModel(
        factory = KlineIndicatorSettingsViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    KlineIndicatorSettingsScreen(
        state = state,
        onBack = onBack,
        onSaveIndicatorSettings = viewModel::saveIndicatorSettings
    )
}

/**
 * 指标设置页主界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KlineIndicatorSettingsScreen(
    state: KlineIndicatorSettingsUiState,
    onBack: () -> Unit,
    onSaveIndicatorSettings: (KlineIndicatorSettings) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    var editingIndicator by remember { mutableStateOf<IndicatorSheetType?>(null) }
    val settings = state.indicatorSettings
    val sections = remember(settings.selectedMainIndicator, settings.selectedSubIndicator) {
        listOf(
            IndicatorSection(
                title = R.string.kline_indicator_settings_main_title,
                items = listOf(
                    IndicatorMenuItem(KlineIndicator.MA, R.string.kline_indicator_ma_subtitle, settings.selectedMainIndicator == KlineIndicator.MA),
                    IndicatorMenuItem(KlineIndicator.EMA, R.string.kline_indicator_ema_subtitle, settings.selectedMainIndicator == KlineIndicator.EMA),
                    IndicatorMenuItem(KlineIndicator.BOLL, R.string.kline_indicator_boll_subtitle, settings.selectedMainIndicator == KlineIndicator.BOLL)
                )
            ),
            IndicatorSection(
                title = R.string.kline_indicator_settings_sub_title,
                items = listOf(
                    IndicatorMenuItem(KlineIndicator.VOL, R.string.kline_indicator_vol_subtitle, settings.selectedSubIndicator == KlineIndicator.VOL),
                    IndicatorMenuItem(KlineIndicator.MACD, R.string.kline_indicator_macd_subtitle, settings.selectedSubIndicator == KlineIndicator.MACD),
                    IndicatorMenuItem(KlineIndicator.RSI, R.string.kline_indicator_rsi_subtitle, settings.selectedSubIndicator == KlineIndicator.RSI),
                    IndicatorMenuItem(KlineIndicator.KDJ, R.string.kline_indicator_kdj_subtitle, settings.selectedSubIndicator == KlineIndicator.KDJ)
                )
            )
        )
    }

    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.kline_indicator_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.pageBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sections) { section ->
                IndicatorSectionBlock(
                    section = section,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    onItemClick = { indicator ->
                        editingIndicator = indicator.toSheetType()
                    }
                )
            }
        }
    }

    editingIndicator?.let { sheetType ->
        IndicatorBottomSheet(
            sheetType = sheetType,
            settings = settings,
            onDismiss = { editingIndicator = null },
            onConfirm = { updatedSettings ->
                onSaveIndicatorSettings(updatedSettings)
                editingIndicator = null
            }
        )
    }
}

/**
 * 指标分组块。
 */
@Composable
private fun IndicatorSectionBlock(
    section: IndicatorSection,
    modifier: Modifier = Modifier,
    onItemClick: (KlineIndicator) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(section.title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.secondaryText
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.cardBackground
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                section.items.forEachIndexed { index, item ->
                    IndicatorMenuRow(
                        item = item,
                        onClick = { onItemClick(item.indicator) }
                    )
                    if (index != section.items.lastIndex) {
                        HorizontalDivider(color = colors.divider.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

/**
 * 指标菜单项。
 */
@Composable
private fun IndicatorMenuRow(
    item: IndicatorMenuItem,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.indicator.displayLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.selected) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.chipSelectedContainer
                    ) {
                        Text(
                            text = stringResource(R.string.kline_indicator_selected),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.chipSelectedContent
                        )
                    }
                }
            }
            Text(
                text = stringResource(item.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = colors.tertiaryText
        )
    }
}

/**
 * 指标配置底部面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorBottomSheet(
    sheetType: IndicatorSheetType,
    settings: KlineIndicatorSettings,
    onDismiss: () -> Unit,
    onConfirm: (KlineIndicatorSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CoinMonitorThemeTokens.colors.cardBackground
    ) {
        when (sheetType) {
            IndicatorSheetType.MA -> {
                var draft by remember(settings) { mutableStateOf(settings.ma) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_ma_title),
                    onReset = { draft = MaIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedMainIndicator = KlineIndicator.MA,
                                ma = draft
                            )
                        )
                    }
                ) {
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_value),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    draft.lines.forEachIndexed { index, line ->
                        LineConfigEditorRow(
                            label = "MA${index + 1}",
                            config = line,
                            onConfigChange = { updated ->
                                draft = draft.copy(lines = draft.lines.toMutableList().apply { set(index, updated) })
                            }
                        )
                    }
                }
            }

            IndicatorSheetType.BOLL -> {
                var draft by remember(settings) { mutableStateOf(settings.boll) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_boll_title),
                    onReset = { draft = BollIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedMainIndicator = KlineIndicator.BOLL,
                                boll = draft
                            )
                        )
                    }
                ) {
                    NumberFieldRow(stringResource(R.string.kline_indicator_calc_period), draft.period.toString()) {
                        draft = draft.copy(period = it.toIntOrNull() ?: draft.period)
                    }
                    NumberFieldRow(stringResource(R.string.kline_indicator_band_width), draft.width.toString()) {
                        draft = draft.copy(width = it.toDoubleOrNull() ?: draft.width)
                    }
                    HorizontalDivider(color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.4f))
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    SimpleLineConfigEditorRow("UP", draft.upperLine) { draft = draft.copy(upperLine = it) }
                    SimpleLineConfigEditorRow("MB", draft.middleLine) { draft = draft.copy(middleLine = it) }
                    SimpleLineConfigEditorRow("DN", draft.lowerLine) { draft = draft.copy(lowerLine = it) }
                }
            }

            IndicatorSheetType.EMA -> {
                var draft by remember(settings) { mutableStateOf(settings.ema) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_ema_title),
                    onReset = { draft = EmaIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedMainIndicator = KlineIndicator.EMA,
                                ema = draft
                            )
                        )
                    }
                ) {
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_value),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    draft.lines.forEachIndexed { index, line ->
                        LineConfigEditorRow(
                            label = "EMA${index + 1}",
                            config = line,
                            onConfigChange = { updated ->
                                draft = draft.copy(lines = draft.lines.toMutableList().apply { set(index, updated) })
                            }
                        )
                    }
                }
            }

            IndicatorSheetType.VOL -> {
                var draft by remember(settings) { mutableStateOf(settings.vol) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_vol_title),
                    onReset = { draft = VolIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedSubIndicator = KlineIndicator.VOL,
                                vol = draft
                            )
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.kline_indicator_vol_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoinMonitorThemeTokens.colors.secondaryText
                    )
                    FillStyleRow(stringResource(R.string.kline_indicator_bullish), draft.bullStyle) { draft = draft.copy(bullStyle = it) }
                    FillStyleRow(stringResource(R.string.kline_indicator_bearish), draft.bearStyle) { draft = draft.copy(bearStyle = it) }
                    HorizontalDivider(color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.4f))
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_value),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    draft.mavolLines.forEachIndexed { index, line ->
                        LineConfigEditorRow(
                            label = "MAVOL${index + 1}",
                            config = line,
                            onConfigChange = { updated ->
                                draft = draft.copy(
                                    mavolLines = draft.mavolLines.toMutableList().apply { set(index, updated) }
                                )
                            }
                        )
                    }
                }
            }

            IndicatorSheetType.MACD -> {
                var draft by remember(settings) { mutableStateOf(settings.macd) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_macd_title),
                    onReset = { draft = MacdIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedSubIndicator = KlineIndicator.MACD,
                                macd = draft
                            )
                        )
                    }
                ) {
                    NumberFieldRow(stringResource(R.string.kline_indicator_short_period), draft.shortPeriod.toString()) {
                        draft = draft.copy(shortPeriod = it.toIntOrNull() ?: draft.shortPeriod)
                    }
                    NumberFieldRow(stringResource(R.string.kline_indicator_long_period), draft.longPeriod.toString()) {
                        draft = draft.copy(longPeriod = it.toIntOrNull() ?: draft.longPeriod)
                    }
                    NumberFieldRow(stringResource(R.string.kline_indicator_signal_period), draft.signalPeriod.toString()) {
                        draft = draft.copy(signalPeriod = it.toIntOrNull() ?: draft.signalPeriod)
                    }
                    HorizontalDivider(color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.4f))
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    SimpleLineConfigEditorRow("DIF", draft.difLine) { draft = draft.copy(difLine = it) }
                    SimpleLineConfigEditorRow("DEA", draft.deaLine) { draft = draft.copy(deaLine = it) }
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_macd_histogram),
                        stringResource(R.string.kline_indicator_header_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    HistogramConfigRow(stringResource(R.string.kline_indicator_histogram_bull_grow), draft.bullGrowHistogram) { draft = draft.copy(bullGrowHistogram = it) }
                    HistogramConfigRow(stringResource(R.string.kline_indicator_histogram_bull_shrink), draft.bullShrinkHistogram) { draft = draft.copy(bullShrinkHistogram = it) }
                    HistogramConfigRow(stringResource(R.string.kline_indicator_histogram_bear_grow), draft.bearGrowHistogram) { draft = draft.copy(bearGrowHistogram = it) }
                    HistogramConfigRow(stringResource(R.string.kline_indicator_histogram_bear_shrink), draft.bearShrinkHistogram) { draft = draft.copy(bearShrinkHistogram = it) }
                }
            }

            IndicatorSheetType.RSI -> {
                var draft by remember(settings) { mutableStateOf(settings.rsi) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_rsi_title),
                    onReset = { draft = RsiIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedSubIndicator = KlineIndicator.RSI,
                                rsi = draft
                            )
                        )
                    }
                ) {
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_value),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    draft.lines.forEachIndexed { index, line ->
                        LineConfigEditorRow(
                            label = "RSI${index + 1}",
                            config = line,
                            onConfigChange = { updated ->
                                draft = draft.copy(lines = draft.lines.toMutableList().apply { set(index, updated) })
                            }
                        )
                    }
                }
            }

            IndicatorSheetType.KDJ -> {
                var draft by remember(settings) { mutableStateOf(settings.kdj) }
                IndicatorSheetScaffold(
                    title = stringResource(R.string.kline_indicator_sheet_kdj_title),
                    onReset = { draft = KdjIndicatorSettings() },
                    onConfirm = {
                        onConfirm(
                            settings.copy(
                                selectedSubIndicator = KlineIndicator.KDJ,
                                kdj = draft
                            )
                        )
                    }
                ) {
                    NumberFieldRow(stringResource(R.string.kline_indicator_calc_period), draft.period.toString()) {
                        draft = draft.copy(period = it.toIntOrNull() ?: draft.period)
                    }
                    NumberFieldRow(stringResource(R.string.kline_indicator_avg_period_one), draft.kSmoothing.toString()) {
                        draft = draft.copy(kSmoothing = it.toIntOrNull() ?: draft.kSmoothing)
                    }
                    NumberFieldRow(stringResource(R.string.kline_indicator_avg_period_two), draft.dSmoothing.toString()) {
                        draft = draft.copy(dSmoothing = it.toIntOrNull() ?: draft.dSmoothing)
                    }
                    HorizontalDivider(color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.4f))
                    IndicatorHeaderRow(
                        stringResource(R.string.kline_indicator_header_name),
                        stringResource(R.string.kline_indicator_header_line_style),
                        stringResource(R.string.kline_indicator_header_color)
                    )
                    SimpleLineConfigEditorRow("K", draft.kLine) { draft = draft.copy(kLine = it) }
                    SimpleLineConfigEditorRow("D", draft.dLine) { draft = draft.copy(dLine = it) }
                    SimpleLineConfigEditorRow("J", draft.jLine) { draft = draft.copy(jLine = it) }
                }
            }
        }
    }
}

/**
 * 面板统一骨架。
 */
@Composable
private fun IndicatorSheetScaffold(
    title: String,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            content()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = CoinMonitorThemeTokens.colors.heroBackground,
                    onClick = onReset
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.kline_indicator_reset), fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFD33D),
                    onClick = onConfirm
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.kline_indicator_confirm), fontWeight = FontWeight.SemiBold, color = Color(0xFF2B2B2B))
                    }
                }
            }
        }
    )
}

/**
 * 表头行。
 */
@Composable
private fun IndicatorHeaderRow(vararg titles: String) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        titles.forEachIndexed { index, title ->
            Text(
                text = title,
                modifier = Modifier.weight(if (index == 0) 1.3f else 1f),
                style = MaterialTheme.typography.labelLarge,
                color = colors.secondaryText
            )
        }
    }
}

/**
 * 带参数的折线配置行。
 */
@Composable
private fun LineConfigEditorRow(
    label: String,
    config: IndicatorLineConfig,
    onConfigChange: (IndicatorLineConfig) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleLabel(label = label, checked = config.enabled) {
            onConfigChange(config.copy(enabled = it))
        }
        CompactValueField(
            value = config.period?.toString().orEmpty(),
            onValueChange = { onConfigChange(config.copy(period = it.toIntOrNull())) }
        )
        LineStyleSelector(selected = config.lineStyle) {
            onConfigChange(config.copy(lineStyle = it))
        }
        ColorPresetSelector(selected = config.colorPreset) {
            onConfigChange(config.copy(colorPreset = it))
        }
    }
}

/**
 * 不带参数值的折线配置行。
 */
@Composable
private fun SimpleLineConfigEditorRow(
    label: String,
    config: IndicatorLineConfig,
    onConfigChange: (IndicatorLineConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleLabel(label = label, checked = config.enabled) {
            onConfigChange(config.copy(enabled = it))
        }
        LineStyleSelector(
            selected = config.lineStyle,
            modifier = Modifier.weight(1f)
        ) {
            onConfigChange(config.copy(lineStyle = it))
        }
        ColorPresetSelector(
            selected = config.colorPreset,
            modifier = Modifier.weight(1f)
        ) {
            onConfigChange(config.copy(colorPreset = it))
        }
    }
}

/**
 * 柱体样式行。
 */
@Composable
private fun HistogramConfigRow(
    label: String,
    config: IndicatorHistogramConfig,
    onConfigChange: (IndicatorHistogramConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.3f),
            style = MaterialTheme.typography.bodyMedium
        )
        FillStyleSelector(
            selected = config.fillStyle,
            modifier = Modifier.weight(1f)
        ) {
            onConfigChange(config.copy(fillStyle = it))
        }
        ColorPresetSelector(
            selected = config.colorPreset,
            modifier = Modifier.weight(1f)
        ) {
            onConfigChange(config.copy(colorPreset = it))
        }
    }
}

/**
 * 数值输入行。
 */
@Composable
private fun NumberFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        CompactValueField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(112.dp)
        )
    }
}

/**
 * 做多做空样式行。
 */
@Composable
private fun FillStyleRow(
    label: String,
    selected: IndicatorFillStyle,
    onSelected: (IndicatorFillStyle) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        FillStyleSelector(
            selected = selected,
            modifier = Modifier.width(112.dp),
            onSelected = onSelected
        )
    }
}

/**
 * 勾选标签。
 */
@Composable
private fun ToggleLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.width(92.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(18.dp),
            shape = RoundedCornerShape(4.dp),
            color = if (checked) Color(0xFFE8E8E8) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = CoinMonitorThemeTokens.colors.divider
            ),
            onClick = { onCheckedChange(!checked) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (checked) {
                    Text("✓", color = Color(0xFF2B2B2B), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 紧凑输入框。
 */
@Composable
private fun CompactValueField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.width(88.dp)
) {
    Surface(
        modifier = modifier.height(INDICATOR_EDITOR_CONTROL_HEIGHT),
        shape = RoundedCornerShape(10.dp),
        color = CoinMonitorThemeTokens.colors.pageBackground,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = CoinMonitorThemeTokens.colors.divider
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = CoinMonitorThemeTokens.colors.primaryText
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

/**
 * 线型选择器。
 */
@Composable
private fun LineStyleSelector(
    selected: IndicatorLineStyle,
    modifier: Modifier = Modifier.width(88.dp),
    onSelected: (IndicatorLineStyle) -> Unit
) {
    SimpleDropdownSelector(
        modifier = modifier,
        currentLabel = selected.displayLabel(),
        options = IndicatorLineStyle.entries.map { it.displayLabel() to it },
        onSelected = onSelected
    )
}

/**
 * 填充样式选择器。
 */
@Composable
private fun FillStyleSelector(
    selected: IndicatorFillStyle,
    modifier: Modifier = Modifier.width(88.dp),
    onSelected: (IndicatorFillStyle) -> Unit
) {
    SimpleDropdownSelector(
        modifier = modifier,
        currentLabel = selected.displayLabel(),
        options = IndicatorFillStyle.entries.map { it.displayLabel() to it },
        onSelected = onSelected
    )
}

/**
 * 颜色选择器。
 */
@Composable
private fun ColorPresetSelector(
    selected: IndicatorColorPreset,
    modifier: Modifier = Modifier.width(88.dp),
    onSelected: (IndicatorColorPreset) -> Unit
) {
    SimpleDropdownSelector(
        modifier = modifier,
        currentLabel = selected.displayLabel(),
        leadingColor = Color(selected.colorInt),
        options = IndicatorColorPreset.entries.map { it.displayLabel() to it },
        optionColorProvider = { Color(it.colorInt) },
        onSelected = onSelected
    )
}

/**
 * 通用下拉选择器。
 */
@Composable
private fun <T> SimpleDropdownSelector(
    currentLabel: String,
    options: List<Pair<String, T>>,
    modifier: Modifier = Modifier,
    leadingColor: Color? = null,
    optionColorProvider: ((T) -> Color?)? = null,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.height(INDICATOR_EDITOR_CONTROL_HEIGHT),
        shape = RoundedCornerShape(10.dp),
        color = CoinMonitorThemeTokens.colors.heroBackground,
        onClick = { expanded = true }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .clickable(onClick = { expanded = true }),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingColor?.let {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(it, RoundedCornerShape(4.dp))
                    )
                }
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (label, value) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                optionColorProvider?.invoke(value)?.let {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(it, RoundedCornerShape(4.dp))
                                    )
                                }
                                Text(label)
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelected(value)
                        }
                    )
                }
            }
        }
    }
}

private data class IndicatorSection(
    val title: Int,
    val items: List<IndicatorMenuItem>
)

/**
 * 指标编辑区统一控件高度。
 */
private val INDICATOR_EDITOR_CONTROL_HEIGHT = 36.dp

private data class IndicatorMenuItem(
    val indicator: KlineIndicator,
    val subtitle: Int,
    val selected: Boolean
)

private enum class IndicatorSheetType {
    MA,
    EMA,
    BOLL,
    VOL,
    MACD,
    RSI,
    KDJ
}

private fun KlineIndicator.toSheetType(): IndicatorSheetType {
    return when (this) {
        KlineIndicator.MA -> IndicatorSheetType.MA
        KlineIndicator.EMA -> IndicatorSheetType.EMA
        KlineIndicator.BOLL -> IndicatorSheetType.BOLL
        KlineIndicator.VOL -> IndicatorSheetType.VOL
        KlineIndicator.MACD -> IndicatorSheetType.MACD
        KlineIndicator.RSI -> IndicatorSheetType.RSI
        KlineIndicator.KDJ -> IndicatorSheetType.KDJ
    }
}

/**
 * 返回指标显示名称。
 */
@Composable
private fun KlineIndicator.displayLabel(): String {
    return when (this) {
        KlineIndicator.MA -> "MA"
        KlineIndicator.EMA -> "EMA"
        KlineIndicator.BOLL -> "BOLL"
        KlineIndicator.VOL -> "VOL"
        KlineIndicator.MACD -> "MACD"
        KlineIndicator.RSI -> "RSI"
        KlineIndicator.KDJ -> "KDJ"
    }
}

/**
 * 返回线型文案。
 */
@Composable
private fun IndicatorLineStyle.displayLabel(): String {
    return when (this) {
        IndicatorLineStyle.THIN -> stringResource(R.string.indicator_line_style_thin)
        IndicatorLineStyle.MEDIUM -> stringResource(R.string.indicator_line_style_medium)
    }
}

/**
 * 返回填充样式文案。
 */
@Composable
private fun IndicatorFillStyle.displayLabel(): String {
    return when (this) {
        IndicatorFillStyle.SOLID -> stringResource(R.string.indicator_fill_style_solid)
        IndicatorFillStyle.HOLLOW -> stringResource(R.string.indicator_fill_style_hollow)
    }
}

/**
 * 返回颜色预设文案。
 */
@Composable
private fun IndicatorColorPreset.displayLabel(): String {
    return when (this) {
        IndicatorColorPreset.YELLOW -> stringResource(R.string.indicator_color_yellow)
        IndicatorColorPreset.MAGENTA -> stringResource(R.string.indicator_color_magenta)
        IndicatorColorPreset.PURPLE -> stringResource(R.string.indicator_color_purple)
        IndicatorColorPreset.GREEN -> stringResource(R.string.indicator_color_green)
        IndicatorColorPreset.PINK -> stringResource(R.string.indicator_color_pink)
        IndicatorColorPreset.CYAN -> stringResource(R.string.indicator_color_cyan)
        IndicatorColorPreset.INDIGO -> stringResource(R.string.indicator_color_indigo)
        IndicatorColorPreset.LIME -> stringResource(R.string.indicator_color_lime)
        IndicatorColorPreset.ORANGE -> stringResource(R.string.indicator_color_orange)
        IndicatorColorPreset.BLUE -> stringResource(R.string.indicator_color_blue)
        IndicatorColorPreset.BULL_GREEN -> stringResource(R.string.indicator_color_bull_green)
        IndicatorColorPreset.BEAR_RED -> stringResource(R.string.indicator_color_bear_red)
    }
}
