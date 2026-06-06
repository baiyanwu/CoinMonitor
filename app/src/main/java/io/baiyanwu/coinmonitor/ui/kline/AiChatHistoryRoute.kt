package io.baiyanwu.coinmonitor.ui.kline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AiChatSessionSummary
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiChatHistoryRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onSelectSession: (String) -> Unit
) {
    val viewModel: AiChatHistoryViewModel = viewModel(factory = AiChatHistoryViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AiChatHistoryScreen(
        state = state,
        onBack = onBack,
        onSelectSession = onSelectSession
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatHistoryScreen(
    state: AiChatHistoryUiState,
    onBack: () -> Unit,
    onSelectSession: (String) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.pageBackground
                ),
                title = { Text(text = stringResource(R.string.kline_chat_history)) },
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
    ) { innerPadding ->
        if (state.sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.kline_chat_history_empty),
                    color = colors.secondaryText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.sessions,
                    key = { summary -> summary.session.id }
                ) { summary ->
                    AiChatSessionCard(
                        summary = summary,
                        onClick = { onSelectSession(summary.session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiChatSessionCard(
    summary: AiChatSessionSummary,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.session.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.kline_chat_new_session),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatHistoryTime(summary.session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
            summary.session.symbol?.let { symbol ->
                Text(
                    text = if (summary.session.sourceTitle.isNullOrBlank()) {
                        symbol
                    } else {
                        "$symbol · ${summary.session.sourceTitle}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = summary.latestMessagePreview?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.kline_chat_history_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatHistoryTime(timestampMillis: Long): String {
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestampMillis))
}
