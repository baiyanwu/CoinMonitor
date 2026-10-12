package io.baiyanwu.coinmonitor.clipboard

/** Candidate mappings and enabled mappings stay separate, so unchecking never loses edits. */
internal fun prepareClipboardNetworkRules(
    site: ClipboardDestination,
    detectedChain: String? = null,
    initializeSelection: Boolean = false
): ClipboardDestination {
    val perChain = site.template.isBlank()
    if (!perChain && !site.template.contains("{chain}")) return site
    val known = clipboardDestinations().firstOrNull { candidate ->
        if (perChain) candidate.chainTemplates.isNotEmpty() &&
            (candidate.id == site.id || site.chainTemplates.values.any { it in candidate.chainTemplates.values })
        else candidate.template == site.template
    }
    // Show every app network for both built-in and custom sites.
    // Blank values are deliberate: a chain's URL alias cannot be inferred from its name.
    val candidates = clipboardNetworkOptions(site).associateWith { "" }
    val values = if (perChain) emptyMap() else site.chainValues +
        (candidates + known?.chainValues.orEmpty()).filterKeys { it !in site.chainValues }
    val templates = if (perChain) site.chainTemplates +
        (candidates + known?.chainTemplates.orEmpty()).filterKeys { it !in site.chainTemplates } else emptyMap()
    val keys = if (perChain) templates.keys else values.keys
    val previousKeys = if (perChain) site.chainTemplates.keys else site.chainValues.keys
    val disabled = if (initializeSelection) keys - setOfNotNull(detectedChain)
        else site.disabledNetworkRules + site.removedNetworkRules + (keys - previousKeys)
    return site.copy(chainValues = values, chainTemplates = templates, disabledNetworkRules = disabled)
}

internal fun ClipboardDestination.withNetworkRuleEnabled(chain: String, enabled: Boolean): ClipboardDestination {
    fun promote(values: Map<String, String>): Map<String, String> =
        if (enabled && chain in values) mapOf(chain to values.getValue(chain)) + (values - chain) else values
    return copy(
        chainValues = promote(chainValues), chainTemplates = promote(chainTemplates),
        disabledNetworkRules = if (enabled) disabledNetworkRules - chain else disabledNetworkRules + chain,
        removedNetworkRules = if (enabled) removedNetworkRules - chain else removedNetworkRules
    )
}

internal fun ClipboardDestination.networkRuleRows(): List<Map.Entry<String, String>> =
    (if (template.isBlank()) chainTemplates else chainValues).entries.sortedBy {
        if (it.key in disabledNetworkRules || it.key in removedNetworkRules) 1 else 0
    }
