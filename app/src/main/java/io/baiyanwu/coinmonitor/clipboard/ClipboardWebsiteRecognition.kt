package io.baiyanwu.coinmonitor.clipboard

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Recognition is local. Unknown hosts only inherit the exact route pasted by the user. */
internal object ClipboardWebsiteRecognition {
    data class Result(val destination: ClipboardDestination, val address: String, val chain: String?)
    sealed interface Outcome {
        data object Invalid : Outcome
        data object NoAddress : Outcome
        data class Ambiguous(val addresses: List<String>) : Outcome
        data class Recognized(val result: Result) : Outcome
    }

    private val aliases = mapOf("sol" to "solana", "solana" to "solana", "eth" to "ethereum",
        "ethereum" to "ethereum", "bnb" to "bsc", "bsc" to "bsc", "base" to "base",
        "monad" to "monad", "robinhood-chain" to "robinhood", "robinhood" to "robinhood",
        "arbitrum" to "arbitrum", "arb" to "arbitrum", "polygon" to "polygon", "optimism" to "optimism")
    private val explorerChains = mapOf("etherscan.io" to "ethereum", "bscscan.com" to "bsc",
        "basescan.org" to "base", "solscan.io" to "solana", "robinhoodchain.blockscout.com" to "robinhood")

    fun recognize(input: String, existing: ClipboardDestination, selectedAddress: String? = null): Outcome {
        val value = input.trim()
        if (value.toByteArray().size > 8192 || safeClipboardUrl(value) == null || '{' in value || '}' in value) return Outcome.Invalid
        val uri = runCatching { URI(value) }.getOrNull() ?: return Outcome.Invalid
        val host = uri.host.lowercase(Locale.ROOT)
        val body = listOfNotNull(uri.rawPath, uri.rawQuery, uri.rawFragment).joinToString("?")
        val decoded = runCatching { URLDecoder.decode(body.replace("+", "%2B"), "UTF-8") }.getOrDefault(body)
        val addresses = ClipboardAddressParser.extract(decoded)
        if (addresses.isEmpty()) return Outcome.NoAddress
        val address = selectedAddress?.let { chosen -> addresses.firstOrNull { it == chosen } }
            ?: addresses.singleOrNull() ?: return Outcome.Ambiguous(addresses)
        // Only a literal, unique address position is safe to replace in a URL template.
        if (!value.contains(address)) return Outcome.Invalid
        var template = value.replace(address, "{ca}")
        var route: String? = null
        uri.rawQuery?.split('&')?.forEach { entry ->
            val key = entry.substringBefore('=').lowercase(Locale.ROOT)
            val mapped = entry.substringAfter('=', "")
            if (route == null && key in setOf("chain", "network", "chain_id", "chainid") && mapped.lowercase() in aliases) {
                route = mapped
                template = template.replace(entry, "${entry.substringBefore('=')}={chain}")
            }
        }
        if (route == null) {
            val parts = (uri.rawPath.orEmpty() + uri.rawFragment.orEmpty()).split('/')
            for ((index, part) in parts.withIndex()) {
                if (part.lowercase() !in aliases) continue
                val previous = parts.getOrNull(index - 1)?.lowercase()
                val next = parts.getOrNull(index + 1)?.lowercase()
                if (previous in setOf("token", "tokens", "address", "coin") || next in setOf("token", "tokens", "address", "coin", address.lowercase())) {
                    route = part
                    template = template.replace("/$part/", "/{chain}/")
                    break
                }
            }
        }
        val chain = route?.lowercase()?.let(aliases::get) ?: explorerChains[host]
        val known = clipboardDestinations().firstOrNull { it.template == template }
        val oldSourceTemplate = existing.sourceUrl?.let { source -> ClipboardAddressParser.extract(source).singleOrNull()?.let { source.replace(it, "{ca}") } }
        val sameFormat = existing.template == template || existing.template.isBlank() && (template in existing.chainTemplates.values || oldSourceTemplate == template)
        val values = linkedMapOf<String, String>()
        if (route != null && chain != null) values[chain] = route!!
        known?.chainValues?.forEach { (key, mapped) -> values.putIfAbsent(key, mapped) }
        if (sameFormat) values.putAll(existing.chainValues)
        val removed = if (sameFormat) existing.removedNetworkRules else emptySet()
        removed.forEach(values::remove)
        val name = known?.name ?: mapOf("dexscreener.com" to "DexScreener", "gmgn.ai" to "GMGN",
            "web3.okx.com" to "OKX Web3", "web3.binance.com" to "Binance Web3", "fomo.family" to "FoMo", "x.com" to "X")[host]
            ?: host.removePrefix("www.")
        val perChain = route == null && chain != null && host in explorerChains
        val destination = existing.copy(
            name = existing.name.ifBlank { name }, template = if (perChain) "" else template,
            chainValues = if (perChain) emptyMap() else values,
            chainTemplates = if (perChain) { if (chain in removed) existing.chainTemplates else existing.chainTemplates + (chain!! to template) } else emptyMap(),
            removedNetworkRules = removed, sourceUrl = value,
            iconUrl = existing.iconUrl?.takeIf { runCatching { URI(it).host == host }.getOrDefault(false) }
                ?: URI(uri.scheme, null, host, uri.port, "/favicon.ico", null, null).toString()
        )
        return if (validClipboardDestination(destination)) Outcome.Recognized(Result(destination, address, chain)) else Outcome.Invalid
    }
}

internal fun clipboardAIPromptUrl(provider: String, match: ClipboardMatch, question: String): String {
    val prompt = buildString {
        append(question.trim())
        append("\n${match.selection.tokenName} (${match.selection.tokenSymbol})")
        append("\n${match.chain.displayName}\nCA: ${match.selection.tokenAddress}")
        append("\nUSD: ${match.selection.priceUsd}")
        match.marketCap?.let { append("\nMarket cap: $it") }
    }
    val encoded = java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
    return if (provider == "grok") "https://grok.com/?q=$encoded" else "https://chatgpt.com/?q=$encoded"
}
