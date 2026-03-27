package io.coinbar.tokenmonitor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.coinbar.tokenmonitor.R
import io.coinbar.tokenmonitor.data.AppContainer
import io.coinbar.tokenmonitor.ui.components.WatchItemCard
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorComponentDefaults
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorThemeTokens

@Composable
fun HomeRoute(
    container: AppContainer,
    onNavigateSearch: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onNavigateSearch = onNavigateSearch,
        onRemoveWatchItem = viewModel::removeWatchItem,
        onToggleOverlay = viewModel::toggleOverlay,
        onRefresh = viewModel::refreshNow
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onNavigateSearch: () -> Unit,
    onRemoveWatchItem: (String) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var menuItemId by remember { mutableStateOf<String?>(null) }
    val colors = TokenMonitorThemeTokens.colors

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                SearchEntryButton(onClick = onNavigateSearch)
            }

            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_empty_hint),
                            textAlign = TextAlign.Center,
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = onNavigateSearch,
                            colors = TokenMonitorComponentDefaults.primaryButtonColors()
                        ) {
                            Text(text = stringResource(R.string.home_add_pair))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 0.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            WatchItemCard(
                                item = item,
                                overlaySelected = state.overlayIds.contains(item.id),
                                onLongPress = { menuItemId = item.id }
                            )

                            DropdownMenu(
                                expanded = menuItemId == item.id,
                                onDismissRequest = { menuItemId = null }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.overlayIds.contains(item.id)) {
                                                stringResource(R.string.overlay_remove)
                                            } else {
                                                stringResource(R.string.overlay_add)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                                    },
                                    onClick = {
                                        onToggleOverlay(item.id)
                                        menuItemId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        onRemoveWatchItem(item.id)
                                        menuItemId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.items.isNotEmpty()) {
            FloatingActionButton(
                onClick = onRefresh,
                containerColor = colors.fabContainer,
                contentColor = colors.fabContent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.refresh)
                )
            }
        }
    }
}

@Composable
private fun SearchEntryButton(onClick: () -> Unit) {
    val colors = TokenMonitorThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.fabContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.home_open_search),
                tint = colors.fabContent
            )
        }
    }
}
