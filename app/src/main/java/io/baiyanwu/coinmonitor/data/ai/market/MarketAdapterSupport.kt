package io.baiyanwu.coinmonitor.data.ai.market

import io.baiyanwu.coinmonitor.lib.agents.AssetRef
import io.baiyanwu.coinmonitor.lib.agents.MarketEvidenceFreshness
import io.baiyanwu.coinmonitor.lib.agents.MarketEventType
import io.baiyanwu.coinmonitor.lib.agents.MarketImpactDirection
import io.baiyanwu.coinmonitor.lib.agents.MarketImpactStrength
import io.baiyanwu.coinmonitor.lib.agents.SourceTimestampConfidence
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * 表示适配器内部统一使用的原始公告条目。
 */
internal data class AnnouncementRecord(
    val id: String,
    val title: String,
    val url: String,
    val publishedAtMillis: Long,
    val sourceTimestampConfidence: SourceTimestampConfidence,
    val snippet: String = ""
)

/**
 * 简单的内存 TTL 缓存，供 source adapter 复用。
 */
internal class TimedCache<T>(
    private val ttlMillis: Long
) {
    private val mutex = Mutex()
    private var entry: CacheEntry<T>? = null

    /**
     * 在缓存可用时返回缓存值，否则执行加载并写回。
     */
    suspend fun getOrLoad(loader: suspend () -> T): T {
        mutex.withLock {
            val current = entry
            if (current != null && System.currentTimeMillis() - current.savedAtMillis < ttlMillis) {
                return current.value
            }
        }

        val fresh = loader()
        mutex.withLock {
            entry = CacheEntry(
                savedAtMillis = System.currentTimeMillis(),
                value = fresh
            )
        }
        return fresh
    }

    private data class CacheEntry<T>(
        val savedAtMillis: Long,
        val value: T
    )
}

/**
 * 生成供交易所公告匹配使用的一组候选关键词。
 */
internal fun AssetRef.searchTerms(): List<String> {
    return buildList {
        add(symbol)
        add(symbol.replace("/", ""))
        baseSymbol?.let(::add)
        displayName?.let(::add)
        tokenAddress?.let(::add)
        aliases.forEach(::add)
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

/**
 * 计算资产和公告文本之间的粗粒度相关性。
 */
internal fun AssetRef.relevanceScore(text: String): Double {
    if (text.isBlank()) return 0.0
    val haystack = text.lowercase(Locale.ROOT)
    val normalizedTokens = haystack
        .replace(Regex("[^a-z0-9]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .toSet()

    val hits = searchTerms().count { term ->
        val normalized = term.lowercase(Locale.ROOT)
        when {
            normalized.length <= 4 -> {
                normalizedTokens.contains(normalized) || haystack.contains(normalized)
            }

            else -> haystack.contains(normalized)
        }
    }
    if (hits == 0) return 0.0
    return (hits.toDouble() / searchTerms().size.toDouble()).coerceIn(0.15, 1.0)
}

/**
 * 判断当前公告是否值得纳入目标资产分析。
 */
internal fun AssetRef.isRelevant(text: String): Boolean {
    return relevanceScore(text) >= 0.2
}

/**
 * 基于标题估算公告事件类型。
 */
internal fun classifyEventType(title: String): MarketEventType {
    val normalized = title.lowercase(Locale.ROOT)
    return when {
        "delist" in normalized -> MarketEventType.DELISTING
        "list " in normalized || "will list" in normalized || "new listing" in normalized -> MarketEventType.LISTING
        "security" in normalized || "hack" in normalized || "exploit" in normalized -> MarketEventType.SECURITY
        "release" in normalized || "upgrade" in normalized -> MarketEventType.RELEASE
        "governance" in normalized || "vote" in normalized -> MarketEventType.GOVERNANCE
        else -> MarketEventType.ANNOUNCEMENT
    }
}

/**
 * 基于标题估算事件影响方向。
 */
internal fun classifyImpactDirection(title: String): MarketImpactDirection {
    val normalized = title.lowercase(Locale.ROOT)
    return when {
        "delist" in normalized || "suspend" in normalized || "maintenance" in normalized ||
            "halt" in normalized || "remove" in normalized || "exploit" in normalized -> MarketImpactDirection.NEGATIVE

        "will list" in normalized || "new listing" in normalized || "launch" in normalized ||
            "support" in normalized || "available" in normalized || "integration" in normalized -> MarketImpactDirection.POSITIVE

        else -> MarketImpactDirection.UNKNOWN
    }
}

/**
 * 基于标题估算事件影响强度。
 */
internal fun classifyImpactStrength(title: String): MarketImpactStrength {
    val normalized = title.lowercase(Locale.ROOT)
    return when {
        "delist" in normalized || "will list" in normalized || "new listing" in normalized ||
            "deposit/withdrawal suspension" in normalized || "suspend" in normalized -> MarketImpactStrength.HIGH

        "maintenance" in normalized || "upgrade" in normalized || "integration" in normalized -> MarketImpactStrength.MEDIUM
        else -> MarketImpactStrength.LOW
    }
}

/**
 * 根据发布时间计算证据新鲜度。
 */
internal fun resolveFreshness(
    publishedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): MarketEvidenceFreshness {
    val ageMillis = (nowMillis - publishedAtMillis).coerceAtLeast(0L)
    return when {
        ageMillis <= 6 * 60 * 60 * 1000L -> MarketEvidenceFreshness.LIVE
        ageMillis <= 3 * 24 * 60 * 60 * 1000L -> MarketEvidenceFreshness.RECENT
        else -> MarketEvidenceFreshness.STALE
    }
}

/**
 * 尝试从 Binance 标题末尾的括号日期中提取发布时间。
 */
internal fun parseBinanceTitleDateMillis(title: String): Long? {
    val rawDate = Regex("\\((\\d{4}-\\d{2}-\\d{2})\\)\\s*$")
        .find(title)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    return runCatching {
        LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}

/**
 * 解析 OKX 公告页里常见的 `Published on 26 Jan 2026` 文本。
 */
internal fun parseOkxPublishedDateMillis(text: String): Pair<Long, SourceTimestampConfidence>? {
    val match = Regex("Published on\\s+(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    val millis = tryParseDate(match) ?: return null
    return millis to SourceTimestampConfidence.ESTIMATED
}

/**
 * 尝试将 ISO 时间串转换成 epoch millis。
 */
internal fun parseIsoInstantMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

/**
 * 将大数字字符串格式化为更易读的摘要片段。
 */
internal fun compactMetric(label: String, value: String?): String? {
    val numeric = value?.toDoubleOrNull() ?: return null
    val formatted = when {
        numeric >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", numeric / 1_000_000_000)
        numeric >= 1_000_000 -> String.format(Locale.US, "%.2fM", numeric / 1_000_000)
        numeric >= 1_000 -> String.format(Locale.US, "%.2fK", numeric / 1_000)
        else -> String.format(Locale.US, "%.2f", numeric)
    }
    return "$label $formatted"
}

private fun tryParseDate(value: String): Long? {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    return try {
        LocalDate.parse(value, formatter)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
