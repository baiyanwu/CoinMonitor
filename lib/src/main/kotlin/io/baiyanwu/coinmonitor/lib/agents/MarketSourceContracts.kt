package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.serialization.Serializable

@Serializable
/**
 * 低噪音市场信息源的标准分类。
 */
enum class MarketSourceType {
    OFFICIAL_SITE,
    OFFICIAL_BLOG,
    OFFICIAL_X_ACCOUNT,
    EXCHANGE_ANNOUNCEMENT,
    CHAIN_ANNOUNCEMENT,
    NEWSROOM,
    GITHUB_RELEASE
}

@Serializable
/**
 * 描述单个市场信息源适配器的元信息。
 */
enum class MarketSourceCapability {
    ASSET_KEYWORD_LOOKUP,
    OFFICIAL_ACCOUNT_TRACKING,
    OFFICIAL_ANNOUNCEMENT_TRACKING,
    CHAIN_ECOSYSTEM_TRACKING,
    RELEASE_TRACKING
}

@Serializable
/**
 * 描述某个 source 采用何种认证方式。
 */
enum class MarketSourceAuthMode {
    NONE,
    USER_API_KEY,
    USER_SESSION,
    USER_COOKIE
}

@Serializable
/**
 * 描述 source 的认证画像。
 */
data class MarketSourceAuthProfile(
    val mode: MarketSourceAuthMode = MarketSourceAuthMode.NONE,
    val required: Boolean = false,
    val credentialHint: String? = null
)

@Serializable
/**
 * 描述 source 的限频提示，供外部运行时做调度和缓存策略。
 */
data class MarketSourceRateLimitHint(
    val recommendedRequestsPerMinute: Int? = null,
    val supportsBurst: Boolean = false,
    val cacheTtlMillis: Long? = null
)

@Serializable
/**
 * 描述单个市场信息源适配器的元信息。
 */
data class MarketSourceSpec(
    val id: String,
    val displayName: String,
    val type: MarketSourceType,
    val lowNoise: Boolean = true,
    val requiresAuthentication: Boolean = false,
    val capabilities: Set<MarketSourceCapability> = emptySet(),
    val authProfile: MarketSourceAuthProfile = MarketSourceAuthProfile(),
    val rateLimitHint: MarketSourceRateLimitHint = MarketSourceRateLimitHint()
)

@Serializable
/**
 * 控制来源选择是走默认低噪音集合，还是只使用显式声明的来源。
 */
enum class MarketSourceSelectionMode {
    DEFAULT,
    EXPLICIT_ONLY
}

@Serializable
/**
 * 描述单个 source 的显式启停、优先级和采样覆盖规则。
 */
data class MarketSourceOverride(
    val sourceId: String,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val maxItems: Int? = null
)

@Serializable
/**
 * 描述市场证据所属的事件语义类型。
 */
enum class MarketEventType {
    ANNOUNCEMENT,
    ECOSYSTEM_UPDATE,
    LISTING,
    DELISTING,
    RELEASE,
    SECURITY,
    GOVERNANCE,
    MACRO,
    OTHER
}

@Serializable
/**
 * 描述事件对目标资产的潜在影响方向。
 */
enum class MarketImpactDirection {
    POSITIVE,
    NEGATIVE,
    MIXED,
    NEUTRAL,
    UNKNOWN
}

@Serializable
/**
 * 描述事件影响强度。
 */
enum class MarketImpactStrength {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}

@Serializable
/**
 * 描述当前证据的新鲜度。
 */
enum class MarketEvidenceFreshness {
    LIVE,
    RECENT,
    STALE,
    UNKNOWN
}

@Serializable
/**
 * 描述证据发布时间的可信程度。
 */
enum class SourceTimestampConfidence {
    EXACT,
    ESTIMATED,
    UNKNOWN
}

@Serializable
/**
 * 表示经过归一化后的单条市场证据。
 */
data class MarketEvidence(
    val id: String,
    val sourceId: String,
    val sourceType: MarketSourceType,
    val title: String,
    val url: String,
    val publishedAtMillis: Long,
    val contentSnippet: String,
    val eventType: MarketEventType = MarketEventType.OTHER,
    val impactDirection: MarketImpactDirection = MarketImpactDirection.UNKNOWN,
    val impactStrength: MarketImpactStrength = MarketImpactStrength.UNKNOWN,
    val freshness: MarketEvidenceFreshness = MarketEvidenceFreshness.UNKNOWN,
    val sourceTimestampConfidence: SourceTimestampConfidence = SourceTimestampConfidence.UNKNOWN,
    val relatedSymbols: List<String> = emptyList(),
    val relevanceScore: Double? = null,
    val credibilityScore: Double? = null
)

@Serializable
/**
 * 控制 `MarketAgent` 应该启用哪些来源及每个来源的采样上限。
 */
data class MarketSourceSelectionPolicy(
    val selectionMode: MarketSourceSelectionMode = MarketSourceSelectionMode.DEFAULT,
    val sourceTypes: Set<MarketSourceType> = LowNoiseSourceCatalog.defaultTypes,
    val maxItemsPerSource: Int = 5,
    val includeUnofficialSources: Boolean = false,
    val allowUserDefinedSources: Boolean = true,
    val sourceOverrides: List<MarketSourceOverride> = emptyList()
)

@Serializable
/**
 * 传递给市场信息源适配器的标准查询参数。
 */
data class MarketSourceQuery(
    val asset: AssetRef,
    val horizon: AnalysisHorizon,
    val scopes: Set<MarketScope>,
    val limit: Int,
    val language: String,
    val publishedAfterMillis: Long? = null
)

/**
 * 外部市场信息源的最小抽象。
 */
interface MarketSourceAdapter {
    val spec: MarketSourceSpec

    /**
     * 拉取并返回与当前查询相关的市场证据。
     */
    suspend fun fetch(query: MarketSourceQuery): List<MarketEvidence>
}

/**
 * 判断某个 source 在当前策略下是否应被启用。
 */
fun MarketSourceSelectionPolicy.isSourceEnabled(spec: MarketSourceSpec): Boolean {
    val override = sourceOverrides.firstOrNull { it.sourceId == spec.id }
    return when (selectionMode) {
        MarketSourceSelectionMode.DEFAULT -> {
            (spec.type in sourceTypes) &&
                (includeUnofficialSources || spec.lowNoise) &&
                (override?.enabled != false)
        }

        MarketSourceSelectionMode.EXPLICIT_ONLY -> override?.enabled == true
    }
}

/**
 * 解析当前 source 应使用的优先级，数值越小越优先。
 */
fun MarketSourceSelectionPolicy.resolvePriority(spec: MarketSourceSpec): Int {
    return sourceOverrides.firstOrNull { it.sourceId == spec.id }?.priority ?: 100
}

/**
 * 解析当前 source 最多可返回多少条证据。
 */
fun MarketSourceSelectionPolicy.resolveMaxItems(spec: MarketSourceSpec): Int {
    return sourceOverrides.firstOrNull { it.sourceId == spec.id }?.maxItems
        ?: maxItemsPerSource
}

/**
 * 提供第一阶段默认启用的低噪音来源集合。
 */
object LowNoiseSourceCatalog {
    val defaultTypes: Set<MarketSourceType> = setOf(
        MarketSourceType.OFFICIAL_SITE,
        MarketSourceType.OFFICIAL_BLOG,
        MarketSourceType.OFFICIAL_X_ACCOUNT,
        MarketSourceType.EXCHANGE_ANNOUNCEMENT,
        MarketSourceType.CHAIN_ANNOUNCEMENT,
        MarketSourceType.NEWSROOM,
        MarketSourceType.GITHUB_RELEASE
    )
}

/**
 * 根据分析周期给出默认市场搜索范围。
 */
object DefaultMarketScopePolicy {
    /**
     * 解析当前请求应该落到哪些市场范围层级。
     */
    fun resolve(request: AnalysisRequest): Set<MarketScope> {
        val override = request.config.market.scopeOverride
        if (override.isNotEmpty()) {
            return override
        }
        return when (request.horizon) {
            AnalysisHorizon.INTRADAY -> setOf(MarketScope.DIRECT, MarketScope.ECOSYSTEM)
            AnalysisHorizon.SWING -> setOf(
                MarketScope.DIRECT,
                MarketScope.ECOSYSTEM,
                MarketScope.SECTOR
            )

            AnalysisHorizon.POSITION -> setOf(
                MarketScope.DIRECT,
                MarketScope.ECOSYSTEM,
                MarketScope.SECTOR,
                MarketScope.MACRO
            )
        }
    }
}
