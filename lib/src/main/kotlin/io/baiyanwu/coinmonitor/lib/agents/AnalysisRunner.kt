package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * 宿主侧更容易消费的分析输入模型。
 */
data class AnalysisRunInput(
    val request: AnalysisRequest,
    val seedContext: AnalysisContext,
    val runtimeOptions: AnalysisRuntimeOptions = AnalysisRuntimeOptions()
)

/**
 * 标准完整分析结果，适合外部运行时一次性消费。
 */
data class AnalysisRunResult(
    val request: AnalysisRequest,
    val finalContext: AnalysisContext,
    val events: List<AgentEvent>,
    val completedResults: Map<AgentId, AgentResult>,
    val failures: List<AgentEvent.Failed>
) {
    /**
     * 返回最终主结果，优先使用 `synthesis_agent` 的结果。
     */
    val finalResult: AgentResult?
        get() = completedResults[AgentId.SYNTHESIS]
            ?: completedResults[AgentId.MARKET]
            ?: completedResults[AgentId.INDICATOR]
}

/**
 * 面向宿主运行时的统一分析入口。
 */
interface AnalysisRunner {
    /**
     * 以事件流形式执行完整分析。
     */
    fun stream(input: AnalysisRunInput): Flow<AgentEvent>

    /**
     * 执行完整分析并返回聚合结果。
     */
    suspend fun run(input: AnalysisRunInput): AnalysisRunResult
}

/**
 * 默认 runner，将请求构造、调度和结果聚合收口到统一入口。
 */
class DefaultAnalysisRunner(
    private val orchestrator: AnalysisOrchestrator
) : AnalysisRunner {
    override fun stream(input: AnalysisRunInput): Flow<AgentEvent> {
        return flow {
            input.runtimeOptions.traceListener.onTrace(
                AnalysisTraceEvent.RunStarted(input.request.requestId)
            )
            emitAll(
                orchestrator.run(
                    request = input.request,
                    seedContext = input.seedContext,
                    runtimeOptions = input.runtimeOptions
                )
            )
        }
    }

    override suspend fun run(input: AnalysisRunInput): AnalysisRunResult {
        val events = stream(input).toList()
        val result = aggregateRunResult(
            request = input.request,
            seedContext = input.seedContext,
            events = events
        )
        input.runtimeOptions.traceListener.onTrace(
            AnalysisTraceEvent.RunFinished(
                requestId = input.request.requestId,
                completedAgentIds = result.completedResults.keys,
                failureCount = result.failures.size
            )
        )
        return result
    }
}

/**
 * 用于快速组装默认运行链路的 facade。
 */
class AnalysisService(
    marketSourceAdapters: List<MarketSourceAdapter> = emptyList(),
    synthesisEngine: SynthesisEngine = RuleBasedSynthesisEngine()
) {
    private val runner: AnalysisRunner = DefaultAnalysisRunner(
        orchestrator = StandardAnalysisOrchestrator(
            DefaultAgentSet(
                marketSourceAdapters = marketSourceAdapters,
                synthesisEngine = synthesisEngine
            ).registry()
        )
    )

    /**
     * 以事件流形式运行分析。
     */
    fun stream(input: AnalysisRunInput): Flow<AgentEvent> {
        return runner.stream(input)
    }

    /**
     * 运行分析并返回聚合后的最终结果。
     */
    suspend fun run(input: AnalysisRunInput): AnalysisRunResult {
        return runner.run(input)
    }
}

/**
 * 将事件序列和初始上下文聚合成最终运行结果。
 */
fun aggregateRunResult(
    request: AnalysisRequest,
    seedContext: AnalysisContext,
    events: List<AgentEvent>
): AnalysisRunResult {
    var finalContext = seedContext
    val completedResults = linkedMapOf<AgentId, AgentResult>()
    val failures = mutableListOf<AgentEvent.Failed>()

    events.forEach { event ->
        when (event) {
            is AgentEvent.Completed -> {
                completedResults[event.agentId] = event.result
                finalContext = finalContext.apply(event.result)
            }

            is AgentEvent.Failed -> failures += event
            is AgentEvent.Partial,
            is AgentEvent.Started -> Unit
        }
    }

    return AnalysisRunResult(
        request = request,
        finalContext = finalContext,
        events = events,
        completedResults = completedResults,
        failures = failures
    )
}
