package io.baiyanwu.coinmonitor.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 首页“更多”移至顶部；保留添加币对和悬浮币对配置两个操作。 */
@Composable
internal fun HomePairActionsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddPair: () -> Unit,
    onConfigureOverlayItems: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        CoinMonitorSmallIconButton(
            CoinMonitorIcons.More, stringResource(R.string.home_pair_actions),
            onClick = { onExpandedChange(!expanded) }, modifier = Modifier.testTag("home-pair-actions-trigger"),
            containerColor = CoinMonitorThemeTokens.design.colors.surface
        )
        DropdownMenu(expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_add_pair)) },
                leadingIcon = { Icon(CoinMonitorIcons.Add, null) },
                onClick = onAddPair, modifier = Modifier.testTag("home-pair-actions-add")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overlay_items_settings_title)) },
                leadingIcon = { Icon(CoinMonitorIcons.Settings, null) },
                onClick = onConfigureOverlayItems, modifier = Modifier.testTag("home-pair-actions-overlay")
            )
        }
    }
}
