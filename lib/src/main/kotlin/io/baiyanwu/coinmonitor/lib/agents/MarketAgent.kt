package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 默认市场情报 agent。
 */
class DefaultMarketAgent(
    private val adapters: List<MarketSourceAdapter>
) : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.MARKET,
        displayName = "Market Agent",
        description = "Collects low-noise market evidence from registered sources.",
        capabilities = setOf(
            AgentCapability.MARKET_INTELLIGENCE,
            AgentCapability.STREAMING_OUTPUT
        ),
        networkMode = AgentNetworkMode.USER_DIRECT
    )

    /**
     * 按策略执行低噪音市场信息拉取、排序和归一化。
     */
    override fun execute(input: AgentInput): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started(spec.id, "Collecting low-noise market evidence"))

        val request = input.request
        val marketConfig = request.config.market
        val sourcePolicy = marketConfig.sourcePolicy
        if (!request.allowNetwork) {
            emit(
                AgentEvent.Completed(
                    agentId = spec.id,
                    result = AgentResult(
                        agentId = spec.id,
                        summary = "Market analysis skipped because network access is disabled.",
                        confidence = 0.2,
                        warnings = listOf("allowNetwork=false")
                    )
                )
            )
            return@flow
        }

        val selectedAdapters = adapters
            .filter { adapter -> sourcePolicy.isSourceEnabled(adapter.spec) }
            .sortedBy { adapter -> sourcePolicy.resolvePriority(adapter.spec) }

        if (selectedAdapters.isEmpty()) {
            emit(
                AgentEvent.Completed(
                    agentId = spec.id,
                    result = AgentResult(
                        agentId = spec.id,
                        summary = "No market source adapters are registered for the requested policy.",
                        confidence = 0.1,
                        warnings = listOf("No adapters matched the market source policy.")
                    )
                )
            )
            return@flow
        }

        val scopes = DefaultMarketScopePolicy.resolve(request)
        val query = MarketSourceQuery(
            asset = request.asset,
            horizon = request.horizon,
            scopes = scopes,
            limit = sourcePolicy.maxItemsPerSource,
            language = request.preferredLanguage,
            publishedAfterMillis = marketConfig.publishedAfterMillis
        )
        val evidence = coroutineScope {
            selectedAdapters.map { adapter ->
                async {
                    val maxItems = sourcePolicy.resolveMaxItems(adapter.spec)
                    runCatching { adapter.fetch(query) }.getOrDefault(emptyList())
                        .take(maxItems)
                }
            }.awaitAll()
                .flatten()
        }

        val ranked = evidence
            .distinctBy { it.url.ifBlank { "${it.sourceId}:${it.title}" } }
            .sortedByDescending { rankEvidence(request.asset, it) }

        val topEvidence = ranked.take(marketConfig.maxTotalEvidence)
        val summary = if (topEvidence.isEmpty()) {
            "No relevant low-noise market evidence was found for ${request.asset.symbol}."
        } else {
            buildString {
                append("Collected ")
                append(topEvidence.size)
                append(" low-noise evidence items for ")
                append(request.asset.symbol)
                append(". Top items: ")
                append(topEvidence.take(3).joinToString { it.title })
                append('.')
            }
        }

        emit(
            AgentEvent.Completed(
                agentId = spec.id,
                result = AgentResult(
                    agentId = spec.id,
                    summary = summary,
                    confidence = if (topEvidence.isEmpty()) 0.25 else 0.7,
                    typedOutput = MarketAgentOutput(
                        scopes = scopes,
                        evidenceCount = topEvidence.size,
                        sourceCount = selectedAdapters.size,
                        selectionMode = sourcePolicy.selectionMode,
                        sourceIds = selectedAdapters.map { it.spec.id },
                        sourceCapabilities = selectedAdapters.flatMap { it.spec.capabilities }.toSet(),
                        authRequiredSourceCount = selectedAdapters.count {
                            it.spec.requiresAuthentication || it.spec.authProfile.required
                        },
                        topTitles = topEvidence.take(5).map { it.title }
                    ),
                    structuredPayload = buildJsonObject {
                        put("scope", buildJsonArray {
                            scopes.forEach { add(JsonPrimitive(it.name)) }
                        })
                        put("evidenceCount", topEvidence.size)
                        put("sourceCount", selectedAdapters.size)
                        put("selectionMode", sourcePolicy.selectionMode.name)
                        put("sourceIds", buildJsonArray {
                            selectedAdapters.forEach { add(JsonPrimitive(it.spec.id)) }
                        })
                        put("sourceCapabilities", buildJsonArray {
                            selectedAdapters
                                .flatMap { it.spec.capabilities }
                                .distinct()
                                .forEach { add(JsonPrimitive(it.name)) }
                        })
                        put("authRequiredSourceCount", selectedAdapters.count {
                            it.spec.requiresAuthentication || it.spec.authProfile.required
                        })
                        put("topTitles", buildJsonArray {
                            topEvidence.take(5).forEach { add(JsonPrimitive(it.title)) }
                        })
                    },
                    contextPatch = ContextPatch(
                        marketEvidence = topEvidence,
                        sharedContext = SharedContextValues(
                            marketSummary = summary
                        )
                    ),
                    evidenceIds = topEvidence.map { it.id },
                    warnings = if (topEvidence.isEmpty()) {
                        listOf("No evidence matched the selected sources.")
                    } else {
                        emptyList()
                    }
                )
            )
        )
    }
}

/**
 * 对单条市场证据进行相关性和可信度混合打分。
 */
private fun rankEvidence(
    asset: AssetRef,
    evidence: MarketEvidence
): Double {
    val referenceTerms = buildList {
        add(asset.symbol)
        asset.displayName?.let(::add)
        asset.baseSymbol?.let(::add)
        asset.aliases.forEach(::add)
    }.map { it.lowercase() }

    val titleAndSnippet = "${evidence.title} ${evidence.contentSnippet}".lowercase()
    val implicitRelevance = referenceTerms.count { term ->
        term.isNotBlank() && titleAndSnippet.contains(term)
    }.toDouble().coerceAtMost(3.0) / 3.0
    val relevance = evidence.relevanceScore ?: implicitRelevance
    val credibility = evidence.credibilityScore ?: defaultCredibility(evidence.sourceType)
    return (relevance * 0.6) + (credibility * 0.4)
}

/**
 * 为未显式打分的来源类型提供默认可信度。
 */
private fun defaultCredibility(sourceType: MarketSourceType): Double {
    return when (sourceType) {
        MarketSourceType.OFFICIAL_SITE,
        MarketSourceType.OFFICIAL_BLOG,
        MarketSourceType.OFFICIAL_X_ACCOUNT,
        MarketSourceType.EXCHANGE_ANNOUNCEMENT,
        MarketSourceType.CHAIN_ANNOUNCEMENT -> 0.9

        MarketSourceType.NEWSROOM -> 0.75
        MarketSourceType.GITHUB_RELEASE -> 0.8
    }
}
