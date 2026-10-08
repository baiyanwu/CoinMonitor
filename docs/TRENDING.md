# 热门

Android 底部导航在“钱包”和“浏览”之间提供“热门”，内含可点选、滑动切换的 indicator 标签：共同热门、GMGN 热门、PumpFun 热门、OKX 热门。业务参考 CoinMonitor-mac 的 Trending 客户端、筛选、交集和 NEW 规则。

标签栏复用首页和钱包的 `MarketModeTabs`，高 44dp，指示条宽 32dp、高 3dp；使用钱包的 labelLarge 字号，四项宽度随手机可用宽度均分，单项最多 96dp。首页和钱包原有两项的尺寸保持一致。

手机列表默认左侧显示排名、图标、币种和地址缩写，币种图标右下角使用打包的链 Logo（复用 Mac 的九条链资源），列表不再重复显示链名；GMGN 和包含 GMGN 的共同热门在币名后显示紫色人形图标和聪明钱人数，下一行只展示地址缩写。右侧显示市值和所选时间窗涨跌；表头“市值/价格”后的双向循环箭头按钮统一切换当前列表所有行。各标签的选择独立保存。价格在列表和详情中复用公共 `QuoteFormatter.formatPrice`，例如 `0.0001234` 显示为 `$0.0₃1234`。表头下分割线采用低透明度主题色。

PumpFun 原榜单不返回独立价格字段；使用同一记录的 USD 市值、`circulating_supply_str` 原始流通量和 `base_decimals` 计算单价，不假设固定十亿枚供应量。流通量或精度缺失、无效时价格显示 `--`。PumpFun 不提供涨跌时展示流动性，其他缺失数值也显示 `--`。点击币种查看完整合约地址、市值、价格、成交额、流动性、聪明钱及来源链接。复制地址仅由明确的复制按钮触发。

## 来源与筛选

| 来源 | API | 筛选 | 凭证 |
| --- | --- | --- | --- |
| GMGN | `GET /v1/market/rank`；`POST /v1/market/hot_searches` | 热门/热搜、全部或单链、1m/5m/1h/6h/24h | 第三方 API 设置新增的 GMGN API key |
| PumpFun | `GET https://frontend-api-v3.pump.fun/coins/great-coins?topK=50` | 固定 Solana 榜单，保留网站排序 | 无需 key |
| OKX | `GET /api/v6/dex/market/token/hot-token` | 热门/热搜、全部或单链、5m/1h/4h/24h | 复用现有启用的 OKX Onchain API 三项凭证 |

GMGN 单链选项与 Mac 相同：Solana、BSC、Base、Ethereum、Arbitrum、HyperEVM、Robinhood、Arc、Stable。热门按成交额请求；热搜按访问数排序。Solana 带 `renounced`/`frozen` 筛选，其余链带 `not_honeypot`/`verified`/`renounced`。全部链热门逐链查询且至少间隔两秒；热搜一次提交所有链参数。

OKX 单链选择提供 Solana、BSC、Base、Ethereum、Arbitrum；全部链接受接口返回的合法链标识。沿用 Mac 参数：`rankingType=4`，热门 `rankBy=5`（成交额），热搜 `rankBy=9`（站内搜索数），`riskFilter=true`、`stableTokenFilter=true`，最多 30 条。该热门排序是应用选择的成交额排序。官方接口默认 Trending 排序为 token score；二者并非相同顺序。

共同热门默认选择三个来源，允许选择其中任意两个，按各来源当前筛选的最新成功结果求交集。身份为链和合约地址；Solana 地址保留大小写，EVM 地址忽略大小写。主记录、指标和顺序优先使用所选来源中的 GMGN、OKX、PumpFun，不使用未选来源的指标。来源未配置或还没有成功快照时显示等待/配置状态；不会将缺失数据解释为没有交集。与 Mac 一样，不同来源的时间窗由各来源独立设置，PumpFun 没有时间窗和热搜接口。

筛选面板提供来源选择、每个来源的链/榜单/时间窗、自动刷新与 15/30/60/120/300 秒间隔、排序方向和市值/价格显示。筛选、来源、排序和刷新设置持久化。首次默认进入共同热门，GMGN/OKX 默认 Solana、热门模式和 5m 时间窗，启用 30 秒自动刷新。显示缺失指标时不会使用其他指标代替。

## 生命周期与错误

- 仅为当前标签请求数据；共同热门请求所选来源。页面生命周期低于 STARTED（离开热门页、打开独立页面、进入后台）时取消计时器和在途请求。
- `TrendingSnapshotCache` 由 AppContainer 持有，在应用初始化时后台预读 `files/market_trending/cache-v1.json`。成功榜单、完整共同热门、更新时间、NEW 首次发现时间和来源冷却状态使用原子文件替换持久化，最多保留 64 个查询快照。页面重建、Tab 切换、进程重启后先恢复匹配链/模式/时间窗/来源选择的缓存，再启动后台请求；不会把其他筛选的结果作为当前筛选显示。
- 记住上次查看的热门标签，进入页面不会为了初始化请求所有其他来源。缓存尚在刷新周期内时不立即请求；过期缓存先继续显示，按原间隔在后台刷新。请求中、失败和限流时保持上次列表；共同热门在来源请求尚未全部结束或存在失败时保留上次完整交集。只有没有匹配的成功缓存时显示初次加载/失败状态。
- 每个来源至少间隔两秒。网络/解析失败等待 60 秒，429 遵循 `Retry-After`、GMGN 的 `reset_at`/`X-RateLimit-Reset`；缺失重置值时 GMGN 等待五分钟，其他来源等待一分钟。手动刷新、切换筛选和重建页面不绕过来源级冷却。
- 认证或额度错误停止该来源自动请求。修改保存的凭证后清除相应来源缓存和失败状态；用户修复授权或额度后可点击刷新主动重试，仍遵循最小请求间隔。
- 第一次成功快照作为基线。后续新进入代币显示 NEW 并置顶 180 秒，刷新不续期，置顶期间保留来源顺序。共同热门使用独立交集基线。
- GMGN key 使用 EncryptedSharedPreferences/Android Keystore，拒绝降级到明文，排除云备份和设备迁移。应用网络日志和调试网络日志脱敏 `X-APIKEY` 和 OKX 鉴权头。项目不内置 key，也不读取 Mac 凭证。

关闭自动刷新后，已有成功快照不会因过期自动请求；没有匹配快照时仍会加载一次。手动刷新仍受来源冷却和两秒最小间隔限制。缓存仅用于展示上次成功结果，不代表实时行情；页面会保留更新时间和错误状态。

## 代码入口

- `domain/model/TrendingModels.kt`：身份、交集、需求、排序、NEW。
- `data/network/TrendingClient.kt`：公开 Pump 请求、GMGN 请求/封装解析、OKX 签名请求及限流。
- `data/repository/TrendingPreferences.kt`、`TrendingSnapshotCache.kt`、`GmgnCredentialsRepository.kt`：标签/筛选偏好、成功榜单和完整交集缓存、GMGN 加密 key。排行榜缓存不包含凭证。
- `ui/trending/TrendingViewModel.kt`：按可见标签加载、按查询缓存、来源级退避和共享基线。
- `ui/trending/TrendingRoute.kt`、`TrendingSheets.kt`：手机列表、indicator 标签、筛选和详情。

## 代码验证

单元测试入口包括 `TrendingClientTest`（请求参数、响应解析、限流及 PumpFun 价格计算）、`TrendingRulesTest`（身份、交集、排序、NEW 与刷新策略）、`TrendingSnapshotCacheTest`（持久化和失效）、`TrendingCachedStateTest` 与 `TrendingDisplayTest`（缓存展示与数值格式），导航入口由 `MainNavigationEntryPolicyTest` 覆盖。

仓库验证命令：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`。这些检查验证代码与构建，不证明生产接口可用或实体机交互结果。

接口参考：[GMGN 官方客户端](https://github.com/GMGNAI/gmgn-skills/blob/main/src/client/OpenApiClient.ts)、[OKX 热门代币](https://web3.okx.com/zh-hans/onchainos/dev-docs/market/market-token-hot-token)。PumpFun 网站接口未提供公开的排名算法或限流承诺；本页不将该榜单称为纯热搜榜。
