# AI Analysis 接入计划

## 目标

在现有 K 线页基础上补齐可用的 AI 分析能力，范围包括：

- 打开并完成 AI 设置入口
- 打开并完成 K 线页 AI 入口
- 以流式输出作为默认 AI 交互模式
- 让用户在对话窗口中显式选择要带给 AI 的指标数据
- 将选中的指标数据和行情上下文一起发送给 AI
- 补齐错误处理、会话态和基础可用性

## 当前已实现

### 1. 配置与依赖

- 已有 `OpenAiCompatibleConfig`
- 已有 `AiConfigRepository` 接口与 `DefaultAiConfigRepository` 实现
- 已有 `AiChatRepository` 接口与 `DefaultAiChatRepository` 实现
- `AppContainer` 已完成 AI 仓库注入

### 2. AI 配置存储

- 已支持 `enabled / baseUrl / apiKey / model / systemPrompt`
- 已使用 `EncryptedSharedPreferences` 安全保存 AI 配置
- 已支持读取、保存、清空、观察配置变化
- 已支持安全存储不可用时给出失败提示

### 3. 设置页状态管理

- 已有 `AiSettingsFormState`
- 已有 AI 配置表单的 ViewModel 更新逻辑
- 已有保存/清空 AI 配置逻辑
- 已有基本表单校验逻辑

### 4. 设置页 UI

- AI 设置表单 UI 已经写好
- 包含开关、Base URL、API Key、Model、System Prompt、保存和清空
- AI 设置入口已打开

### 5. AI 请求基础链路

- 已能按 OpenAI compatible 的 `chat/completions` 格式发请求
- 已能自动拼接 `/v1/chat/completions`
- 已能注入 Bearer Token
- 已从普通整包请求切换为流式主链路
- 已支持 `stream=true`
- 已支持读取 `data:` 增量片段和 `[DONE]`
- 已支持增量解析 assistant `delta.content`
- 架构上已为普通非流式请求预留扩展口，但未接入主流程

### 6. K 线页 AI 状态

- `KlineUiState` 已有 `chatMessages / isAiSending / aiReady`
- `KlineViewModel.sendMessage()` 已能发起流式请求
- 已能把用户消息、AI 回复和错误文案写回消息列表
- 已支持 assistant 占位消息增量更新
- 已支持停止当前生成任务

### 7. 对话弹窗 UI

- 已有 AI 对话弹窗
- 已有消息列表、输入框、发送按钮、loading 状态
- K 线页 AI 入口已打开
- 流式生成时会展示占位气泡
- 流式生成中发送按钮会切换为“停止”

## 当前缺口

### P0. 用户不可达

- 已解决

### P0. 指标上下文不可控

- 当前发送给 AI 的指标信息只有指标名称，没有具体指标数值
- 用户无法决定这次对话要带哪些指标
- 缺少“本次对话上下文构成”的显式交互

### P0. 上下文过弱

- 当前只发送标的、市场类型、周期、指标名、最新一根 K 线、最新价、24h 涨跌幅
- 没有发送 MA/EMA/BOLL/MACD/RSI/KDJ/VOL 的结构化计算结果
- 没有发送最近 N 根 K 线摘要
- 没有发送趋势、波动、量价关系等中间结论

### P1. 失败兜底较弱

- HTTP 非 2xx 只返回状态码，缺少响应体解析
- 已补基础错误正文解析
- 已补基础超时配置
- 已补“停止生成”取消能力
- 仍缺少重试和更细的错误分类
- 仍需继续增强不同 OpenAI compatible 服务的响应兼容处理

### P1. 会话态较弱

- 会话消息只保存在当前 `ViewModel` 内存中
- 页面重建或进程回收后消息会丢失
- 没有上下文裁剪策略，后续多轮对话容易无限增长

### P1. 交互细节未完成

- 对话发送前没有显示“将发送哪些上下文”
- 没有快捷问题模板
- 没有“清空会话”动作

### P2. 可观测性不足

- 没有单独记录 AI 请求耗时
- 没有单独记录 AI 请求失败原因
- 没有可用于调试的 prompt 组装结果查看入口

## 新增需求落地

### 流式输出优先

用户要求：

- AI 交互必须直接支持流式输出
- 不以普通整包请求作为当前主实现
- 架构上可以给普通请求保留扩展口，但不作为当前交付目标

### 架构约束

- `Repository` 不直接负责底层流读取细节
- 应新增单独的 AI 数据源层，例如：
  - `AiStreamingDataSource`
  - `OpenAiCompatibleStreamingClient`
- 流式通道作为主路径
- 普通请求仅保留可选接口，例如：
  - `AiCompletionFallbackDataSource`
  - 或在同一接口下保留非流式实现分支

### UI 交互要求

- 发送后应立即插入一条空的 assistant 占位消息
- 流返回内容到达时逐段追加到这条 assistant 消息
- 用户应能看到“正在生成”状态，而不是等待整段完成
- 生成结束、失败、取消都要能正确收尾

### 对话窗口指标标签选择

用户要求：

- 在 AI 对话窗口中提供指标选择标签
- 标签位置紧贴输入框上方
- 标签采用“小标签”形式
- 用户点击后进入选中状态
- 仅把选中的指标数据带给 AI

### 建议交互方案

- 在输入框上方增加一行可横向滚动的指标标签
- 默认提供这些标签：
  - `MA`
  - `EMA`
  - `BOLL`
  - `VOL`
  - `MACD`
  - `RSI`
  - `KDJ`
- 标签支持多选
- 选中态和未选中态视觉明确区分
- 初始默认值建议为当前 K 线页正在展示的 `主图指标 + 副图指标`
- 切换币种或周期时，已选标签保留；如果某指标当前无法计算，则发送时自动跳过并给出空值保护

### 发送规则

- 发送消息时，除了用户输入文本，还要读取当前已选指标集合
- 只为已选指标组装结构化数据
- 未选中的指标不进入 prompt
- prompt 中应明确列出“本次用户选择带上的指标”

### 数据组织建议

- 不建议只传“指标名称”
- 应传“最新值 + 必要的最近几根变化”
- 统一用结构化文本或结构化 JSON 片段，再拼入 prompt

建议每个指标最少带上：

- `MA/EMA`
  - 各周期最新值
  - 当前价相对均线的位置
  - 最近 2 到 3 根的方向变化
- `BOLL`
  - 上轨、中轨、下轨
  - 带宽
  - 当前价所处区域
- `VOL`
  - 最新成交量
  - 最近均量对比
  - 是否放量/缩量
- `MACD`
  - DIF
  - DEA
  - Histogram
  - 最近柱体变化方向
- `RSI`
  - 最新 RSI
  - 是否进入超买/超卖区
- `KDJ`
  - K/D/J 最新值
  - 金叉/死叉状态

## 实施计划

### 阶段 1. 打开入口并连通最小可用链路

- 打开 AI 设置页入口
- 打开 K 线页 AI 入口
- 确认未配置 AI 时的提示文案和禁用逻辑
- 确认聊天弹窗可正常打开、关闭、发送
- 打通最小可用的流式聊天链路

验收：

- 用户能在设置页填写并保存 AI 配置
- 用户能在 K 线页打开 AI 对话框
- 用户能成功发起一次流式 AI 请求
- AI 回复内容会逐步显示，而不是整包落下

当前状态：

- 已完成

### 阶段 2. 补指标标签选择

- 在 `KlineUiState` 增加“已选 AI 指标集合”
- 在 `KlineViewModel` 增加标签切换逻辑
- 在对话弹窗输入框上方增加标签行
- 标签使用现有页面视觉体系，保持轻量胶囊样式
- 发送时把“已选标签集合”传给 `AiChatRepository`

验收：

- 标签紧贴输入框上方
- 标签可点击切换选中态
- 多选可用
- 发送请求时只携带已选指标

当前状态：

- 尚未开始

### 阶段 3. 组装结构化指标上下文

- 为 AI 上下文增加专门的数据组装层，避免 `DefaultAiChatRepository` 继续膨胀
- 复用现有指标计算逻辑，补出 AI 侧需要的指标快照
- 将当前指标数据转成稳定的结构化文本
- 将最近 N 根 K 线的关键信息压缩为摘要

建议新增模块：

- `AiAnalysisContextBuilder`
- `AiIndicatorSnapshotBuilder`
- 如有必要，增加 `AiPromptFormatter`

验收：

- prompt 中包含用户选中的指标详细数值
- 不同指标可独立开关
- 指标数据缺失时不会导致整个请求失败

### 阶段 4. 完善流式请求与错误处理

- 给 AI 流式请求增加超时
- 解析失败时尽量显示服务端错误正文
- 区分配置错误、网络错误、解析错误、空响应错误
- 增加取消前一次发送或防重复发送保护
- 增加流结束标记、半包、断流场景处理

验收：

- 常见失败场景下给出可理解提示
- 连续快速点击发送不会造成状态混乱
- 断流或服务端提前结束时 UI 状态能正确恢复

当前状态：

- 已完成基础版：超时、错误正文解析、停止生成
- 未完成增强版：重试、断流恢复、服务商差异兼容

### 阶段 5. 会话态与产品细节

- 增加“清空会话”
- 增加多轮消息裁剪策略
- 评估是否需要按币种 + 周期保存最近会话
- 增加若干快捷提问模板

验收：

- 会话行为更稳定
- prompt 不会随轮次无限膨胀

## 推荐改动点

### UI

- `app/src/main/java/io/baiyanwu/coinmonitor/ui/kline/KlineRoute.kt`
  - 打开 AI 入口
  - 在 `KlineChatDialog` 输入框上方增加指标标签行

### 状态与业务逻辑

- `app/src/main/java/io/baiyanwu/coinmonitor/ui/kline/KlineViewModel.kt`
  - 增加 AI 指标选择状态
  - 增加切换标签的方法
  - 发送消息时传递已选指标
  - 处理流式消息增量更新

### AI 数据组织

- `app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultAiChatRepository.kt`
  - 改为接收“用户选中的指标数据”
  - 不再只传指标名

- `app/src/main/java/io/baiyanwu/coinmonitor/data/ai/`
  - 新增流式客户端或数据源层
  - 负责 SSE / chunked response 解析

- `app/src/main/java/io/baiyanwu/coinmonitor/ui/kline/chart/`
  - 复用或下沉现有指标计算逻辑，供 AI 上下文组装使用

## 建议的数据流

1. 用户打开 AI 对话框
2. 用户在输入框上方勾选指标标签
3. `KlineViewModel` 持有已选标签集合
4. 用户点击发送
5. `KlineViewModel` 根据当前 candles + indicator settings + selected tags 组装请求参数
6. `AiChatRepository` 只拼接被选中的指标上下文
7. 流式客户端持续产出增量文本
8. ViewModel 持续更新最后一条 assistant 消息
9. 流结束后更新发送态

## 优先级建议

### 必做

- 打开 AI 两个入口
- 流式输出主链路
- 对话窗指标标签选择
- 已选指标结构化数据入 prompt
- 基础错误处理

### 次优先

- 清空会话
- 快捷问题模板
- 上下文裁剪

### 后续增强

- 普通请求 fallback
- 会话持久化
- AI 请求调试面板
- 不同模型的兼容适配

## 建议先做的实现顺序

1. 打开 AI 入口
2. 打通流式输出主链路
3. 在对话窗加指标标签选择
4. 把已选指标集合打通到发送链路
5. 输出真实指标数值，而不是仅输出名称
6. 补流式错误处理
7. 再做会话优化和增强能力

## 本次已完成

- 已打开 AI 设置页入口
- 已打开 K 线页 AI 入口
- 已将 AI 链路切换为流式输出主路径
- 已新增独立 streaming client，负责 OpenAI compatible 流式解析
- 已在 ViewModel 中支持 assistant 消息增量更新
- 已支持停止当前生成
- 已补首段内容到达前的对话占位态

## 明日待定

- 是否继续按当前流式架构演进
- 指标标签选择的最终交互形式
- 指标数据采用结构化文本还是更强约束的 JSON 片段传给 AI
