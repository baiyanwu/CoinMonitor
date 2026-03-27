package io.coinbar.tokenmonitor.overlay

object PriceTextSizer {
    fun resolveTextSizeSp(priceText: String): Float {
        val compactLength = priceText.filterNot { it == ',' || it == '.' }.length
        return when {
            compactLength <= 4 -> 12f
            compactLength <= 7 -> 11f
            compactLength <= 10 -> 10f
            compactLength <= 13 -> 9f
            else -> 8f
        }
    }
}
