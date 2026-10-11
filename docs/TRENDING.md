# 热门

Android 底部导航顺序为“行情 / 热门 / 钱包 / 设置”，“热门”内含可点选、滑动切换的 indicator 标签：共同热门、GMGN 热门、PumpFun 热门、OKX 热门。业务参考 CoinMonitor-mac 的 Trending 客户端、筛选、交集和 NEW 规则。

## 圆润蓝紫 UI（2026-10-09）

- 2026-10-11 最终布局：圆角榜单白块保持固定，条目在内部滚动，取消行间分割线。白块内的固定底行左侧显示结果数量，右侧显示“更新于 … · USD”，两者同一行；无成功快照时首次结果提示只在左侧展示。“手动刷新”提示文字移除，顶部刷新按钮保留。Seeker 原始截图与文字边界已核对，用户确认当前展示没有问题；记录在 `app/build/ui-review/trending-inline-footer-seeker-20261011-131258/`。

- 页面局部使用 `CoinMonitorDesignTheme`，沿用浅色/深色/跟随系统偏好。顶部为小字号品牌/页面标题和小图标按钮；“共同 / GMGN / PumpFun / OKX”采用胶囊标签，保留点击与左右滑动。
- 来源数量、链/时间窗筛选摘要、更新时间和刷新状态分开显示。独立来源使用自己的实际筛选；共同来源链或时间窗不同、或包含没有时间窗的 PumpFun 时显示“独立筛选”，不照抄设计图里的 Solana/24h 示例。共同榜单等待其他来源时不显示已成功更新或零交集；刷新失败后更新时间对应正在保留的完整共同快照。
- 榜单收进圆角卡片：两位排名、28dp 币种图标、链徽标、真实来源色点、缩写 CA、市值/价格和涨跌/流动性。首行使用轻微蓝紫底色；行高以 52dp 为下限，允许长数值和大字体撑开。新增的复制图标保留 48dp 点击区域，复制完整 CA；点击其余行区域仍进入详情。
- 聪明钱仍紧跟币种，NEW 和风险状态保留。来源色点只按代币实际 `links` 展示，不给所有币种写死三个来源。市值/价格循环切换仍在表头；极小价格继续复用 `QuoteFormatter`。
- 筛选面板使用圆角分组和紧凑胶囊选项，来源为多选语义，其余筛选为单选语义；保持至少两个共同来源、原有排序/刷新间隔/凭证入口。详情面板统一字号和指标卡片，保留复制及独立浏览器入口。
- 不修改 `TrendingViewModel`、客户端、榜单规则、缓存或凭证逻辑，也不添加新的网络来源或交易动作。基础规范见 [UI_DESIGN_SYSTEM.md](./UI_DESIGN_SYSTEM.md)。

2026-10-10 实机校对后，顶部可用按钮采用白色底盘；来源栏启用通用组件的 `compactTrack`，默认字号下可见底盘为 40dp，点击区域仍至少 48dp。紧凑来源栏的反馈只在同一层胶囊中切换颜色，不附加整块涟漪或缩放。该参数默认关闭，首页来源切换栏的既有外观保持不变。

顶部使用 `HorizontalPager` 横向分页。点击标签立即锁定目标来源，页面通过 `animateScrollToPage` 横滑，顶部唯一的胶囊和小点同步滑向目标；两者采用 `pageSwitchDurationMillis = 280` 与 `FastOutSlowInEasing`。文字保持固定位置，不再把各标签的底色分别开关，也不把经过的中间页当作选中来源。连续点击会取消旧切页任务，使用请求序号防止旧任务完成时清掉新目标。手动左右滑动仍保留，停稳后同步来源；焦点/悬停只强调文字，按压不额外铺背景或缩放。

同日以 120ms 按压录制共同/PumpFun 跨页与连续点击：修正前未点击的 GMGN 选中点在 60fps 取样中出现 22 帧，修正后为 0 帧，且 393 个取样帧中始终只有一个选中点。另确认左滑到 GMGN 的实际单来源内容、右滑恢复共同榜。321 个测试、Debug 静态检查（无错误）、构建及同签名覆盖安装通过；录像和最终截图保留在 `app/build/ui-review/trending-switch-20261010-123016/`。这验证本机当前浅色切换状态，不替代用户人工验收。

上述阶段曾以直接切页消除闪动，用户反馈其过于生硬，现已改为前述横滑分页与单一滑动指示器。新版本通过 321 个测试、Debug 静态检查和构建，已同签名覆盖安装到 Seeker；录像复核须单独获得用户授权，未执行时不能将代码检查写成动效视觉验收。

### 有数据时的切换性能（2026-10-10）

用户随后反馈 PumpFun / OKX 之间切换卡顿。代码排查确认，切换调用 `selectBoard → reconcile` 检查目标来源的请求需求；它不强制刷新新鲜缓存，但无缓存或自动刷新已到期时会请求。此前 `enqueue()` 的响应恢复到 ViewModel 界面线程后，仍在该线程执行响应体读取、JSON 解析与榜单解码；列表图标的同步 `peekBitmap()` 也会读取磁盘、解码图片。这些工作可能与横滑争用界面线程，不能仅凭代码认定它们占实际卡顿的具体比例。

修正后 `TrendingClient.fetch()` 的请求准备、响应体读取和解码统一在 IO 调度器执行，取消与来源间隔仍保留；图标同步预览只读内存，磁盘读取和解码走原有异步 `loadBitmap()`。分页只提前组合左右各一个相邻列表，使用已有快照，不增加榜单请求需求。点击立即移动选中胶囊，实际来源需求在分页停稳后提交；目标来源仍按原刷新周期与冷却规则加载。未变化的榜单排序缓存到 `remember`，更新时间跳动不重新排序；胶囊位移在布局阶段读取动画值，避免每帧重新组合整个标签栏。280ms 横滑和手动滑动保留。

切换尚未停稳或来源状态尚未同步时，暂时禁用顶部刷新/筛选入口，避免刷新旧来源或打开旧来源筛选；列表的筛选快捷入口使用同样的停稳条件。

新增响应体线程回归测试覆盖 PumpFun / OKX / GMGN：在单独的界面调用线程执行客户端，记录实际响应体读取线程。旧实现因占用调用线程而失败，修正后通过；最终 322 个测试全部通过，失败/错误/跳过均为 0。Debug 静态检查无错误（166 条警告）、构建通过，同签名 Debug 1.0.13/13 已覆盖安装到 Seeker，保留数据后停止设备操作。记录位于 `app/build/ui-review/trending-performance-20261010-130752/`。实体机观感由用户自行确认，不录制或自动遍历，代码检查不证明所有卡顿已消失。

### 图片未加载期间的滚动（2026-10-10）

用户随后进一步确认 GMGN/PumpFun 在图片未加载时滚动卡顿。榜单图片此前仍走 `CoinSymbolIcon → CoinIconService.loadBitmap` 的自建位图缓存和全尺寸解码；本轮 Compose 图标入口统一到现有 Coil，热门直接调用 `CoilCoinSymbolIcon`，代币与本地链徽标均按实际显示尺寸异步加载，复用图库缓存及稳定请求对象。Solana 徽标源 PNG 为 512×512，当前按 12dp 徽标内部的 8dp 图形目标取样，不再在每行通过 `painterResource` 直接解码。空图、加载中和失败不改变图标占位尺寸。

坏图跨行共享 60 秒/最多 256 项的失败冷却，取消不计为失败，成功清除冷却；保留代币图标优先及原回退，不为热门新增符号搜索。榜单客户端、刷新周期、快照、筛选与限流逻辑在本轮均未变更。本次只进行了代码层检查，不将尚未进行的实体机冷图滚动观察写为验证通过。共用策略详见 [UI_DESIGN_SYSTEM.md](./UI_DESIGN_SYSTEM.md#首页指示器与列表图片加载2026-10-10)。

本轮 326 个单元测试、Debug 静态检查（无错误，166 条警告）与构建通过；新包同签名覆盖安装 1.0.13/13 并保留数据，安装后未操作页面或清理图片缓存。回归包括失败冷却到期、滚动取消、成功恢复和有限缓存容量；记录位于 `app/build/ui-review/home-indicator-icons-20261010-163126/`。

正常字号下右侧数值列统一为 110dp，复制按钮不再随每行金额长度左右变化；首行使用浅紫至白色的轻渐变。空榜单采用内容决定高度的卡片，空内容区至少 96dp，配置凭证入口位于卡片内；有数据时继续使用原有可滚动列表。加载、失败、缓存及缺失凭证的判断规则保持原样，不为截图填入示例行情。

同轮打开筛选面板时，实机发现横向选项文字不可见、胶囊被撑成竖条。通用 `CoinMonitorPillOption` 修正为：在有界宽度里继续为文字分配剩余权重，在 `LazyRow` 的无界宽度里按文字自然宽度测量；文字单行显示，原有单选/多选回调、禁用条件与点击区域不变。修正后的 GMGN、PumpFun、OKX、链、榜单与时间窗选项已由原始截图和有效文字边界复核。

标签栏使用通用 `CoinMonitorSegmentedTabs`，四项随可用宽度均分；标签保留至少 48dp 点击高度，选中项使用胶囊底盘与小点，允许大字体自然撑开。行情和钱包资产/活动同样复用该控件，胶囊直接跟随实际分页位置，保留各页业务与独立滚动状态。

手机列表默认左侧显示排名、图标、币种和地址缩写，币种图标右下角使用打包的链 Logo（复用 Mac 的九条链资源），列表不再重复显示链名；GMGN 和包含 GMGN 的共同热门在币名后显示紫色人形图标和聪明钱人数，地址行同时保留 NEW/风险标记。右侧显示市值/价格及来源返回的涨跌幅或流动性；大字体或窄屏时数值改到下一行。表头“市值/价格”后的双向循环箭头按钮统一切换当前列表所有行。各标签的选择独立保存。价格在列表和详情中复用公共 `QuoteFormatter.formatPrice`，例如 `0.0001234` 显示为 `$0.0₃1234`。列表不绘制行间分割线，内容首尾各留 8dp。

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
- `ui/trending/TrendingRoute.kt`、`TrendingSheets.kt`：页面、胶囊标签、筛选和详情；`TrendingTokenRow.kt`：紧凑业务列表行、来源色点和链徽标。
- `ui/trending/TrendingPresentation.kt`：整理实际筛选和完整快照时间的展示信息；`TrendingPreviews.kt`：浅色、深色、大字体英文、缓存失败与凭证未配置的离线 Preview。
- `ui/components/CoinMonitorPageTopBar.kt`、`CoinMonitorSegmentedTabs.kt`：不依赖数据仓库的通用顶部栏和胶囊标签。

## 代码验证

单元测试入口包括 `TrendingClientTest`（请求参数、响应解析、限流及 PumpFun 价格计算）、`TrendingRulesTest`（身份、交集、排序、NEW 与刷新策略）、`TrendingSnapshotCacheTest`（持久化和失效）、`TrendingCachedStateTest` 与 `TrendingDisplayTest`（缓存展示与数值格式）、`TrendingPresentationTest`（实际筛选摘要、PumpFun 无时间窗、完整缓存时间与来源未齐），导航入口由 `MainNavigationEntryPolicyTest` 覆盖。

仓库验证命令：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`。这些检查验证代码与构建，不证明生产接口可用或实体机交互结果。

本次 UI 改造在 2026-10-09 完成 `:app:testDebugUnitTest`（307 个测试全部通过）、`:app:lintDebug`（无错误，162 条警告）、`:app:assembleDebug`。Seeker 已安装版本为 Debug 1.0.13；新包与原安装包的签名一致，`adb install -r` 返回 `Success`，保留已有数据。自动安装过程未启动 App 或截图；随后用户反馈本轮热门页无明显问题，并确认提交。上述代码检查不代表生产接口验证。

接口参考：[GMGN 官方客户端](https://github.com/GMGNAI/gmgn-skills/blob/main/src/client/OpenApiClient.ts)、[OKX 热门代币](https://web3.okx.com/zh-hans/onchainos/dev-docs/market/market-token-hot-token)。PumpFun 网站接口未提供公开的排名算法或限流承诺；本页不将该榜单称为纯热搜榜。
