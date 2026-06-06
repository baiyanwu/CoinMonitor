package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

/**
 * 统一的多 agent 调度入口。
 */
interface AnalysisOrchestrator {
    /**
     * 运行一次完整分析，并持续输出所有 agent 事件。
     */
    fun run(
        request: AnalysisRequest,
        seedContext: AnalysisContext = AnalysisContext(request = request),
        runtimeOptions: AnalysisRuntimeOptions = AnalysisRuntimeOptions()
    ): Flow<AgentEvent>
}

/**
 * 负责按 `AgentId` 管理 agent 实例。
 */
class AgentRegistry(
    agents: Iterable<AnalysisAgent>
) {
    private val agentsById = agents.associateBy { it.spec.id }

    /**
     * 返回指定 id 的 agent，不存在时直接失败。
     */
    fun require(agentId: AgentId): AnalysisAgent {
        return requireNotNull(agentsById[agentId]) {
            "Missing agent registration for $agentId"
        }
    }
}

/**
 * 标准调度器，固定采用“指标 + 市场并行，之后综合”的执行图。
 */
class StandardAnalysisOrchestrator(
    private val registry: AgentRegistry
) : AnalysisOrchestrator {
    override fun run(
        request: AnalysisRequest,
        seedContext: AnalysisContext,
        runtimeOptions: AnalysisRuntimeOptions
    ): Flow<AgentEvent> = channelFlow {
        var context = seedContext
        val policy = runtimeOptions.executionPolicy
        val shouldRunIndicator = request.requiredAgents.contains(AgentId.INDICATOR) ||
            request.requiredAgents.contains(AgentId.SYNTHESIS)
        val shouldRunMarket = request.requiredAgents.contains(AgentId.MARKET) ||
            request.requiredAgents.contains(AgentId.SYNTHESIS)
        val shouldRunSynthesis = request.requiredAgents.contains(AgentId.SYNTHESIS)

        supervisorScope {
            val indicatorOutcome = if (shouldRunIndicator) {
                async {
                    runAgent(
                        agent = registry.require(AgentId.INDICATOR),
                        context = context,
                        runtimeOptions = runtimeOptions
                    )
                }
            } else {
                null
            }
            val marketOutcome = if (shouldRunMarket) {
                async {
                    runAgent(
                        agent = registry.require(AgentId.MARKET),
                        context = context,
                        runtimeOptions = runtimeOptions
                    )
                }
            } else {
                null
            }

            val indicatorRunOutcome = indicatorOutcome?.await()
            val marketRunOutcome = marketOutcome?.await()

            indicatorRunOutcome?.result?.let { result ->
                context = context.apply(result)
            }
            marketRunOutcome?.result?.let { result ->
                context = context.apply(result)
            }

            val hasStageFailure = listOfNotNull(
                indicatorRunOutcome?.failure,
                marketRunOutcome?.failure
            ).isNotEmpty()
            val shouldRunSynthesisAfterFailures = when (policy.partialFailurePolicy) {
                PartialFailurePolicy.CONTINUE_WITH_AVAILABLE_RESULTS -> true
                PartialFailurePolicy.SKIP_SYNTHESIS_ON_UPSTREAM_FAILURE,
                PartialFailurePolicy.ABORT_AFTER_STAGE_FAILURE -> !hasStageFailure
            }

            if (shouldRunSynthesis && shouldRunSynthesisAfterFailures) {
                runAgent(
                    agent = registry.require(AgentId.SYNTHESIS),
                    context = context,
                    runtimeOptions = runtimeOptions
                )
            }
        }
    }

    /**
     * 在当前事件流作用域中运行单个 agent，并收集终态。
     */
    private suspend fun ProducerScope<AgentEvent>.runAgent(
        agent: AnalysisAgent,
        context: AnalysisContext,
        runtimeOptions: AnalysisRuntimeOptions
    ): AgentRunOutcome {
        val traceListener = runtimeOptions.traceListener
        val policy = runtimeOptions.executionPolicy
        val maxAttempts = policy.resolveMaxAttempts(agent.spec)
        var lastOutcome = AgentRunOutcome()

        repeat(maxAttempts) { index ->
            val attempt = index + 1
            traceListener.onTrace(
                AnalysisTraceEvent.AgentAttemptStarted(
                    agentId = agent.spec.id,
                    attempt = attempt,
                    maxAttempts = maxAttempts
                )
            )

            lastOutcome = executeAttempt(
                agent = agent,
                context = context,
                runtimeOptions = runtimeOptions,
                attempt = attempt,
                maxAttempts = maxAttempts
            )
            traceListener.onTrace(
                AnalysisTraceEvent.AgentAttemptFinished(
                    agentId = agent.spec.id,
                    attempt = attempt,
                    success = lastOutcome.result != null,
                    message = lastOutcome.failure?.message
                )
            )
            if (lastOutcome.result != null) {
                return lastOutcome
            }

            val failure = lastOutcome.failure ?: return lastOutcome
            val shouldRetry = attempt < maxAttempts &&
                (!policy.retryOnRecoverableFailureOnly || failure.recoverable)
            if (!shouldRetry) {
                return lastOutcome
            }
        }
        return lastOutcome
    }

    /**
     * 执行某个 agent 的单次尝试，并应用超时。
     */
    private suspend fun ProducerScope<AgentEvent>.executeAttempt(
        agent: AnalysisAgent,
        context: AnalysisContext,
        runtimeOptions: AnalysisRuntimeOptions,
        attempt: Int,
        maxAttempts: Int
    ): AgentRunOutcome {
        val timeoutMillis = runtimeOptions.executionPolicy.resolveTimeoutMillis(agent.spec)
        return try {
            if (timeoutMillis != null) {
                withTimeout(timeoutMillis) {
                    collectAttemptEvents(agent, context, runtimeOptions)
                }
            } else {
                collectAttemptEvents(agent, context, runtimeOptions)
            }
        } catch (error: TimeoutCancellationException) {
            val failure = AgentEvent.Failed(
                agentId = agent.spec.id,
                message = "Agent timed out after ${timeoutMillis ?: 0}ms",
                recoverable = attempt < maxAttempts
            )
            send(failure)
            runtimeOptions.traceListener.onTrace(AnalysisTraceEvent.EventObserved(failure))
            AgentRunOutcome(failure = failure)
        } catch (error: Throwable) {
            val failure = AgentEvent.Failed(
                agentId = agent.spec.id,
                message = error.message ?: "Agent execution failed",
                recoverable = attempt < maxAttempts
            )
            send(failure)
            runtimeOptions.traceListener.onTrace(AnalysisTraceEvent.EventObserved(failure))
            AgentRunOutcome(failure = failure)
        }
    }

    /**
     * 收集单次尝试中的事件。
     */
    private suspend fun ProducerScope<AgentEvent>.collectAttemptEvents(
        agent: AnalysisAgent,
        context: AnalysisContext,
        runtimeOptions: AnalysisRuntimeOptions
    ): AgentRunOutcome {
        var completed: AgentResult? = null
        var failed: AgentEvent.Failed? = null
        agent.execute(
            AgentInput(
                request = context.request,
                context = context
            )
        ).collect { event ->
            send(event)
            runtimeOptions.traceListener.onTrace(AnalysisTraceEvent.EventObserved(event))
            when (event) {
                is AgentEvent.Completed -> completed = event.result
                is AgentEvent.Failed -> failed = event
                is AgentEvent.Partial,
                is AgentEvent.Started -> Unit
            }
        }
        if (completed == null && failed == null) {
            failed = AgentEvent.Failed(
                agentId = agent.spec.id,
                message = "Agent completed without a terminal event",
                recoverable = false
            )
            send(failed!!)
            runtimeOptions.traceListener.onTrace(AnalysisTraceEvent.EventObserved(failed!!))
        }
        return AgentRunOutcome(
            result = completed,
            failure = failed
        )
    }

    private data class AgentRunOutcome(
        val result: AgentResult? = null,
        val failure: AgentEvent.Failed? = null
    )
}
