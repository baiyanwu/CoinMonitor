package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 紧凑主页面标题；只在已接入新设计的页面使用。 */
@Composable
fun CoinMonitorPageHeader(title: String, modifier: Modifier = Modifier) {
    val design = CoinMonitorThemeTokens.design
    Row(
        modifier.heightIn(min = design.sizes.headerMinimumHeight)
            .padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.small)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(design.spacing.inline),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinMonitorLogo()
        Text(
            stringResource(R.string.app_name),
            modifier = Modifier.weight(1f, fill = false),
            style = design.typography.pageTitle,
            color = design.colors.accent
        )
        Box(Modifier.width(design.sizes.dividerThickness).height(design.sizes.actionIcon).background(design.colors.divider))
        Text(title, style = design.typography.rowTitle, color = design.colors.primaryText)
    }
}
