package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DefaultAgentSetTest {
    @Test
    fun `runner retries failed agent and emits trace events`() = runBlocking {
        val traces = mutableListOf<AnalysisTraceEvent>()
        val runner = DefaultAnalysisRunner(
            orchestrator = StandardAnalysisOrchestrator(
                AgentRegistry(
                    listOf(
                        FlakyIndicatorAgent(),
                        StableMarketAgent(),
                        StableSynthesisAgent()
                    )
                )
            )
        )
        val request = AnalysisRequest(
            requestId = "retry-1",
            userPrompt = "Analyze BTC",
            asset = AssetRef(symbol = "BTC/USDT", displayName = "Bitcoin"),
            intervalLabel = "1H"
        )
        val input = AnalysisRunInput(
            request = request,
            seedContext = AnalysisContext(request = request),
            runtimeOptions = AnalysisRuntimeOptions(
                executionPolicy = AnalysisExecutionPolicy(
                    defaultMaxAttempts = 2
                ),
                traceListener = AnalysisTraceListener { traces += it }
            )
        )

        val result = runner.run(input)

        assertTrue(result.completedResults.containsKey(AgentId.INDICATOR))
        assertTrue(result.failures.any { it.agentId == AgentId.INDICATOR })
        assertTrue(traces.any { it is AnalysisTraceEvent.RunStarted })
        assertTrue(
            traces.filterIsInstance<AnalysisTraceEvent.AgentAttemptStarted>()
                .count { it.agentId == AgentId.INDICATOR } == 2
        )
        assertTrue(traces.any { it is AnalysisTraceEvent.RunFinished })
    }

    @Test
    fun `runner applies timeout to slow agents`() = runBlocking {
        val runner = DefaultAnalysisRunner(
            orchestrator = StandardAnalysisOrchestrator(
                AgentRegistry(
                    listOf(
                        StableIndicatorAgent(),
                        SlowMarketAgent(),
                        StableSynthesisAgent()
                    )
                )
            )
        )
        val request = AnalysisRequest(
            requestId = "timeout-1",
            userPrompt = "Analyze BTC",
            asset = AssetRef(symbol = "BTC/USDT", displayName = "Bitcoin"),
            intervalLabel = "1H",
            requiredAgents = setOf(AgentId.MARKET)
        )
        val result = runner.run(
            AnalysisRunInput(
                request = request,
                seedContext = AnalysisContext(request = request),
                runtimeOptions = AnalysisRuntimeOptions(
                    executionPolicy = AnalysisExecutionPolicy(
                        agentTimeoutMillis = mapOf(AgentId.MARKET to 10L)
                    )
                )
            )
        )

        assertTrue(result.failures.any { it.agentId == AgentId.MARKET })
        assertTrue(result.completedResults.isEmpty())
    }

    @Test
    fun `runner skips synthesis on upstream failure when policy requires it`() = runBlocking {
        val runner = DefaultAnalysisRunner(
            orchestrator = StandardAnalysisOrchestrator(
                AgentRegistry(
                    listOf(
                        StableIndicatorAgent(),
                        FailingMarketAgent(),
                        StableSynthesisAgent()
                    )
                )
            )
        )
        val request = AnalysisRequest(
            requestId = "skip-synth-1",
            userPrompt = "Analyze BTC",
            asset = AssetRef(symbol = "BTC/USDT", displayName = "Bitcoin"),
            intervalLabel = "1H"
        )
        val result = runner.run(
            AnalysisRunInput(
                request = request,
                seedContext = AnalysisContext(request = request),
                runtimeOptions = AnalysisRuntimeOptions(
                    executionPolicy = AnalysisExecutionPolicy(
                        partialFailurePolicy = PartialFailurePolicy.SKIP_SYNTHESIS_ON_UPSTREAM_FAILURE
                    )
                )
            )
        )

        assertTrue(result.completedResults.containsKey(AgentId.INDICATOR))
        assertTrue(result.failures.any { it.agentId == AgentId.MARKET })
        assertTrue(!result.completedResults.containsKey(AgentId.SYNTHESIS))
    }

    @Test
    fun `analysis service runs a complete external flow`() = runBlocking {
        val service = AnalysisService(
            marketSourceAdapters = listOf(FakeMarketAdapter())
        )
        val input = AnalysisRequestBuilder()
            .requestId("service-1")
            .userPrompt("Analyze BTC")
            .asset(
                AssetRef(
                    symbol = "BTC/USDT",
                    displayName = "Bitcoin",
                    baseSymbol = "BTC",
                    aliases = listOf("Bitcoin")
                )
            )
            .intervalLabel("1H")
            .candles(fakeCandles())
            .build()

        val result = service.run(input)

        assertNotNull(result.finalResult)
        assertTrue(result.completedResults.containsKey(AgentId.INDICATOR))
        assertTrue(result.completedResults.containsKey(AgentId.MARKET))
        assertTrue(result.completedResults.containsKey(AgentId.SYNTHESIS))
        assertEquals("service-1", result.request.requestId)
        assertTrue(result.finalContext.sharedContext.indicatorSummary?.isNotBlank() == true)
        assertTrue(result.finalContext.sharedContext.marketSummary?.isNotBlank() == true)
    }

    @Test
    fun `orchestrator completes all default agents`() = runBlocking {
        val orchestrator = StandardAnalysisOrchestrator(
            DefaultAgentSet(
                marketSourceAdapters = listOf(FakeMarketAdapter())
            ).registry()
        )
        val request = AnalysisRequest(
            requestId = "req-1",
            userPrompt = "Analyze BTC",
            asset = AssetRef(
                symbol = "BTC/USDT",
                displayName = "Bitcoin",
                baseSymbol = "BTC",
                aliases = listOf("Bitcoin")
            ),
            intervalLabel = "1H"
        )
        val context = AnalysisContext(
            request = request,
            candles = fakeCandles()
        )

        val events = orchestrator.run(
            request = request,
            seedContext = context
        ).toList()

        val completedAgentIds = events.filterIsInstance<AgentEvent.Completed>().map { it.agentId }
        assertEquals(3, completedAgentIds.size)
        assertTrue(completedAgentIds.contains(AgentId.INDICATOR))
        assertTrue(completedAgentIds.contains(AgentId.MARKET))
        assertTrue(completedAgentIds.contains(AgentId.SYNTHESIS))
        assertTrue(
            events.filterIsInstance<AgentEvent.Completed>()
                .first { it.agentId == AgentId.INDICATOR }
                .result.typedOutput is IndicatorAgentOutput
        )
        assertTrue(
            events.filterIsInstance<AgentEvent.Completed>()
                .first { it.agentId == AgentId.MARKET }
                .result.typedOutput is MarketAgentOutput
        )
        assertTrue(
            events.filterIsInstance<AgentEvent.Completed>()
                .first { it.agentId == AgentId.SYNTHESIS }
                .result.typedOutput is SynthesisAgentOutput
        )
        assertTrue(
            events.filterIsInstance<AgentEvent.Completed>()
                .first { it.agentId == AgentId.SYNTHESIS }
                .result.summary
                .contains("Bitcoin")
        )
    }

    @Test
    fun `market source overrides can force explicit source selection`() = runBlocking {
        val orchestrator = StandardAnalysisOrchestrator(
            DefaultAgentSet(
                marketSourceAdapters = listOf(
                    FakeMarketAdapter(),
                    SecondaryMarketAdapter()
                )
            ).registry()
        )
        val request = AnalysisRequest(
            requestId = "req-2",
            userPrompt = "Analyze BTC",
            asset = AssetRef(
                symbol = "BTC/USDT",
                displayName = "Bitcoin",
                baseSymbol = "BTC",
                aliases = listOf("Bitcoin")
            ),
            intervalLabel = "1H",
            config = AnalysisConfig(
                market = MarketAgentConfig(
                    sourcePolicy = MarketSourceSelectionPolicy(
                        selectionMode = MarketSourceSelectionMode.EXPLICIT_ONLY,
                        sourceOverrides = listOf(
                            MarketSourceOverride(
                                sourceId = "secondary-news",
                                enabled = true,
                                priority = 1,
                                maxItems = 1
                            )
                        )
                    ),
                    maxTotalEvidence = 2
                )
            ),
            requiredAgents = setOf(AgentId.MARKET)
        )
        val context = AnalysisContext(
            request = request,
            candles = fakeCandles()
        )

        val marketResult = orchestrator.run(
            request = request,
            seedContext = context
        ).toList()
            .filterIsInstance<AgentEvent.Completed>()
            .first { it.agentId == AgentId.MARKET }
            .result

        assertTrue(marketResult.summary.contains("secondary source update"))
        assertEquals(listOf("ev-2"), marketResult.evidenceIds)
        val typedOutput = marketResult.typedOutput as MarketAgentOutput
        assertEquals(MarketSourceSelectionMode.EXPLICIT_ONLY, typedOutput.selectionMode)
        assertEquals(listOf("secondary-news"), typedOutput.sourceIds)
    }

    private fun fakeCandles(): List<CandleSnapshot> {
        return listOf(
            CandleSnapshot(1, 100.0, 102.0, 99.0, 101.0, 80.0),
            CandleSnapshot(2, 101.0, 103.0, 100.0, 102.0, 85.0),
            CandleSnapshot(3, 102.0, 104.0, 101.0, 103.5, 90.0),
            CandleSnapshot(4, 103.5, 105.0, 102.5, 104.0, 95.0),
            CandleSnapshot(5, 104.0, 106.0, 103.0, 105.5, 110.0),
            CandleSnapshot(6, 105.5, 107.0, 104.5, 106.0, 120.0),
            CandleSnapshot(7, 106.0, 108.0, 105.0, 107.5, 125.0),
            CandleSnapshot(8, 107.5, 109.0, 106.5, 108.0, 130.0),
            CandleSnapshot(9, 108.0, 110.0, 107.0, 109.0, 135.0),
            CandleSnapshot(10, 109.0, 111.0, 108.0, 110.0, 140.0)
        )
    }
}

private class FakeMarketAdapter : MarketSourceAdapter {
    override val spec: MarketSourceSpec = MarketSourceSpec(
        id = "official-blog",
        displayName = "Official Blog",
        type = MarketSourceType.OFFICIAL_BLOG,
        capabilities = setOf(
            MarketSourceCapability.ASSET_KEYWORD_LOOKUP,
            MarketSourceCapability.OFFICIAL_ANNOUNCEMENT_TRACKING
        ),
        rateLimitHint = MarketSourceRateLimitHint(
            recommendedRequestsPerMinute = 30,
            supportsBurst = true,
            cacheTtlMillis = 60_000L
        )
    )

    override suspend fun fetch(query: MarketSourceQuery): List<MarketEvidence> {
        return listOf(
            MarketEvidence(
                id = "ev-1",
                sourceId = spec.id,
                sourceType = spec.type,
                title = "Bitcoin ecosystem update",
                url = "https://example.com/btc-update",
                publishedAtMillis = 1L,
                contentSnippet = "Bitcoin infrastructure expansion update",
                eventType = MarketEventType.ECOSYSTEM_UPDATE,
                impactDirection = MarketImpactDirection.POSITIVE,
                impactStrength = MarketImpactStrength.MEDIUM,
                freshness = MarketEvidenceFreshness.RECENT,
                sourceTimestampConfidence = SourceTimestampConfidence.EXACT,
                relatedSymbols = listOf("BTC"),
                relevanceScore = 0.9,
                credibilityScore = 0.9
            )
        )
    }
}

private class SecondaryMarketAdapter : MarketSourceAdapter {
    override val spec: MarketSourceSpec = MarketSourceSpec(
        id = "secondary-news",
        displayName = "Secondary News",
        type = MarketSourceType.NEWSROOM,
        capabilities = setOf(MarketSourceCapability.ASSET_KEYWORD_LOOKUP),
        authProfile = MarketSourceAuthProfile(
            mode = MarketSourceAuthMode.USER_API_KEY,
            required = true,
            credentialHint = "Provide a personal newsroom API key."
        ),
        requiresAuthentication = true
    )

    override suspend fun fetch(query: MarketSourceQuery): List<MarketEvidence> {
        return listOf(
            MarketEvidence(
                id = "ev-2",
                sourceId = spec.id,
                sourceType = spec.type,
                title = "secondary source update",
                url = "https://example.com/secondary",
                publishedAtMillis = 2L,
                contentSnippet = "Bitcoin secondary source confirmation",
                eventType = MarketEventType.ANNOUNCEMENT,
                impactDirection = MarketImpactDirection.POSITIVE,
                impactStrength = MarketImpactStrength.LOW,
                freshness = MarketEvidenceFreshness.RECENT,
                sourceTimestampConfidence = SourceTimestampConfidence.EXACT,
                relatedSymbols = listOf("BTC"),
                relevanceScore = 0.8,
                credibilityScore = 0.7
            )
        )
    }
}

private class StableIndicatorAgent : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.INDICATOR,
        displayName = "Stable Indicator",
        description = "Test indicator agent"
    )

    override fun execute(input: AgentInput) = kotlinx.coroutines.flow.flow {
        emit(AgentEvent.Started(spec.id, "stable indicator"))
        emit(
            AgentEvent.Completed(
                spec.id,
                AgentResult(
                    agentId = spec.id,
                    summary = "indicator ok"
                )
            )
        )
    }
}

private class StableMarketAgent : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.MARKET,
        displayName = "Stable Market",
        description = "Test market agent"
    )

    override fun execute(input: AgentInput) = kotlinx.coroutines.flow.flow {
        emit(AgentEvent.Started(spec.id, "stable market"))
        emit(
            AgentEvent.Completed(
                spec.id,
                AgentResult(
                    agentId = spec.id,
                    summary = "market ok"
                )
            )
        )
    }
}

private class StableSynthesisAgent : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.SYNTHESIS,
        displayName = "Stable Synthesis",
        description = "Test synthesis agent"
    )

    override fun execute(input: AgentInput) = kotlinx.coroutines.flow.flow {
        emit(AgentEvent.Started(spec.id, "stable synthesis"))
        emit(
            AgentEvent.Completed(
                spec.id,
                AgentResult(
                    agentId = spec.id,
                    summary = "synthesis ok"
                )
            )
        )
    }
}

private class FlakyIndicatorAgent : AnalysisAgent {
    private val attempts = AtomicInteger(0)

    override val spec: AgentSpec = AgentSpec(
        id = AgentId.INDICATOR,
        displayName = "Flaky Indicator",
        description = "Fails once before succeeding"
    )

    override fun execute(input: AgentInput) = kotlinx.coroutines.flow.flow {
        val currentAttempt = attempts.incrementAndGet()
        emit(AgentEvent.Started(spec.id, "flaky attempt $currentAttempt"))
        if (currentAttempt == 1) {
            error("transient indicator failure")
        }
        emit(
            AgentEvent.Completed(
                spec.id,
                AgentResult(
                    agentId = spec.id,
                    summary = "indicator recovered"
                )
            )
        )
    }
}

private class SlowMarketAgent : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.MARKET,
        displayName = "Slow Market",
        description = "Sleeps to trigger timeout",
        timeoutMillis = 5_000L
    )

    override fun execute(input: AgentInput) = kotlinx.coroutines.flow.flow {
        emit(AgentEvent.Started(spec.id, "slow market"))
        delay(100L)
        emit(
            AgentEvent.Completed(
                spec.id,
                AgentResult(
                    agentId = spec.id,
                    summary = "market slow success"
                )
            )
        )
    }
}

private class FailingMarketAgent : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.MARKET,
        displayName = "Failing Market",
        description = "Always fails"
    )

    override fun execute(input: AgentInput) = kotlinx.coroutines.flow.flow {
        emit(AgentEvent.Started(spec.id, "failing market"))
        error("market failure")
    }
}
