package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/**
 * 网络日志页路由入口。
 */
@Composable
fun NetworkLogRoute(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: NetworkLogViewModel = viewModel(factory = NetworkLogViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NetworkLogScreen(
        state = state,
        onBack = onBack,
        onToggleRecording = {
            viewModel.setRecordingEnabled(!state.recordingEnabled)
        },
        onHttpEnabledChange = viewModel::setHttpEnabled,
        onWssEnabledChange = viewModel::setWssEnabled,
        onClear = viewModel::clear,
        onEntryClick = viewModel::onEntryClick
    )
}

/**
 * 网络日志页主界面。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NetworkLogScreen(
    state: NetworkLogUiState,
    onBack: () -> Unit,
    onToggleRecording: () -> Unit,
    onHttpEnabledChange: (Boolean) -> Unit,
    onWssEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onEntryClick: (Long) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.pageBackground
                ),
                title = {
                    Text(text = stringResource(R.string.network_log_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleRecording) {
                        Icon(
                            imageVector = if (state.recordingEnabled) {
                                Icons.Rounded.PauseCircle
                            } else {
                                Icons.Rounded.PlayCircle
                            },
                            contentDescription = stringResource(
                                if (state.recordingEnabled) {
                                    R.string.network_log_stop
                                } else {
                                    R.string.network_log_start
                                }
                            )
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.network_log_clear)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (state.recordingEnabled) {
                                R.string.network_log_status_running
                            } else {
                                R.string.network_log_status_stopped
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.network_log_count, state.entries.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                }

                ProtocolSwitchRow(
                    title = stringResource(R.string.network_log_protocol_http),
                    checked = state.httpEnabled,
                    onCheckedChange = onHttpEnabledChange
                )
                ProtocolSwitchRow(
                    title = stringResource(R.string.network_log_protocol_wss),
                    checked = state.wssEnabled,
                    onCheckedChange = onWssEnabledChange
                )
            }

            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.pageBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.network_log_empty),
                        color = colors.secondaryText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = state.entries,
                        key = { entry -> entry.id }
                    ) { entry ->
                        NetworkLogRow(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 协议开关行。
 */
@Composable
private fun ProtocolSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 单条网络日志行。
 */
@Composable
private fun NetworkLogRow(
    entry: NetworkLogEntry,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (entry.protocol == NetworkLogProtocol.HTTP) {
                    stringResource(R.string.network_log_protocol_http)
                } else {
                    stringResource(R.string.network_log_protocol_wss)
                },
                style = MaterialTheme.typography.labelMedium,
                color = CoinMonitorThemeTokens.colors.secondaryText
            )
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
