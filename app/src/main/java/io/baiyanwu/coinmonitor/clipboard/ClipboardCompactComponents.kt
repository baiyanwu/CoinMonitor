package io.baiyanwu.coinmonitor.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact controls are scoped to the transient query window. */
@Composable
internal fun ClipboardCompactTheme(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        MaterialTheme(typography = typography.copy(
            titleMedium = typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
            titleSmall = typography.titleSmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
            bodyMedium = typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
            bodySmall = typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            labelLarge = typography.labelLarge.copy(fontSize = 11.sp, lineHeight = 14.sp)
        ), content = content)
    }
}

@Composable
internal fun ClipboardTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier.defaultMinSize(minHeight = 32.dp), enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), content = content)
}

@Composable
internal fun ClipboardTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick, modifier.defaultMinSize(minHeight = 32.dp), enabled = enabled,
        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), content = content)
}

@Composable
internal fun ClipboardOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.defaultMinSize(minHeight = 32.dp), enabled = enabled,
        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), content = content)
}

@Composable
internal fun ClipboardIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick, Modifier.size(32.dp), content = content)
}

@Composable
internal fun ClipboardModeTabs(wallet: Boolean, onSelect: (Boolean) -> Unit, tokenLabel: String, walletLabel: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(2.dp)) {
        listOf(false to tokenLabel, true to walletLabel).forEach { (value, label) ->
            Box(Modifier.weight(1f).heightIn(min = 28.dp)
                .background(if (wallet == value) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
                .selectable(selected = wallet == value, role = Role.Tab, onClick = { onSelect(value) })
                .padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (wallet == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ClipboardFloatingToggle(checked: Boolean, enabled: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.size(32.dp).semantics { contentDescription = label }.toggleable(checked, enabled, Role.Switch, onCheckedChange), contentAlignment = Alignment.Center) {
        val color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        Box(Modifier.size(30.dp, 18.dp).background(color.copy(alpha = if (enabled) 1f else 0.38f), RoundedCornerShape(9.dp)).padding(2.dp)) {
            Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).size(14.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp)))
        }
    }
}
