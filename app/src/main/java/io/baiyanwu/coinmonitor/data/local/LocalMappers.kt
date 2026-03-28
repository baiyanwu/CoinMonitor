package io.baiyanwu.coinmonitor.data.local

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem

fun WatchItemEntity.toDomain(): WatchItem {
    return WatchItem(
        id = id,
        symbol = symbol,
        name = name,
        exchangeSource = ExchangeSource.valueOf(source),
        marketType = marketType.toEnumOrDefault(MarketType.CEX_SPOT),
        chainFamily = chainFamily?.toEnumOrNull<ChainFamily>(),
        chainIndex = chainIndex,
        tokenAddress = tokenAddress,
        iconUrl = iconUrl,
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
        marketType = marketType.name,
        chainFamily = chainFamily?.name,
        chainIndex = chainIndex,
        tokenAddress = tokenAddress,
        iconUrl = iconUrl,
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

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T {
    return runCatching { enumValueOf<T>(this) }.getOrDefault(default)
}

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? {
    return runCatching { enumValueOf<T>(this) }.getOrNull()
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
