package io.baiyanwu.coinmonitor.clipboard

import java.net.URI
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal data class ClipboardAIPromptLabels(
    val token: String = "Token",
    val chain: String = "Network",
    val contract: String = "Contract address",
    val price: String = "Price (USD)",
    val marketCap: String = "Market cap (USD)",
    val pool: String = "Pool address",
    val market: String = "Market",
    val website: String = "Website",
    val unknown: String = "Unknown"
)

/** Keep the token context aligned with the macOS ClipboardAIPrompt. */
internal fun clipboardAIPromptUrl(
    provider: String,
    match: ClipboardMatch,
    question: String,
    labels: ClipboardAIPromptLabels = ClipboardAIPromptLabels()
): String {
    fun text(value: String) = value.takeIf { it.isNotBlank() } ?: labels.unknown
    val selection = match.selection
    val cap = match.marketCap?.takeIf { it.isFinite() && it >= 0 }?.let {
        DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(it)
    } ?: labels.unknown
    val price = selection.priceUsd.takeIf { it.isFinite() && it >= 0 }?.toString() ?: labels.unknown
    val lines = mutableListOf(
        "${labels.token}: ${text(selection.tokenName)} (${text(selection.tokenSymbol)})",
        "${labels.chain}: ${match.chain.displayName}",
        "${labels.contract}: ${selection.tokenAddress}",
        "${labels.price}: $price",
        "${labels.marketCap}: $cap"
    )
    selection.pair.pairAddress.takeIf { it.isNotBlank() }?.let { lines += "${labels.pool}: $it" }
    val marketUrl = safeClipboardUrl(selection.pair.url) ?: clipboardDestinations().first { it.id == "dex" }.url(match)
    marketUrl?.let { lines += "${labels.market}: $it" }
    for (link in match.projectLinks) {
        val uri = URI(link.url)
        val path = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        if (link.kind == ClipboardProjectLinkKind.X &&
            uri.host.lowercase(Locale.ROOT) in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com") &&
            path.size == 1 && path.single().lowercase(Locale.ROOT) !in setOf("search", "explore", "home", "intent")) {
            lines += "X: ${link.url}"
        } else if (link.kind == ClipboardProjectLinkKind.WEBSITE) {
            lines += "${labels.website}: ${link.url}"
        }
    }
    if (question.isNotBlank()) lines += "\n$question"
    val encoded = URLEncoder.encode(lines.joinToString("\n"), "UTF-8").replace("+", "%20")
    return if (provider == "grok") "https://grok.com/?q=$encoded" else "https://chatgpt.com/?q=$encoded"
}
