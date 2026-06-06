package io.baiyanwu.coinmonitor.data.ai.market

import io.baiyanwu.coinmonitor.lib.agents.SourceTimestampConfidence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

private val announcementJson = Json { ignoreUnknownKeys = true }

/**
 * 解析 Binance 公共公告列表接口的响应。
 */
internal fun parseBinanceAnnouncementRecords(
    payload: String,
    nowMillis: Long = System.currentTimeMillis()
): List<AnnouncementRecord> {
    val root = announcementJson.parseToJsonElement(payload).jsonObject
    val articles = root["data"]
        ?.jsonObject
        ?.get("articles")
        ?.jsonArray
        .orEmpty()

    return articles.mapNotNull { element ->
        val article = element.jsonObject
        val code = article["code"]?.jsonPrimitive?.content.orEmpty()
        val title = article["title"]?.jsonPrimitive?.content.orEmpty().trim()
        if (code.isBlank() || title.isBlank()) return@mapNotNull null
        val publishedAtMillis = parseBinanceTitleDateMillis(title) ?: nowMillis
        AnnouncementRecord(
            id = "binance:$code",
            title = title,
            url = "https://www.binance.com/en/support/announcement/detail/$code",
            publishedAtMillis = publishedAtMillis,
            sourceTimestampConfidence = if (parseBinanceTitleDateMillis(title) != null) {
                SourceTimestampConfidence.ESTIMATED
            } else {
                SourceTimestampConfidence.UNKNOWN
            }
        )
    }
}

/**
 * 解析 OKX Help Center 公告页 HTML。
 */
internal fun parseOkxAnnouncementRecords(
    html: String,
    nowMillis: Long = System.currentTimeMillis()
): List<AnnouncementRecord> {
    val document = Jsoup.parse(html, "https://www.okx.com")
    return document.select("a[href^=/help/], a[href^=https://www.okx.com/help/]")
        .mapNotNull { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val title = anchor.text().trim()
            if (title.isBlank()) return@mapNotNull null
            if (href.contains("/section/") || href.endsWith("/help") || href.contains("/page/")) return@mapNotNull null

            val contextText = buildString {
                append(anchor.parent()?.text().orEmpty())
                append(' ')
                append(anchor.parents().take(3).joinToString(" ") { it.text() })
            }
            val published = parseOkxPublishedDateMillis(contextText)
            AnnouncementRecord(
                id = "okx:${href.substringAfterLast('/')}",
                title = title,
                url = if (href.startsWith("http")) href else "https://www.okx.com$href",
                publishedAtMillis = published?.first ?: nowMillis,
                sourceTimestampConfidence = published?.second ?: SourceTimestampConfidence.UNKNOWN,
                snippet = contextText.take(280)
            )
        }
        .distinctBy { it.url }
}
