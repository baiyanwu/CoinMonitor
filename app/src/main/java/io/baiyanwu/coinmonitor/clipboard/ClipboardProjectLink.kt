package io.baiyanwu.coinmonitor.clipboard

import java.net.URI
import java.util.Locale

internal enum class ClipboardProjectLinkKind { WEBSITE, DOCUMENTS, X, DISCORD, OTHER }

internal data class ClipboardProjectLink(val label: String, val url: String, val kind: ClipboardProjectLinkKind)

internal fun clipboardProjectLink(label: String?, url: String?, website: Boolean): ClipboardProjectLink? {
    val safeUrl = safeClipboardUrl(url) ?: return null
    val uri = URI(safeUrl)
    val host = uri.host.orEmpty().lowercase(Locale.ROOT)
    val name = label.orEmpty().trim().lowercase(Locale.ROOT)
    val kind = when {
        host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") -> ClipboardProjectLinkKind.X
        host == "discord.gg" || host.endsWith(".discord.gg") || host == "discord.com" || host.endsWith(".discord.com") || host == "discordapp.com" || host.endsWith(".discordapp.com") -> ClipboardProjectLinkKind.DISCORD
        name in setOf("docs", "doc", "documentation", "documents", "whitepaper", "white paper", "文档", "白皮书") ||
            (website && (host.startsWith("docs.") || host.startsWith("documentation.") || uri.path.orEmpty().lowercase(Locale.ROOT).split('/').any { it in setOf("docs", "documentation", "whitepaper") })) -> ClipboardProjectLinkKind.DOCUMENTS
        name in setOf("twitter", "x") -> ClipboardProjectLinkKind.X
        name == "discord" -> ClipboardProjectLinkKind.DISCORD
        website -> ClipboardProjectLinkKind.WEBSITE
        else -> ClipboardProjectLinkKind.OTHER
    }
    return ClipboardProjectLink(label ?: if (website) "Website" else "Social", safeUrl, kind)
}
