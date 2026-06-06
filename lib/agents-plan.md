# Agents Plan

## Goal

将当前项目中的 AI 能力抽离为一个平台无关的 `lib`，把“指标分析、市场情报、综合判断”统一建模为 `agent`，避免后续继续把逻辑堆进某一个 UI、平台页面或仓库实现里。

这个 `lib` 的默认定位不是“作者托管的服务端系统”，而是“客户端本地编排 + 用户自配模型与数据源”的分析框架。

## Why A Lib

- 先把上层 UI 和分析系统解耦，后续 Android / iOS / Desktop / CLI 都能复用相同协议。
- 先把领域模型和 orchestration 固定下来，后面即使改模型供应商、改数据源、改界面，也不用重写分析主干。
- 保持开源项目的边界清晰：默认不依赖作者服务端，用户自己掌控 API Key、模型、数据源和执行路径。

## Module Position

`lib` 只负责 agent 协议、上下文模型、调度骨架、market source 抽象和默认策略。

`lib` 不负责：

- Android UI
- Compose 状态管理
- 某一个平台的本地数据库
- 某一个平台的数据采集实现
- 具体模型请求实现
- 某一个平台专属的缓存或权限处理

这些内容都应该由宿主运行时适配进来。

## Required Agents

三类 agent 必须同时存在，不能再回到“单聊天入口慢慢补能力”的路线：

### `indicator_agent`

- 输入：当前标的、周期、candles、指标参数、已有技术面中间态
- 输出：结构化技术面快照、趋势判断、量价关系、支撑阻力、风险提示
- 网络策略：`LOCAL_ONLY`

### `market_agent`

- 输入：symbol、项目名、别名、链信息、合约地址、分析周期、市场搜索范围
- 输出：结构化市场证据列表、来源、时间、可信度、相关性
- 网络策略：`USER_DIRECT`
- 第一阶段只接低噪音源，不做全站 Twitter 搜索

### `synthesis_agent`

- 输入：`indicator_agent` 结果 + `market_agent` 结果
- 输出：面向用户的最终解释、关键驱动因素、风险、结论摘要
- 网络策略：通常走用户配置模型，保持 `USER_DIRECT`

## Execution Graph

标准执行图固定如下：

1. `indicator_agent` 与 `market_agent` 并行执行
2. 两者结果汇总进共享 `AnalysisContext`
3. `synthesis_agent` 在汇总后的上下文上执行

这个顺序已经在 `StandardAnalysisOrchestrator` 中固化。后面新增别的 agent，也应该挂进图中，而不是改掉三段主干。

## Core Contracts

`lib` 里当前已经落下这些核心协议：

- `AnalysisRequest`
- `AnalysisConfig`
- `IndicatorAgentConfig`
- `MarketAgentConfig`
- `SynthesisAgentConfig`
- `IndicatorSnapshot`
- `SharedContextValues`
- `AgentOutput`
- `IndicatorAgentOutput`
- `MarketAgentOutput`
- `SynthesisAgentOutput`
- `MarketSourceSelectionPolicy`
- `MarketSourceOverride`
- `MarketSourceSpec`
- `AnalysisContext`
- `AnalysisRunInput`
- `AnalysisRunResult`
- `AnalysisRunner`
- `AnalysisService`
- `AnalysisRequestBuilder`
- `AnalysisExecutionPolicy`
- `AnalysisRuntimeOptions`
- `AnalysisTraceListener`
- `AgentSpec`
- `AnalysisAgent`
- `AgentEvent`
- `AgentResult`
- `ContextPatch`
- `AgentRegistry`
- `StandardAnalysisOrchestrator`
- `MarketSourceAdapter`
- `MarketSourceSelectionPolicy`
- `DefaultMarketScopePolicy`

这套协议的重点是：

- 所有分析单元都统一叫 `agent`
- 所有 agent 都走统一输入输出模型
- 所有 agent 都可以流式输出
- 所有 agent 都可以把结构化结果回灌到共享上下文
- 指标快照和共享摘要也优先走 typed 结构
- 所有 agent 都有 typed output，`JsonObject` 只作为扩展槽保留
- 外部运行时已经有稳定入口，不必自己手拼 orchestrator 和 registry
- 执行策略和 trace hook 已经是 `lib` 的一等概念

## Market Design

`market_agent` 第一阶段采用低噪音源策略。当前默认白名单已经在 `LowNoiseSourceCatalog` 中定义：

- 项目官网
- 项目博客
- 官方 X 账号
- 交易所公告
- 链官方公告
- 主流新闻源
- GitHub Release

这部分特意没有做“全站 Twitter 关键词搜索”。原因很简单：

- 噪音高
- 容易命中重名 ticker
- 很难在客户端稳定执行
- 对开源客户端来说，治理成本很高

正确路线是先做 `official handles + low-noise source adapters`，而不是直接做一个难以控制的搜索器。

除了默认低噪音集合之外，当前还支持 typed source override：

- `selectionMode=DEFAULT`
  按来源类型和低噪音策略自动选择
- `selectionMode=EXPLICIT_ONLY`
  只启用 `sourceOverrides` 里显式声明的 source
- `MarketSourceOverride`
  可以单独控制某个 source 的：
  - `enabled`
  - `priority`
  - `maxItems`

这意味着外部运行时不只是“注入一组 adapter”，还可以明确声明“这次分析到底允许哪些 source 参与”。

除此之外，`MarketSourceSpec` 现在也承载更明确的 source 元信息：

- `capabilities`
  表示这个 source 擅长什么，例如：
  - `ASSET_KEYWORD_LOOKUP`
  - `OFFICIAL_ACCOUNT_TRACKING`
  - `OFFICIAL_ANNOUNCEMENT_TRACKING`
  - `CHAIN_ECOSYSTEM_TRACKING`
  - `RELEASE_TRACKING`
- `authProfile`
  明确该 source 是否需要用户认证，以及认证模式是：
  - `NONE`
  - `USER_API_KEY`
  - `USER_SESSION`
  - `USER_COOKIE`
- `rateLimitHint`
  给外部运行时一个明确的限频提示，例如：
  - 建议每分钟请求数
  - 是否支持短时 burst
  - 建议缓存 TTL

这样后面无论是接桌面端、移动端还是脚本运行时，都能在不猜测 source 特性的情况下做更合理的调度。

同时，`MarketEvidence` 也已经不再只是通用文本证据，而是带有更明确的事件语义：

- `eventType`
- `impactDirection`
- `impactStrength`
- `freshness`
- `sourceTimestampConfidence`

这样综合分析阶段不必只靠字符串猜测某条证据的性质。

同样地，运行时共享上下文也已经进一步去 JSON 化：

- `IndicatorSnapshot` 现在带的是 typed `IndicatorValuePayload`
- `ContextPatch` 和 `AnalysisContext` 现在共享的是 typed `SharedContextValues`

这意味着 agent 之间传递“指标快照”和“摘要级共享信息”时，不需要再依赖约定俗成的 JSON 键名。

## Search Scope Policy

`market_agent` 的搜索范围必须由策略控制，不允许自由扩散。当前默认策略已经独立成 `DefaultMarketScopePolicy`：

- `INTRADAY`：`DIRECT + ECOSYSTEM`
- `SWING`：`DIRECT + ECOSYSTEM + SECTOR`
- `POSITION`：`DIRECT + ECOSYSTEM + SECTOR + MACRO`

其中：

- `DIRECT`：当前币对、项目、合约、官方账号
- `ECOSYSTEM`：所在链、基金会、主要协议、主要交易所
- `SECTOR`：同赛道、竞争项目、板块龙头
- `MACRO`：BTC、ETH、ETF、监管、宏观风险事件

这样做的目的是避免第一版就把“所有金融信息”无差别塞进分析上下文。

## Client First, Server Optional

当前方案明确选择客户端优先：

- Orchestrator 在客户端
- Agent 在客户端
- Source Adapter 在客户端按需直连公开源
- LLM 请求由用户自己配置 endpoint 和 key
- 项目默认不依赖作者服务器

这样更符合开源项目和用户可控的方向。

同时也保留扩展口：如果未来用户自己有服务端，可以在宿主层实现 remote provider，但这不应成为本项目默认前提。

## Runtime Boundary

这个 `lib` 只要求外部运行时把分析所需参数灌进来，不要求它来自某个特定 UI 或 K 线页面。

也就是说，这个分析工具可以完全泡在：

- CLI
- Desktop App
- Android App
- iOS App
- 本地守护进程
- 用户自己的脚本运行时

外部只需要负责两件事：

1. 组装输入参数：`AnalysisRequest + AnalysisContext`
2. 消费输出事件：`AgentEvent`

K 线如果存在，只是输入来源之一，而不是这个系统的宿主。

为了减少宿主直接操作底层协议的负担，当前已经提供了更稳定的外部入口：

- `AnalysisRequestBuilder`
  用于从宿主输入组装 `AnalysisRunInput`
- `AnalysisService`
  用于快速组装默认运行链路
- `AnalysisRunner`
  作为统一执行入口，支持：
  - `stream`
  - `run`
- `AnalysisRunResult`
  作为聚合后的完整分析结果

这样宿主现在已经可以走一条完整流程：

1. 用 `AnalysisRequestBuilder` 组装输入
2. 用 `AnalysisService` 或自定义 `AnalysisRunner` 执行
3. 用 `stream` 消费事件流，或者用 `run` 直接拿聚合结果

当前外部入口同时支持运行时控制：

- `AnalysisExecutionPolicy`
  - per-agent timeout
  - per-agent retries
  - partial failure policy
- `AnalysisRuntimeOptions`
  - 将执行策略和 trace hook 一起传给本次运行
- `AnalysisTraceListener`
  - 观察 run start / run finish
  - 观察 agent attempt start / finish
  - 观察所有 `AgentEvent`

这使得宿主侧现在不只是“能调起来”，而是已经能对运行行为做明确控制。

## Input Contract Thinking

这套系统应当被理解为“参数驱动分析工具”，而不是“页面驱动功能”。

推荐的最小输入集如下：

1. 资产标识：`symbol / name / chain / tokenAddress / aliases`
2. 时间尺度：`intervalLabel / horizon`
3. 价格上下文：`candles`
4. typed config：`AnalysisConfig`
5. 用户问题：`userPrompt`

只要这些参数能从任意平台提供出来，三类 agent 就可以运行。

当前推荐的方式不是传自由 JSON，而是传明确的 typed config。这样后续扩展新 agent 时，配置契约不会退化成不可控的参数包。

对于 `market_agent`，typed config 已经细化到 source 级别，不只是类型级别：

- 可以声明启用模式
- 可以声明具体 source id
- 可以声明 source 优先级
- 可以声明单个 source 的采样上限
- 可以读取 source capability
- 可以读取 source auth profile
- 可以读取 source rate limit hint

对于结果侧，当前也已经有明确 typed output：

- `IndicatorAgentOutput`
- `MarketAgentOutput`
- `SynthesisAgentOutput`

`AgentResult.structuredPayload` 仍然保留，但只作为向后兼容和扩展字段，不再是主输出。

对于运行时中间态，当前也保留了扩展槽，但主路径已经切到 typed：

- `IndicatorSnapshot.extensionPayload`
- `SharedContextValues.extensionPayload`

## Suggested Next Work

后续如果继续完善这个 `lib`，应优先补：

1. `IndicatorAgent` 的纯库实现
2. `MarketAgent` 的纯库实现和 source adapter 注册机制
3. `SynthesisAgent` 的纯库实现
4. 一份跨语言可复刻的 JSON schema
5. 可替换的远程综合引擎实现

## Current Status

当前这一步已经落下：

- `DefaultIndicatorAgent`
- `DefaultMarketAgent`
- `DefaultSynthesisAgent`
- `DefaultAgentSet`
- `StandardAnalysisOrchestrator`
- `AnalysisRequestBuilder`
- `AnalysisService`
- `AnalysisRunResult`
- `AnalysisExecutionPolicy`
- `AnalysisTraceListener`
- typed config 契约：`AnalysisConfig`
- typed source selection：`MarketSourceSelectionPolicy`
- typed source metadata：`MarketSourceSpec`
- typed result 契约：`AgentOutput`
- typed runtime snapshot/context：`IndicatorSnapshot` / `SharedContextValues`

目前仍然没有附带任何平台 UI，也没有绑定某一个运行时。

这是刻意的：

- 先把协议和边界定清楚
- 先在 `lib` 内把默认 agent 做出来
- 再决定外部运行时如何接入
- 避免一边接某个平台，一边继续放大耦合
