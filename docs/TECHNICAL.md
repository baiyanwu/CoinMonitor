# Technical Notes

这个文档用于集中说明 `CoinMonitor` 的技术实现细节、工程结构和当前设计取舍。

项目按 Android 常规工程结构收口，优先保证可运行、可维护和便于后续继续演进。

## Tech Stack

- Kotlin
- Jetpack Compose
- Room
- Preferences DataStore
- Retrofit + OkHttp + Kotlinx Serialization
- Foreground Service
- WindowManager Overlay

## Architecture

```text
app/src/main/java/io/baiyanwu/coinmonitor/
  boot/        开机恢复、升级恢复、自恢复广播
  data/        Room 数据库、DataStore 偏好、网络与仓库实现
  domain/      核心模型和仓库接口
  overlay/     悬浮窗控制器、前台服务、轮询协调器
  ui/          Compose 页面、主题、Activity 宿主
```

整体设计保持单模块结构，优先保证可读性、实现速度和后续演进空间，而不是过早做复杂模块拆分。

## Core Behavior

### Watchlist

- 首页展示观察列表
- 支持添加、删除、手动刷新
- 首页首屏会先等待本地数据回流，避免“先闪空态按钮、再切列表”
- 首页现在只保留一套手动顺序源，不再区分名字排序、价格排序等模式
- 首页支持整卡长按拖动排序，并支持置顶 / 取消置顶；置顶项固定排在普通项之前
- 长按单个币对会弹出跟手快捷菜单，支持删除、置顶 / 取消置顶，以及加入或移出悬浮窗
- 首页价格继续保持实时显示，但实时价格更新已经从 Room 高频写回中拆出，改为走内存报价状态，降低大量 `WSS` 推送时的滚动抖动

### Search

- 支持关键字搜索
- 页面顶部拆分为可点击、可左右滑动的“交易所 / 链上”两个模式；两个模式各自保存独立输入内容、加载状态和搜索结果
- 交易所模式并行搜索 `Binance Alpha / Binance Spot / Binance USDT-M Futures / OKX Spot / OKX USDT Swap`；等待五路来源全部结束后统一合并、排序并一次性展示结果。单个来源失败会短延迟重试一次，仍失败时保留其他来源结果并明确提示部分来源不可用；同一关键词重复提交不会重建整组请求
- 链上模式通过 `DexScreener` 搜索代币，不再提供手动选链控件
- 链上搜索支持币名 / Symbol / 合约地址输入；合约地址会先识别为 `EVM` 或 `Solana` 地址，再从对应链族的返回结果中定位具体链
- 链上结果按“`chainIndex + normalized token address`”做语义去重，同一条链上的同一代币不会因为存在多个池而重复展示
- 链上结果主标题直接展示当前实际交易对，并同时展示链 Logo、DEX、美元流动性和合约地址缩写
- 同一代币存在多个有效池时，可在当前结果内展开备选池；界面最多展示流动性排名靠前的 5 个备选，完整候选仅保留在当前搜索结果内存中，用于还原历史固定池
- 搜索页当前按入口模式分流：
  - 从首页进入时，结果页继续承担观察列表的 `添加 / 删除` 管理
  - 从 K 线页进入时，结果页隐藏增删按钮，点击单条结果后会回填到 K 线页并立即关闭搜索页；当前 K 线公开入口已隐藏，这一路径作为保留实现暂不暴露

### K-line

- K 线页、图表、指标设置、搜索回填和 AI 聊天实现仍保留在工程中，但当前不再作为底部导航或首页卡片点击入口暴露
- `NavHost` 中仍保留 `Destinations.KLINE` route，用于后续恢复入口时复用既有实现；底部导航列表只展示首页和设置
- 图表内核当前基于仓库内 vendored 的 `TradingView Lightweight Charts Android wrapper` 源码模块
- 第三方图表源码当前直接放在 `third_party/lightweightlibrary`，应用不再依赖外部 `aar`，方便直接调试 wrapper 和内嵌 JS core
- K 线数据统一走 `MarketKlineRepository`，交易所继续使用 `Binance / Binance Alpha / OKX`，链上池使用 `GeckoTerminal`
- 主图支持 `MA / EMA / BOLL`，副图支持 `VOL / MACD / RSI / KDJ`
- 指标设置使用独立 `Activity`，通过本地偏好持久化完整配置模型
- 当前图表已经消费 `开关 / 参数 / 颜色 / 基础样式`，并按配置重绘，不会因为改指标参数而重新请求行情接口
- 图表实现和页面实现之间通过 `KlineChartContract` 解耦，方便后续继续替换成自研 K 线内核
- 图表颜色当前通过独立 `KlineChartPalette` 管理，不直接复用应用页面主题色；夜间模式下网格、文字和主副图颜色可以独立调整
- 当前实现已经从“双 `ChartsView` 手工同步”切到“单 chart + pane”结构，主图和副图共用同一套十字线、时间轴和缩放逻辑
- wrapper 本地补齐了官方已有但 Android 侧未暴露的 pane 和 logical range 能力，用于把副图指标 series 移入独立 pane 并保持时间轴一致
- vendored wrapper 当前内嵌的 JS core 已切到 `lightweight-charts v5.1.0`
- 当前对价格轴手势只做了一处集中修正：在 vendored JS core 内屏蔽价格轴区域的双指放大异常，保留主绘图区的正常 pinch 缩放
- 当前 K 线页通过在 `NavHost` 级别复用 `KlineChartHostView`，避免 route 切换时整块 chart 被销毁重建
- 夜间模式下的 WebView 首帧白底和 pane 分隔白线，当前收口在 vendored wrapper 的加载页与 JS 初始化层做透明背景修正
- 为了隔离 K 线问题，K 线页外层仍暂时移除了下拉刷新和纵向滚动，避免额外手势干扰；周期切换已经恢复为真实生效
- AI 聊天当前已经切到“会话 + 消息”两层持久化模型，K 线页支持新建会话，并通过独立历史页回看和切换旧会话；第三方 API 设置页中的 AI 配置区当前通过入口开关隐藏，保留代码不删除
- K 线页输入框当前使用自定义紧凑 `BasicTextField` 容器，而不是 `OutlinedTextField`，避免 Material 默认最小高度、内部垂直 padding 和尾部标准按钮把输入区撑高
- AI 历史页当前只展示至少有一条消息的会话；空白新会话不会反复计入历史列表

### On-chain

- 当前链上能力提供搜索、最新价格、24 小时涨跌、流动性、成交量与 K 线，不提供交易执行
- 搜索与报价使用无需 API Key 的 `DexScreener`，K 线使用无需 API Key 的 `GeckoTerminal`
- 链上搜索不再把本地注册表作为白名单：DexScreener 返回的非空 `chainId` 都会参与结果解析，且不再施加本地 80 条结果上限
- 本地注册表继续为 17 条已知链提供精确 `chainIndex`、DexScreener 与 GeckoTerminal 网络映射；未知链直接持久化 DexScreener `chainId`，后续报价沿用该标识，K 线以同名 GeckoTerminal 网络作最佳努力请求
- EVM 合约地址统一转为小写，Solana 与其他链地址保留原始大小写；未知网络会根据返回的代币地址形态区分 EVM 与其他链
- 搜索选池先要求目标合约精确匹配，并排除无有效美元价格或无流动性的池；随后依次按目标代币位于 `base` 侧、美元流动性、24 小时成交量和池地址排序
- DexScreener 网络 DTO 按官方契约容纳显式 `null`：`pairs`、`labels`、`priceChange` 在解码层保持可空，并在客户端或业务边界统一归一化为空集合，避免单个缺失字段导致整次搜索或报价解析失败
- 添加观察项时固定池地址和目标代币的 `base / quote` 方向；用户从搜索结果切换池后，已添加标的立即更新绑定，未添加标的会在添加时保存当前选择
- 同一标的连续切池时会取消上一任务，并等待上一代数据库写入完全结束后再写入最新选择；报价落库还会比较“请求发起时的池绑定”和当前绑定，拒绝迟到旧请求回写池地址或价格
- 搜索到已有观察项时，会优先在完整候选中恢复数据库里的固定池，避免搜索结果显示的池与实际报价、K 线来源不一致
- 后续价格刷新和 GeckoTerminal K 线共用同一个固定池；只有固定池明确失效或连续缺失后才自动重选
- GeckoTerminal 只接收官方支持的聚合参数：`1m / 5m / 15m` 使用 minute，`1H / 4H` 使用 hour，`1D` 使用 `day + aggregate=1`
- `3D / 1W / 30D` 不再向上游发送无效的 `day + aggregate=3/7/30`；应用改为请求日线后在本地合并 OHLCV，其中周线按 UTC 周一对齐，`30D` 是固定 30 天而非自然月，交易所仍显示自然月 `1M`
- 长周期请求按目标根数扩展日线数量；超过 GeckoTerminal 单次 1000 根时使用 `before_timestamp` 向前分页，直到达到目标、上游无更多历史或请求被取消。页面默认仍以最多 240 根合成 K 线为目标，实际根数受池子创建时间和免费接口可用历史限制
- 报价和 K 线捕获普通网络异常时不会捕获 `CancellationException`，快速切换标的、周期或重启刷新任务后，旧任务不会继续更新 UI
- 搜索结果通过 `LazyColumn.itemsIndexed` 逐条组合和回收，不再在单个 lazy item 内用 `forEach` 一次性组合全部结果
- 代币图标优先使用 DexScreener 返回的公开 `info.imageUrl`；链 Logo 优先使用本地映射，未命中或下载失败时依次尝试在线链图标候选
- 链上代币缺少自身图标时，会回退到链 Logo 并在缓存阶段生成灰阶版本复用；所有在线候选都失败时，Compose 列表和原生悬浮窗都使用内置默认占位图，网络异常不会向上抛出中断渲染

### Overlay

- 支持选择要展示的币对
- 当前最多允许选择 `10` 个悬浮窗币对；超过上限时会在入口页给出提示，限制值统一收口在 `OverlaySettings.MAX_SELECTABLE_ITEMS`
- 支持锁定拖动、透明度调节、最大展示数量限制
- 支持字体大小调节，并同步缩放左侧图标 / 名称区比例
- 左侧展示默认使用图标，也可以切换成币对名称
- 悬浮窗中的币种图标统一按圆形裁剪，和应用内列表的视觉语义保持一致
- “吸附靠边”总开关下提供两个互斥模式：“仅吸附靠边”继续展示边栏跑马灯，“彻底隐藏”只保留屏幕边缘唤出条
- 彻底隐藏模式的唤出条使用主题色，透明度可在 `15%–100%` 范围连续调整，默认 `45%`
- 点击唤出条时，边条先消失，价格区域按当前左右吸附方向滑入，并使用和“仅吸附靠边”完全相同的单 item ticker；自动收回时价格区域反向滑出，结束后才重新显示边条
- 自动收回时间支持 `1–5 秒` 整秒选择，默认 `3 秒`；拖动悬浮窗期间会延后回收，避免定时器打断手势
- 通知栏支持临时隐藏 / 恢复显示，以及拖动开关
- 只有在悬浮窗权限满足时，应用才会把悬浮窗正式标记为启用
- 悬浮窗设置页中的币对选择列表会带上交易所来源副标题，避免同名币对辨识成本过高
- 悬浮窗设置页和悬浮窗实际展示顺序，都会直接复用首页最终顺序；首页拖动或置顶后，这两处会同步更新
- 普通悬浮窗模式不再按 `5` 个一组分页轮播，而是直接按当前 `maxItems` 铺开显示
- 吸附侧边栏模式不再使用整串 `TextView marquee`，而是改成单 item ticker：当前“图标 + 价格”向左滑出，下一个 item 从右侧滑入；WSS 更新只刷新内容，不重置切换节奏

### Refresh Strategy

- 全局刷新间隔统一配置
- 当前支持：
  - 自定义 `3-10 秒`
  - `30 秒`
  - `1 分钟`
- 首页和悬浮窗只保留一套全局刷新协调器
- 当前底层默认实现中，`Binance Spot / Binance Alpha / Binance USDT-M Futures / OKX Spot / OKX USDT-M Futures` 优先走 `WSS`
- 当前实时价格主链路已经改成 `WSS / REST -> InMemory QuoteRepository -> UI`，不再每次报价都直接写回 `watch_items`
- `watch_items` 里的价格字段当前只承担启动恢复和低频快照持久化，默认在页面不再活跃时落一次，并在前台运行期间按低频兜底写回
- 链上价格固定使用 `DexScreener REST`，按链分组且每批最多 30 个不同合约地址；默认“智能刷新”以 30 秒缓存窗口规划完整轮转，也可选择 `30 / 45 / 60 / 120 秒`固定轮转周期
- 上述“每批 30 个”表示同一条链上的最多 30 个不同代币合并为一次 HTTP 请求，并非每个代币单独消耗一次请求；不同链分别形成批次，所有批次在完整周期内顺序分散，相邻请求至少间隔 1 秒
- 首页手动刷新复用同一条链上请求队列，30 秒内已成功刷新的批次不会重复发送；单批失败按 `5 / 10 / 20 / 30 秒`独立退避，其他批次继续轮转
- DexScreener 客户端统一限制在每分钟最多 240 次请求，为公开接口限额保留余量；429 会优先遵守 `Retry-After`，否则执行带随机抖动的指数退避
- 链上 K 线固定使用 `GeckoTerminal`，按已保存的池地址和目标代币方向查询，并在客户端限制为每分钟最多 8 次
- 链上刷新设置页使用智能/固定分段按钮与固定周期选项，并展示当前请求批次数、批次间隔、轮转周期和失败重试批次数
- HTTP / WSS 网络日志会脱敏 API Key、签名、Passphrase、鉴权头与 Cookie

### Upstream Docs And Endpoints

为方便后续继续接手，这里把当前实际接入的上游文档入口、`base URL` 和主要接口路径集中列出。

- `Binance Spot`
  - 官方文档：`https://developers.binance.com/docs/binance-spot-api-docs/rest-api`、`https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams`
  - REST base URL：`https://api.binance.com/`
  - WSS URL：`wss://stream.binance.com:9443/ws`
  - 当前 REST 路径：`GET /api/v3/exchangeInfo`、`GET /api/v3/ticker/24hr`
  - 当前 WSS 订阅：`${symbol.lowercase()}@ticker`

- `Binance USDT-M Futures`
  - 官方文档：`https://developers.binance.com/docs/derivatives/usds-margined-futures/websocket-market-streams`
  - REST base URL：`https://fapi.binance.com/`
  - WSS URL：`wss://fstream.binance.com/market/ws`
  - 当前 REST 路径：`GET /fapi/v1/exchangeInfo`、`GET /fapi/v1/ticker/24hr`
  - 当前 WSS 订阅：`<symbol>@ticker`（例如 `btcusdt@ticker`）

- `Binance Alpha`
  - 官方文档入口：当前项目主要参考 `Binance Alpha / Web3 Wallet` 公开页面行为与现网接口，缺少一套稳定的官方开放文档索引；后续如果 Binance 提供正式文档，建议优先补到这里
  - REST base URL：`https://www.binance.com/`
  - WSS URL：`wss://nbstream.binance.com/w3w/wsa/stream`
  - 当前 REST 路径：`GET /bapi/defi/v1/public/alpha-trade/get-exchange-info`、`GET /bapi/defi/v1/public/wallet-direct/buw/wallet/cex/alpha/all/token/list`、`GET /bapi/defi/v1/public/alpha-trade/ticker`
  - 当前 WSS 订阅：`${symbol.lowercase()}@ticker`

- `OKX Spot`
  - 官方文档：`https://www.okx.com/docs-v5/en/`
  - REST base URL：`https://www.okx.com/`
  - WSS URL：`wss://ws.okx.com:8443/ws/v5/public`
  - 当前 REST 路径：`GET /api/v5/public/instruments?instType=SPOT`、`GET /api/v5/market/ticker`
  - 当前 WSS 订阅：`channel=tickers`

- `DexScreener`
  - API 文档：`https://docs.dexscreener.com/api/reference`
  - REST base URL：`https://api.dexscreener.com/`
  - 当前路径：`GET /latest/dex/search`、`GET /token-pairs/v1/{chainId}/{tokenAddress}`、`GET /tokens/v1/{chainId}/{tokenAddresses}`
  - 搜索响应同时提供链、池地址、base / quote 代币、DEX、价格、流动性、成交量和公开图标等字段；应用不调用网页内部接口

- `GeckoTerminal`
  - API 文档：`https://apiguide.geckoterminal.com/`
  - REST base URL：`https://api.geckoterminal.com/`
  - 当前路径：`GET /api/v2/networks/{network}/pools/{poolAddress}/ohlcv/{timeframe}`；长周期分页使用官方 `before_timestamp` 参数

- `Trust Wallet Assets`
  - 资源仓库：`https://github.com/trustwallet/assets`
  - 当前仅用于链 Logo 静态图片，不参与搜索、报价或 K 线请求

- 代码对齐位置
  - REST base URL 定义：`app/src/main/java/io/baiyanwu/coinmonitor/data/network/NetworkFactory.kt`
  - REST 路径定义：`app/src/main/java/io/baiyanwu/coinmonitor/data/network/NetworkModels.kt`
  - WSS URL 与订阅实现：`app/src/main/java/io/baiyanwu/coinmonitor/data/refresh/StreamingQuoteRefreshEngine.kt`

## TODO

- 增加”行情刷新方式”设置项，允许用户在 `智能 / 仅 WSS / 仅 API` 三种模式之间切换
- `智能` 模式只为交易所行情选择 `WSS / API`；链上价格始终固定使用 DexScreener，不设置隐藏备用源
- `仅 API` 模式继续复用现有轮询引擎和刷新间隔配置，作为弱网、代理环境和问题排查时的稳定兜底
- 给 `REST` 快照刷新和 `WSS` 推送补统一时序保护，避免手动下拉刷新时旧快照短暂覆盖更晚到达的实时价格
- 精简通知栏文案，去掉”每 3 秒刷新一次”这类频率提示，避免在 `WSS` 模式下继续显示过时的轮询描述
- 补齐 `AI 分析` 剩余能力：指标标签选择、结构化指标上下文入 prompt、会话持久化与裁剪、快捷提问模板

## CI/CD

自动 workflow：

- `.github/workflows/android.yml` — CI：单测 + lint + debug 构建
- `.github/workflows/android-release.yml` — Release：单测 + lint + release 构建 + 上传 APK

触发规则：

- `release/*`、`hotfix/*`、`dev`、`main` 的 push 都会触发 `Android CI`
- Release 创建（GitHub Release 发布）或手动触发 `workflow_dispatch` 会触发 `Android Release`
- 仅推送 tag 不等于自动发布

Release 自动流程：

1. `testDebugUnitTest`
2. `:app:lintDebug`
3. `:app:assembleRelease`
4. 上传 APK 到 GitHub Release

## Implementation Notes

- 首页长按快捷菜单挂在同一棵 Compose 树里渲染，不走独立 `PopupWindow`；菜单会先测量真实宽度，再按手指落点附近定位，并补一段轻量的入场动画。
- 首页列表在 ViewModel 首次收到本地数据前会先展示加载态，避免把默认空列表误判为空页面。
- 首页 `CoinSymbolIcon` 会先同步读取本地 / 内存图标缓存，再异步补齐，避免列表滚动时反复闪回占位图。
- 首页实时价格读取下沉到单行价格子树；每个 item 只订阅自己的 quote flow，避免任意一个币价变化时唤醒整屏可见项。
- 首页列表项手势统一收口在自定义 `awaitEachGesture` 流程里：点击、拖动和长按菜单共用一套状态机，避免多套手势监听互相抢占。
- 首页拖动入口为整卡长按，交互时序为 `400ms` 进入拖动、`650ms` 弹出快捷菜单。
- 搜索页和悬浮窗设置页使用独立 `Activity`，避免和主 `NavHost` 的底部导航、转场动画、窗口 inset 相互耦合。
- 首页刷新使用 `PullToRefreshBox`，ViewModel 里维护手动刷新态，避免手势刷新和后台轮询互相打架。
- 第三方 API 设置页与悬浮窗设置页使用和网络日志页一致的 `Scaffold(topBar = CenterAlignedTopAppBar)` 结构，滚动内容只放在 content 区域，避免下方内容滚动时顶部栏被带走。
- 设置页里涉及 `Switch` 的横向行都支持整行点击，不只靠右侧小开关命中。
- 悬浮窗使用 `WindowManager + View`，没有改成 Compose，以降低系统悬浮场景下的重排、生命周期和兼容性风险。
- 悬浮窗”临时隐藏”建模为运行态，不落库；隐藏时立即 `removeViewImmediate`，保证原位置点击可以穿透到底层应用。
- 标准悬浮窗行视图复用已有 `ImageView` / `TextView`，避免高频价格刷新时反复重建 leading 区域导致图标闪动。
- 吸附侧边栏 ticker 做了图标 bitmap 复用和宽度按内容自适应，避免图标闪烁和右侧留白过宽。
- 悬浮窗图标在 `WindowManager` 视图层单独做圆形裁剪，保留外层定宽布局，避免改成圆形后把价格列对齐打乱。
- 悬浮窗全部配置（启停、锁定、透明度、字体、数量、吸附方式、边条透明度、回收时间与窗口坐标）统一由 Preferences DataStore 管理；在悬浮窗链路中，Room 只继续管理币对实体和 `overlaySelected` 选择状态。
- 旧 `overlay_settings` 数据会先暂存并通过 `SharedPreferencesMigration` 一次性导入 DataStore；已经安装过旧 version 8 的开发包也会在 Room 打开前执行兼容导入。
- 前台通知使用自定义 `RemoteViews` 内容布局，统一正文与操作按钮的对齐方式。
- 数据库移除默认破坏性迁移，开启 Room schema 导出，为后续显式 migration 留出接口。
- Room schema 为 `v8`，迁移路径：v4→v5（悬浮窗字体/吸附）→v6（旧链上字段）→v7（首页排序与置顶 + AI 聊天表）→v8（通用链上来源、固定池地址与代币方向 + 旧悬浮窗配置导出）。
- v7→v8 是唯一合并迁移：它会保留旧链上观察项 ID 和引用关系，把来源迁移为通用 `ONCHAIN`，清理旧来源价格快照，同时把旧悬浮窗单行配置暂存给 DataStore 导入；没有并行的 v8→v9 迁移。
- `androidTest` 使用 Room `MigrationTestHelper` 和仓库内导出的 v7/v8 schema，真实创建旧库并执行迁移；测试同时覆盖应用启动前预导出、Room migration 兜底导出，以及 SharedPreferencesMigration 首次导入 DataStore。测试数据库、偏好和 DataStore 目录全部隔离，不读写正式用户配置。
- 为兼容已经运行过早期 version 8 的开发包，v8 schema 暂时保留空的 `overlay_settings` 表壳，但运行时已删除对应 DAO，迁移后也会清空旧行；这张表不再是悬浮窗配置的数据源。
- 调试网络日志只在 Debug 构建输出，Release 默认关闭。
- AI 聊天复用同一套带网络日志拦截器的 `OkHttpClient`，`K线 AI` 请求也会进入网络日志页。
- HTTP 网络日志记录请求头与请求体预览；`Authorization` 会脱敏，响应体不主动展开，避免影响流式 AI 返回。
- 悬浮窗启停规则已统一，避免 UI 开关状态和真实运行状态不一致。
- 搜索页的交易所模式和链上模式使用独立查询状态；链上模式不会混发交易所请求，交易所模式也不会触发 DexScreener。
- 链上搜索采用“代币作为结果、池子作为可切换属性”的模型：观察项 ID 与语义去重仍基于链和合约，池地址只决定报价与 K 线来源。
- 首页列表和搜索结果页共用同一套交易所 badge 视觉：`Binance / Binance Alpha / OKX` 都按统一的强调色标签渲染，避免跨页面样式漂移。
- 行情刷新拆成”全局协调器 + 可替换刷新引擎”两层结构；交易所保留 `WSS`，链上保持独立 REST 轮询。
- 流式引擎内部对订阅集合做指纹比较，避免价格回流导致重复重建长连接。
- vendored chart wrapper 关闭了 `WebView` 自身页面缩放，避免系统层缩放和图表手势混在一起。
- vendored wrapper 生成产物直接提交 `src/main/assets/com/tradingview/lightweightcharts/scripts/app/main.js`，不执行 `npm run compile` 时也能直接构建运行。
