package io.coinbar.tokenmonitor.data.local

import io.coinbar.tokenmonitor.domain.model.ExchangeSource
import io.coinbar.tokenmonitor.domain.model.LivePriceTrend
import io.coinbar.tokenmonitor.domain.model.OverlayLeadingDisplayMode
import io.coinbar.tokenmonitor.domain.model.OverlaySettings
import io.coinbar.tokenmonitor.domain.model.WatchItem

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
        windowX = windowX,
        windowY = windowY
    )
}
