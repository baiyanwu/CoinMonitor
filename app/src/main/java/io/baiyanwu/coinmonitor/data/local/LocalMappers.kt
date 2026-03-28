package io.baiyanwu.coinmonitor.data.local

import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem

fun WatchItemEntity.toDomain(): WatchItem {
    return WatchItem(
        id = id,
        symbol = symbol,
        name = name,
        exchangeSource = ExchangeSource.valueOf(source),
        overlaySelected = overlaySelected,
        addedAt = addedAt,
        lastPrice = lastPrice,
        previousPrice = previousPrice,
        liveTrend = LivePriceTrend.valueOf(liveTrend),
        change24hPercent = change24hPercent,
        lastUpdatedAt = lastUpdatedAt
    )
}

fun WatchItem.toEntity(): WatchItemEntity {
    return WatchItemEntity(
        id = id,
        symbol = symbol,
        name = name,
        source = exchangeSource.name,
        overlaySelected = overlaySelected,
        addedAt = addedAt,
        lastPrice = lastPrice,
        previousPrice = previousPrice,
        liveTrend = liveTrend.name,
        change24hPercent = change24hPercent,
        lastUpdatedAt = lastUpdatedAt
    )
}

fun OverlaySettingsEntity.toDomain(): OverlaySettings {
    return OverlaySettings(
        enabled = enabled,
        locked = locked,
        opacity = opacity,
        maxItems = maxItems,
        leadingDisplayMode = OverlayLeadingDisplayMode.valueOf(leadingDisplayMode),
        fontScale = fontScale,
        snapToEdge = snapToEdge,
        windowX = windowX,
        windowY = windowY
    )
}

fun OverlaySettings.toEntity(): OverlaySettingsEntity {
    return OverlaySettingsEntity(
        enabled = enabled,
        locked = locked,
        opacity = opacity,
        maxItems = maxItems,
        leadingDisplayMode = leadingDisplayMode.name,
        fontScale = fontScale,
        snapToEdge = snapToEdge,
        windowX = windowX,
        windowY = windowY
    )
}
