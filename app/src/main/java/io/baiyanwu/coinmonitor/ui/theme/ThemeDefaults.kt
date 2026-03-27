package io.baiyanwu.coinmonitor.ui.theme

import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/**
 * 统一收口 Compose 常用控件的颜色方案，避免页面直接散落主题细节。
 * 后续如果需要切模板，只需要改语义 token 和这层映射。
 */
object CoinMonitorComponentDefaults {
    @Composable
    fun elevatedCardColors(): CardColors {
        val colors = CoinMonitorThemeTokens.colors
        return CardDefaults.elevatedCardColors(containerColor = colors.cardBackground)
    }

    @Composable
    fun primaryButtonColors(): ButtonColors {
        val colors = CoinMonitorThemeTokens.colors
        return ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.accentOnColor,
            disabledContainerColor = colors.cardBackground,
            disabledContentColor = colors.secondaryText
        )
    }

    @Composable
    fun filterChipColors() = run {
        val colors = CoinMonitorThemeTokens.colors
        FilterChipDefaults.filterChipColors(
            containerColor = colors.cardBackground,
            labelColor = colors.primaryText,
            iconColor = colors.secondaryText,
            selectedContainerColor = colors.chipSelectedContainer,
            selectedLabelColor = colors.chipSelectedContent,
            selectedLeadingIconColor = colors.chipSelectedContent,
            selectedTrailingIconColor = colors.chipSelectedContent,
            disabledContainerColor = colors.cardBackground,
            disabledLabelColor = colors.secondaryText
        )
    }

    @Composable
    fun navigationBarItemColors(): NavigationBarItemColors {
        val colors = CoinMonitorThemeTokens.colors
        return NavigationBarItemDefaults.colors(
            selectedIconColor = colors.chipSelectedContent,
            selectedTextColor = colors.primaryText,
            indicatorColor = colors.chipSelectedContainer,
            unselectedIconColor = colors.secondaryText,
            unselectedTextColor = colors.secondaryText
        )
    }

    @Composable
    fun switchColors(): SwitchColors {
        val colors = CoinMonitorThemeTokens.colors
        return SwitchDefaults.colors(
            checkedThumbColor = colors.accentOnColor,
            checkedTrackColor = colors.accent,
            uncheckedThumbColor = colors.secondaryText,
            uncheckedTrackColor = colors.heroBackground,
            uncheckedBorderColor = colors.divider
        )
    }

    @Composable
    fun sliderColors(): SliderColors {
        val colors = CoinMonitorThemeTokens.colors
        return SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.heroBackground,
            activeTickColor = colors.accentOnColor.copy(alpha = 0.9f),
            inactiveTickColor = colors.accent.copy(alpha = 0.5f)
        )
    }

    @Composable
    fun outlinedTextFieldColors() = run {
        val colors = CoinMonitorThemeTokens.colors
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.primaryText,
            unfocusedTextColor = colors.primaryText,
            focusedContainerColor = colors.cardBackground,
            unfocusedContainerColor = colors.cardBackground,
            disabledContainerColor = colors.cardBackground,
            cursorColor = colors.accent,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.secondaryText,
            focusedTrailingIconColor = colors.accent,
            unfocusedTrailingIconColor = colors.secondaryText
        )
    }

    @Composable
    fun assistChipColors() = run {
        val colors = CoinMonitorThemeTokens.colors
        AssistChipDefaults.assistChipColors(
            containerColor = colors.heroBackground,
            labelColor = colors.primaryText,
            leadingIconContentColor = colors.accent,
            trailingIconContentColor = colors.accent
        )
    }
}
