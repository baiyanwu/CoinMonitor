package io.baiyanwu.coinmonitor.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.ui.settings.overlay.OverlayItemSelectionSection
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
fun OverlayItemsSettingsRoute(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: OverlayItemsSettingsViewModel = viewModel(
        factory = OverlayItemsSettingsViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.noticeMessage) {
        val message = state.noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    OverlayItemsSettingsScreen(
        state = state,
        onBack = onBack,
        onToggleItem = viewModel::toggleItem,
        onMoveItem = viewModel::moveItem
    )
}

@Composable
internal fun OverlayItemsSettingsScreen(
    state: OverlayItemsSettingsUiState,
    onBack: () -> Unit,
    onToggleItem: (String) -> Unit,
    onMoveItem: (String, String?) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    var isDraggingItem by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = { OverlayItemsSettingsTopBar(onBack = onBack) }
    ) { innerPadding ->
        if (!state.isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = !isDraggingItem
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            OverlayItemSelectionSection(
                settings = state.settings,
                selectedItems = state.selectedItems,
                availableItems = state.availableItems,
                onToggleItem = onToggleItem,
                onMoveItem = onMoveItem,
                onDraggingChange = { isDraggingItem = it }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OverlayItemsSettingsTopBar(onBack: () -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = colors.pageBackground
        ),
        title = { Text(stringResource(R.string.overlay_items_settings_title)) },
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
