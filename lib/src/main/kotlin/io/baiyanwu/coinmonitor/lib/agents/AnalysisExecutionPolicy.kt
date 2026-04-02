package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.serialization.Serializable

@Serializable
/**
 * 控制当部分 agent 失败时，调度器应如何继续。
 */
enum class PartialFailurePolicy {
    CONTINUE_WITH_AVAILABLE_RESULTS,
    SKIP_SYNTHESIS_ON_UPSTREAM_FAILURE,
    ABORT_AFTER_STAGE_FAILURE
}

@Serializable
/**
 * 控制一次分析运行中的超时、重试和失败处理策略。
 */
data class AnalysisExecutionPolicy(
    val defaultAgentTimeoutMillis: Long? = null,
    val agentTimeoutMillis: Map<AgentId, Long> = emptyMap(),
    val defaultMaxAttempts: Int = 1,
    val agentMaxAttempts: Map<AgentId, Int> = emptyMap(),
    val retryOnRecoverableFailureOnly: Boolean = true,
    val partialFailurePolicy: PartialFailurePolicy = PartialFailurePolicy.CONTINUE_WITH_AVAILABLE_RESULTS
) {
    /**
     * 解析某个 agent 的有效超时。
     */
    fun resolveTimeoutMillis(spec: AgentSpec): Long? {
        return agentTimeoutMillis[spec.id]
            ?: defaultAgentTimeoutMillis
            ?: spec.timeoutMillis.takeIf { it > 0 }
    }

    /**
     * 解析某个 agent 的最大尝试次数。
     */
    fun resolveMaxAttempts(spec: AgentSpec): Int {
        return (agentMaxAttempts[spec.id] ?: defaultMaxAttempts).coerceAtLeast(1)
    }
}

/**
 * 运行时 trace 事件。
 */
sealed interface AnalysisTraceEvent {
    data class RunStarted(
        val requestId: String
    ) : AnalysisTraceEvent

    data class AgentAttemptStarted(
        val agentId: AgentId,
        val attempt: Int,
        val maxAttempts: Int
    ) : AnalysisTraceEvent

    data class AgentAttemptFinished(
        val agentId: AgentId,
        val attempt: Int,
        val success: Boolean,
        val message: String? = null
    ) : AnalysisTraceEvent

    data class EventObserved(
        val event: AgentEvent
    ) : AnalysisTraceEvent

    data class RunFinished(
        val requestId: String,
        val completedAgentIds: Set<AgentId>,
        val failureCount: Int
    ) : AnalysisTraceEvent
}

/**
 * 宿主可注入的 trace / log hook。
 */
fun interface AnalysisTraceListener {
    fun onTrace(event: AnalysisTraceEvent)
}

/**
 * 默认空 trace listener。
 */
object NoopAnalysisTraceListener : AnalysisTraceListener {
    override fun onTrace(event: AnalysisTraceEvent) = Unit
}

/**
 * 宿主运行时可配置的执行策略和 hook。
 */
data class AnalysisRuntimeOptions(
    val executionPolicy: AnalysisExecutionPolicy = AnalysisExecutionPolicy(),
    val traceListener: AnalysisTraceListener = NoopAnalysisTraceListener
)
