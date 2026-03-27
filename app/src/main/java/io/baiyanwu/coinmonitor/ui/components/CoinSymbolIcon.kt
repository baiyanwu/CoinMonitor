package io.baiyanwu.coinmonitor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.overlay.CoinIconService
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
fun CoinSymbolIcon(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val context = LocalContext.current
    var bitmap by remember(symbol) { mutableStateOf<Bitmap?>(null) }
    val iconModifier = modifier.size(size)

    LaunchedEffect(symbol) {
        bitmap = CoinIconService.Companion.get(context).loadBitmap(symbol)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = iconModifier.clip(CircleShape)
        )
    } else {
        GenericCoinPlaceholder(modifier = iconModifier)
    }
}

@Composable
private fun GenericCoinPlaceholder(modifier: Modifier = Modifier) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.heroBackground,
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier
                .background(colors.heroBackground),
            contentAlignment = Alignment.Center
        ) {
            PlaceholderLayers()
        }
    }
}

@Composable
private fun BoxScope.PlaceholderLayers() {
    val colors = CoinMonitorThemeTokens.colors
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.22f))
            .align(Alignment.Center)
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.55f))
            .align(Alignment.Center)
    )
}
