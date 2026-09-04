package io.baiyanwu.coinmonitor.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.overlay.OverlayRuntimePolicy
import io.baiyanwu.coinmonitor.ui.settings.overlay.OverlaySettingSwitchRow
import io.baiyanwu.coinmonitor.ui.settings.overlay.OverlaySettingsCard
import io.baiyanwu.coinmonitor.ui.settings.overlay.arranged.ArrangedOverlaySettingsSection
import io.baiyanwu.coinmonitor.ui.settings.overlay.marquee.MarqueeOverlaySettingsSection
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlinx.coroutines.launch

@Composable
fun OverlaySettingsRoute(
    container: AppContainer,
    overlayPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val viewModel: OverlaySettingsViewModel = viewModel(
        factory = OverlaySettingsViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.noticeMessage) {
        val message = state.noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    OverlaySettingsScreen(
        state = state,
        overlayPermissionGranted = overlayPermissionGranted,
        notificationPermissionGranted = notificationPermissionGranted,
        onBack = onBack,
        onRequestOverlayPermission = onRequestOverlayPermission,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onEnabledChange = { enabled ->
            scope.launch {
                if (enabled) {
                    if (!notificationPermissionGranted) onRequestNotificationPermission()
                    val shouldEnable = OverlayRuntimePolicy.shouldPersistEnabled(
                        requestedEnabled = true,
                        canDrawOverlays = overlayPermissionGranted
                    )
                    viewModel.setEnabled(shouldEnable)
                    if (shouldEnable) {
                        onStartOverlay()
                    } else {
                        onStopOverlay()
                        onRequestOverlayPermission()
                    }
                } else {
                    viewModel.setEnabled(false)
                    onStopOverlay()
                }
            }
        },
        onLockedChange = viewModel::setLocked,
        onDisplayTypeChange = viewModel::setDisplayType,
        onArrangedOpacityChange = viewModel::setArrangedOpacity,
        onArrangedMaxCountChange = viewModel::setArrangedMaxCount,
        onArrangedLeadingDisplayModeChange = viewModel::setArrangedLeadingDisplayMode,
        onArrangedFontScaleChange = viewModel::setArrangedFontScale,
        onArrangedSnapToEdgeChange = viewModel::setArrangedSnapToEdge,
        onArrangedEdgeDisplayModeChange = viewModel::setArrangedEdgeDisplayMode,
        onArrangedEdgeTabOpacityChange = viewModel::setArrangedEdgeTabOpacity,
        onArrangedEdgeAutoCollapseSecondsChange =
            viewModel::setArrangedEdgeAutoCollapseSeconds,
        onMarqueeOpacityChange = viewModel::setMarqueeOpacity,
        onMarqueeMaxCountChange = viewModel::setMarqueeMaxCount,
        onMarqueeFontScaleChange = viewModel::setMarqueeFontScale,
        onMarqueeSpeedChange = viewModel::setMarqueeSpeed,
        onToggleItem = viewModel::toggleItem
    )
}

@Composable
private fun OverlaySettingsScreen(
    state: OverlaySettingsUiState,
    overlayPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onDisplayTypeChange: (OverlayDisplayType) -> Unit,
    onArrangedOpacityChange: (Float) -> Unit,
    onArrangedMaxCountChange: (Int) -> Unit,
    onArrangedLeadingDisplayModeChange: (OverlayLeadingDisplayMode) -> Unit,
    onArrangedFontScaleChange: (Float) -> Unit,
    onArrangedSnapToEdgeChange: (Boolean) -> Unit,
    onArrangedEdgeDisplayModeChange: (ArrangedEdgeDisplayMode) -> Unit,
    onArrangedEdgeTabOpacityChange: (Float) -> Unit,
    onArrangedEdgeAutoCollapseSecondsChange: (Int) -> Unit,
    onMarqueeOpacityChange: (Float) -> Unit,
    onMarqueeMaxCountChange: (Int) -> Unit,
    onMarqueeFontScaleChange: (Float) -> Unit,
    onMarqueeSpeedChange: (MarqueeSpeed) -> Unit,
    onToggleItem: (String) -> Unit
) {
    if (!state.isLoaded) {
        OverlaySettingsLoadingScreen(onBack)
        return
    }

    val colors = CoinMonitorThemeTokens.colors
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = { OverlaySettingsTopBar(onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OverlaySettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        OverlaySettingSwitchRow(
                            title = stringResource(R.string.overlay_enable),
                            checked = state.settings.enabled,
                            horizontalPadding = 0.dp,
                            verticalPadding = 0.dp,
                            onCheckedChange = onEnabledChange
                        )
                        OverlaySettingSwitchRow(
                            title = stringResource(R.string.overlay_lock_drag),
                            checked = state.settings.locked,
                            horizontalPadding = 0.dp,
                            verticalPadding = 0.dp,
                            onCheckedChange = onLockedChange
                        )
                    }
                    Text(
                        text = stringResource(R.string.overlay_display_type),
                        style = MaterialTheme.typography.titleSmall
                    )
                    OverlayDisplayTypeSelector(
                        selectedType = state.settings.displayType,
                        onTypeSelected = onDisplayTypeChange
                    )
                }
            }

            item {
                when (state.settings.displayType) {
                    OverlayDisplayType.ARRANGED -> ArrangedOverlaySettingsSection(
                        settings = state.settings.arranged,
                        onOpacityChange = onArrangedOpacityChange,
                        onMaxCountChange = onArrangedMaxCountChange,
                        onLeadingDisplayModeChange = onArrangedLeadingDisplayModeChange,
                        onFontScaleChange = onArrangedFontScaleChange,
                        onSnapToEdgeChange = onArrangedSnapToEdgeChange,
                        onEdgeDisplayModeChange = onArrangedEdgeDisplayModeChange,
                        onEdgeTabOpacityChange = onArrangedEdgeTabOpacityChange,
                        onEdgeAutoCollapseSecondsChange =
                            onArrangedEdgeAutoCollapseSecondsChange
                    )

                    OverlayDisplayType.MARQUEE -> MarqueeOverlaySettingsSection(
                        settings = state.settings.marquee,
                        onOpacityChange = onMarqueeOpacityChange,
                        onMaxCountChange = onMarqueeMaxCountChange,
                        onFontScaleChange = onMarqueeFontScaleChange,
                        onSpeedChange = onMarqueeSpeedChange
                    )
                }
            }

            item {
                OverlaySettingsCard {
                    Text(
                        text = stringResource(R.string.overlay_select_items),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (state.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 104.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.overlay_empty_state),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            state.items.forEach { item ->
                                OverlaySettingSwitchRow(
                                    title = item.symbol,
                                    subtitle = item.exchangeSource.title,
                                    horizontalPadding = 0.dp,
                                    verticalPadding = 0.dp,
                                    checked = item.overlaySelected,
                                    onCheckedChange = { onToggleItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (!overlayPermissionGranted) {
                item {
                    MessageCard(
                        stringResource(R.string.overlay_permission_message),
                        stringResource(R.string.overlay_permission_action),
                        onRequestOverlayPermission
                    )
                }
            }

            if (!notificationPermissionGranted) {
                item {
                    MessageCard(
                        stringResource(R.string.notification_permission_message),
                        stringResource(R.string.notification_permission_action),
                        onRequestNotificationPermission
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayDisplayTypeSelector(
    selectedType: OverlayDisplayType,
    onTypeSelected: (OverlayDisplayType) -> Unit
) {
    val options = listOf(
        OverlayDisplayType.ARRANGED to stringResource(R.string.overlay_display_type_arranged),
        OverlayDisplayType.MARQUEE to stringResource(R.string.overlay_display_type_marquee)
    )
    val colors = CoinMonitorThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.weight(1f),
                    label = { Text(label) }
                )
            }
        }
        Text(
            text = stringResource(
                when (selectedType) {
                    OverlayDisplayType.ARRANGED -> R.string.overlay_display_type_arranged_hint
                    OverlayDisplayType.MARQUEE -> R.string.overlay_display_type_marquee_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
    }
}

@Composable
private fun OverlaySettingsLoadingScreen(onBack: () -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = { OverlaySettingsTopBar(onBack = onBack) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = colors.accent
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OverlaySettingsTopBar(onBack: () -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = colors.pageBackground
        ),
        title = { Text(stringResource(R.string.overlay_settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
        }
    )
}

@Composable
private fun MessageCard(message: String, buttonLabel: String, onClick: () -> Unit) {
    OverlaySettingsCard {
        Text(message)
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CoinMonitorComponentDefaults.primaryButtonColors()
        ) {
            Text(buttonLabel)
        }
    }
}
