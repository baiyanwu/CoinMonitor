package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
/**
 * 声明 agent 能力标签，供外部做注册和筛选。
 */
enum class AgentCapability {
    STREAMING_OUTPUT,
    INDICATOR_ANALYSIS,
    MARKET_INTELLIGENCE,
    SYNTHESIS
}

@Serializable
/**
 * 定义某个 agent 的元数据和运行约束。
 */
data class AgentSpec(
    val id: AgentId,
    val displayName: String,
    val description: String,
    val dependencies: Set<AgentId> = emptySet(),
    val capabilities: Set<AgentCapability> = emptySet(),
    val networkMode: AgentNetworkMode = AgentNetworkMode.LOCAL_ONLY,
    val timeoutMillis: Long = 30_000L
)

/**
 * 封装传给 agent 的输入，请求参数和共享上下文都会一起进入执行过程。
 */
data class AgentInput(
    val request: AnalysisRequest,
    val context: AnalysisContext
)

/**
 * 平台无关的分析 agent 协议。
 */
interface AnalysisAgent {
    val spec: AgentSpec

    /**
     * 执行当前 agent，并以事件流形式输出过程和结果。
     */
    fun execute(input: AgentInput): Flow<AgentEvent>
}

/**
 * 统一描述 agent 的运行事件。
 */
sealed interface AgentEvent {
    val agentId: AgentId

    data class Started(
        override val agentId: AgentId,
        val message: String
    ) : AgentEvent

    data class Partial(
        override val agentId: AgentId,
        val chunk: String
    ) : AgentEvent

    data class Completed(
        override val agentId: AgentId,
        val result: AgentResult
    ) : AgentEvent

    data class Failed(
        override val agentId: AgentId,
        val message: String,
        val recoverable: Boolean
    ) : AgentEvent
}
