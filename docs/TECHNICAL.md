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
  overlay/     悬浮窗类型路由、前台服务、轮询协调器
    arranged/  排列型窗口、布局、吸附和隐藏边条
    marquee/   跑马灯型窗口、循环轨道、动画和纵向拖动
  ui/          Compose 页面、主题、Activity 宿主
```

整体设计保持单模块结构，优先保证可读性、实现速度和后续演进空间，而不是过早做复杂模块拆分。

## Core Behavior

### Watchlist

- 首页观察列表拆分为可点击、可左右滑动的“交易所 / 链上”两个分页，默认显示交易所；交易所页包含 `CEX_SPOT / CEX_USDT_FUTURES`，链上页只包含 `ONCHAIN_TOKEN`
- 两个分页分别保存当前进程内的滚动位置和拖动状态；分页选择可在页面重建时恢复，但不跨冷启动持久化
- 首页添加按钮和分类空状态入口会把当前市场模式传给搜索页；其他搜索入口仍默认进入交易所模式
- 支持添加、删除和全局手动刷新；两个分页分别展示空状态，删除与悬浮窗选择继续复用原有行为
- 首页首屏会先等待本地数据回流，避免“先闪空态按钮、再切列表”
- 首页现在只保留一套手动顺序源，不再区分名字排序、价格排序等模式
- 首页支持整卡长按拖动排序，并支持置顶 / 取消置顶；置顶项固定排在普通项之前，拖动只调整当前市场分类及同一置顶分组内的相对顺序
- 长按单个币对会弹出跟手快捷菜单，支持删除、置顶 / 取消置顶，以及加入或移出悬浮窗
- 首页行情行采用紧凑密度：缩小主币对、价格、涨跌幅、来源标签和图标，并压缩行内间距与垂直内边距
- 首页币对标题通过系统 URI 处理器打开外部行情页：Binance / OKX 使用持久化的真实 instrument ID，Binance Alpha 回退到官方 Alpha 行情目录，链上优先打开当前固定池的 DexScreener 页面，旧数据缺少池地址时回退到合约搜索
- 首页链上观察项在副信息行显示合约地址头尾缩写和复制按钮；复制操作写入完整纯文本 CA 并给出 Toast 反馈，交易所观察项不显示该入口
- 首页价格继续保持实时显示，但实时价格更新已经从 Room 高频写回中拆出，改为走内存报价状态，降低大量 `WSS` 推送时的滚动抖动

### Search

- 支持关键字搜索
- 页面顶部拆分为可点击、可左右滑动的“交易所 / 链上”两个模式；两个模式各自保存独立输入内容、加载状态和搜索结果
- 左上角返回按钮只负责退出搜索页，输入框右侧“确认”只负责提交当前模式的搜索；键盘搜索动作与“确认”复用同一提交逻辑
- 交易所模式并行搜索 `Binance Alpha / Binance Spot / Binance USDT-M Futures / OKX Spot / OKX USDT Swap`；等待五路来源全部结束后统一合并、排序并一次性展示结果。单个来源失败会短延迟重试一次，仍失败时保留其他来源结果并明确提示部分来源不可用；同一关键词重复提交不会重建整组请求
- 链上模式按用户在第三方 API 设置中保存的 `DexScreener / OKX DEX` 顺序搜索代币，不再提供手动选链控件
- 链上搜索支持币名 / Symbol / 合约地址输入；合约地址会先识别为 `EVM` 或 `Solana` 地址，再从对应链族的返回结果中定位具体链
- 链上结果按“`chainIndex + normalized token address`”做语义去重，同一条链上的同一代币不会因为存在多个池而重复展示
- 链上结果主标题直接展示当前实际交易对，并同时展示链 Logo、DEX、美元流动性和合约地址缩写
- 同一代币存在多个有效池时，可在当前结果内展开备选池；界面最多展示流动性排名靠前的 5 个备选，完整候选仅保留在当前搜索结果内存中，用于还原历史固定池
- 搜索页当前按入口模式分流：
  - 从首页进入时，结果页继续承担观察列表的 `添加 / 删除` 管理
  - 从 K 线页进入时，结果页隐藏增删按钮，点击单条结果后会回填到 K 线页并立即关闭搜索页；当前 K 线公开入口已隐藏，这一路径作为保留实现暂不暴露

### Settings

- 设置页顶部新增“通用设置”分组，其中“显示链上市值”通过 Preferences DataStore 全局持久化；该状态由首页和前台悬浮服务共同观察，冷启动、Activity 重建与悬浮窗重建后保持一致，仅改变 `ONCHAIN_TOKEN` 的主数值展示
- 设置页新增独立 `AboutActivity`，沿用统一 Compose Activity 宿主、主题、语言和详情页转场
- “关于”页通过 `BuildConfig.VERSION_NAME / VERSION_CODE` 读取当前构建版本，版本信息不在字符串资源中重复维护
- 页面集中展示项目用途、作者 `baiyanwu`、GitHub 源码仓库、Apache-2.0 许可、Issues 反馈入口及行情风险说明；外部链接统一交由系统 URI 处理器打开
- 关于页和设置入口均提供简体中文与英文资源；设置入口只展示“关于”标题，不附加重复副标题

### App Update Check

- `MainActivity` 每次创建主界面 Compose 内容时，通过 `LaunchedEffect` 调用一次 `GitHubReleaseUpdateChecker`；不记录上次检查时间，也没有 24 小时缓存
- 检查器使用应用已有的 `OkHttpClient`，直接请求 GitHub 官方 `GET https://api.github.com/repos/baiyanwu/CoinMonitor/releases/latest`，请求沿用全局 `10 秒` call timeout，不依赖第三方更新服务或 API Key
- 远端 `tag_name` 与 `BuildConfig.VERSION_NAME` 按数字段比较，兼容 `v1.0.7`、`1.0.7` 与不同段数；标签无法解析、网络失败或非成功响应时静默跳过，不阻塞应用启动
- 只在远端版本更高时展示双语提示；用户确认后交由系统 URI 处理器打开 `https://github.com/baiyanwu/CoinMonitor/releases/latest`，应用不自动下载或安装 APK

### K-line

- K 线页、图表、指标设置、搜索回填和 AI 聊天实现仍保留在工程中，但当前不再作为底部导航或首页卡片点击入口暴露
- `NavHost` 中仍保留 `Destinations.KLINE` route，用于后续恢复入口时复用既有实现；底部导航按“首页 / 钱包 / 浏览 / 设置”展示四个一级页面
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

- 观察地址已提升为底部导航中间的“钱包”一级页面，直接由主 `NavHost` 承载 `WalletWatchRoute`；首页“链上”分页不再显示钱包摘要横栏或独立刷新入口。钱包页与首页、设置之间使用顶层 Tab 状态恢复，不显示二级页面返回按钮；系统栏和底部导航安全区统一由主 `Scaffold` 传入，钱包页内层 `Scaffold` 使用零 `contentWindowInsets`，避免重复计算顶部安全区。钱包资产模型、仓库和 UI 组件仍不复用首页币对模型
- 观察地址支持 EVM 与 Solana，分别路由至 OKX EVM ChainIndex 注册表和 Solana `501`；明细与 token-only 总值并发请求，金额全程使用 `BigDecimal`
- OKX API Key、Secret Key、Passphrase 使用独立的 `okx_wallet_credentials_secure` 加密偏好保存；安全存储不可用时拒绝明文降级，日志拦截器统一脱敏 OKX 鉴权 Header
- 观察地址按链切换资产列表；链栏可进入编辑状态并隐藏整条链，长按资产可隐藏单币。两类状态按钱包地址分别持久化，并在底部“已隐藏”面板中分组恢复；页头总资产和数量由全部可见资产汇总，隐藏与恢复会立即同步数值
- “隐藏风险资产”默认开启；切换时同时更新风险资产可见性并重取对应 OKX 总值。“隐藏小于 1U”只影响当前列表显示，并将无价格或无法计算持仓价值的资产按小额资产处理。完整设计和接口契约见 [WALLET_WATCH.md](WALLET_WATCH.md)

- 当前链上能力提供搜索、最新价格、24 小时涨跌、流动性、成交量与 K 线，不提供交易执行
- `OnchainProviderRouter` 是搜索、报价优先级、链身份适配和故障转移的唯一入口；它按用户保存的顺序生成已经绑定 Provider 的请求计划，默认仍为 `DexScreener -> OKX DEX`
- 每个 `OnchainMarketProvider` 暴露自己的 `OnchainPollingStrategy`；调度器只执行 Router 生成的 Provider 批次，不在请求发出时重新选择来源。报价只把尚未返回的标的生成一次性备用批次，并通过标准链身份在 DexScreener chain id 与 OKX ChainIndex 之间转换，未知链无法安全映射时不会跨 Provider 请求
- OKX 支持链从 `/api/v6/dex/market/supported/chain` 动态获取并缓存，不在客户端写死完整 Chain ID 列表；搜索使用 `/api/v6/dex/market/token/search`，持续报价使用 `/api/v6/dex/market/price-info`
- 用户未配置 OKX 且可用 Provider 均无结果时，搜索页显示前往现有第三方 API 设置的提示；项目不打包开发者 API Key、Secret 或 Passphrase
- K 线继续使用无需 API Key 的 `GeckoTerminal`；本次不为 OKX 回退标的提供 K 线
- 链上搜索不再把本地注册表作为白名单：DexScreener 返回的非空 `chainId` 都会参与结果解析，且不再施加本地 80 条结果上限
- 本地注册表继续为 17 条已知链提供精确 `chainIndex`、DexScreener 与 GeckoTerminal 网络映射；未知链直接持久化 DexScreener `chainId`，后续报价沿用该标识，K 线以同名 GeckoTerminal 网络作最佳努力请求
- EVM 合约地址统一转为小写，Solana 与其他链地址保留原始大小写；未知网络会根据返回的代币地址形态区分 EVM 与其他链
- 搜索选池先要求目标合约精确匹配，并排除无有效美元价格或无流动性的池；随后依次按目标代币位于 `base` 侧、美元流动性、24 小时成交量和池地址排序
- DexScreener 网络 DTO 按官方契约容纳显式 `null`：`pairs`、`labels`、`priceChange` 在解码层保持可空，并在客户端或业务边界统一归一化为空集合，避免单个缺失字段导致整次搜索或报价解析失败
- DexScreener 的 `marketCap` 从搜索、固定池报价、`MarketQuote`、内存 `QuoteState` 到 Room 快照保持可空传递；只有目标合约位于所选池的 base 侧时才接受该值，quote 侧不会误用 base 代币市值，也不以 `fdv` 回退。无有效市值时统一显示 `--`
- 添加观察项时固定池地址和目标代币的 `base / quote` 方向；用户从搜索结果切换池后，已添加标的立即更新绑定，未添加标的会在添加时保存当前选择
- 链上观察项的 `symbol` 保存为当前池的 `目标币 / 另一侧币种`；生成标签时无论目标合约位于池子的 `base` 还是 `quote` 侧，都固定把目标币放在前面。新添加和切池时立即更新，历史项在下一次通过绑定校验的 DexScreener 报价落库时自动回填，不新增 Room 字段或迁移
- 同一标的连续切池时会取消上一任务，并等待上一代数据库写入完全结束后再写入最新选择；报价落库还会比较“请求发起时的池绑定”和当前绑定，拒绝迟到旧请求回写池地址或价格
- 搜索到已有观察项时，会优先在完整候选中恢复数据库里的固定池，避免搜索结果显示的池与实际报价、K 线来源不一致
- 后续价格刷新和 GeckoTerminal K 线共用同一个固定池；只有固定池明确失效或连续缺失后才自动重选
- GeckoTerminal 只接收官方支持的聚合参数：`1m / 5m / 15m` 使用 minute，`1H / 4H` 使用 hour，`1D` 使用 `day + aggregate=1`
- `3D / 1W / 30D` 不再向上游发送无效的 `day + aggregate=3/7/30`；应用改为请求日线后在本地合并 OHLCV，其中周线按 UTC 周一对齐，`30D` 是固定 30 天而非自然月，交易所仍显示自然月 `1M`
- 长周期请求按目标根数扩展日线数量；超过 GeckoTerminal 单次 1000 根时使用 `before_timestamp` 向前分页，直到达到目标、上游无更多历史或请求被取消。页面默认仍以最多 240 根合成 K 线为目标，实际根数受池子创建时间和免费接口可用历史限制
- 报价和 K 线捕获普通网络异常时不会捕获 `CancellationException`，快速切换标的、周期或重启刷新任务后，旧任务不会继续更新 UI
- 搜索结果通过 `LazyColumn.itemsIndexed` 逐条组合和回收，不再在单个 lazy item 内用 `forEach` 一次性组合全部结果
- 代币图标优先使用 DexScreener 返回的公开 `info.imageUrl`；链 Logo 优先使用本地映射，未命中或下载失败时依次尝试在线链图标候选
- 链上代币缺少自身图标时会按链 Logo 候选依次回退；原生悬浮窗严格按“代币图标缓存/下载 → 链 Logo 缓存/下载”的候选顺序处理，低优先级的已缓存链 Logo 不会抢占尚未缓存的代币图标。所有在线候选都失败时，Compose 列表和原生悬浮窗都使用内置默认占位图，网络异常不会向上抛出中断渲染

### Overlay

- 顶层提供 `ARRANGED / MARQUEE` 两种显示类型，设置页分别显示为“排列型 / 跑马灯型”；默认值为 `ARRANGED`，升级后保持原外观和行为
- 当前最多允许选择 `10` 个共用悬浮窗币对；超过上限时会在入口页给出提示，限制值统一收口在 `OverlaySettings.MAX_SELECTABLE_ITEMS`。排列型和跑马灯型共用一份跨交易所/链上的 `overlayOrder`，再按各自 `maxItems` 截取前 N 项
- 两种类型共用启用、锁定和币对选择，透明度、字体、展示数量和位置分别保存；排列型和跑马灯型的主体透明度都支持真实的 `0%–72%`，设置页只展示当前类型的专属配置
- 两种主体透明度滑杆在拖动期间只更新设置页内的本地百分比，松手时才一次性写入 DataStore 并通知对应悬浮窗；避免连续拖动与跑马灯动画或排列型窗口刷新叠加造成卡顿。
- 排列型默认最多显示 `5` 个币对，支持字体、左侧图标 / 名称、布局方向、左右吸附和隐藏边条等原有设置
- 悬浮窗中的币种图标统一按圆形裁剪，和应用内列表的视觉语义保持一致
- 排列型“吸附靠边”总开关下提供两个互斥模式：“仅吸附靠边”继续展示靠边价格面板，“彻底隐藏”只保留屏幕边缘唤出条
- 彻底隐藏模式的唤出条使用主题色，透明度可在 `15%–100%` 范围连续调整，默认 `45%`
- 点击唤出条时，边条先消失，价格区域按当前左右吸附方向滑入，并使用和“仅吸附靠边”完全相同的单项靠边轮播；自动收回时价格区域反向滑出，结束后才重新显示边条
- 自动收回时间支持 `1–5 秒` 整秒选择，默认 `3 秒`；拖动悬浮窗期间会延后回收，避免定时器打断手势
- 跑马灯使用 `MATCH_PARENT × WRAP_CONTENT` 的单行窗口，基础高度为 `32dp × fontScale`，X 固定为 `0`，Y 限制在可用屏幕纵向范围并独立持久化
- 跑马灯默认最多显示全部 `10` 个已选币对；每项使用 `16dp` 圆形币种图标和在轨道创建时按当前价格格式独立测量、最大 `58dp` 的左对齐最新价列；图标左右内距与项末间距统一为 `4dp`，不显示 24 小时涨跌
- 每个完整数据周期末尾插入一个宽度为项末间距两倍的低亮度圆点分隔槽；当前项末间距为 `4dp`，因此分隔槽为 `8dp`，圆点直径为 `3dp`。由于首项图标内距也为 `4dp`，圆点固定居中后到末项价格及下一轮首项图标的可见距离，均与普通 Item 之间的可见距离相同；分隔槽计入单周期宽度和动画位移，不破坏循环接缝
- 跑马灯背景保留透明度设置，但外层不绘制边界线；图标复用公共缓存并按圆形裁剪，在线图标缺失时显示内置圆形占位
- 跑马灯按 `24 / 40 / 56 dp/s` 提供慢速、正常、快速三档；轨道复制到足以覆盖屏幕并线性无限移动一个内容周期，形成无空白接缝
- 跑马灯价格列宽在每次轨道创建后冻结，行情刷新只更新实际变化的价格文本和颜色，不再重新测量价格、重绑图标、更新窗口布局或重启动画；只有币对集合、字体、速度、主题或屏幕宽度变化时才重建轨道。系统动画关闭时回退为静态横排行情
- 首页、排列型与跑马灯型统一由全局偏好决定链上主数值显示价格或市值；市值文本由 `QuoteFormatter.formatMarketCap` 统一压缩为美元单位。红绿闪烁始终比较底层真实价格，新价格上涨/下跌时短暂着色后恢复中性色，相同价格或缺少前值时不触发
- 悬浮窗行情协调器对相同渲染快照去重，并在主线程繁忙时只保留最新快照，避免未选币对更新或积压的中间行情重复触发窗口渲染
- 前台服务和应用页面当前位于同一进程并共享主线程，系统悬浮窗口与 `Activity` 又分别拥有独立 `ViewRoot`；因此在 `120Hz` 等高刷新率设备上，首页滚动或页面切换的大量重绘仍可能短暂延迟跑马灯帧。现有优化解决的是行情刷新造成的额外工作，不等同于渲染线程隔离；如后续要求彻底隔离，需要把跑马灯改为独立 `Surface` 渲染线程或单独进程，并重新验证透明合成、触摸、图标与数据同步
- 实体机对比已否决两种局部方案：强制整条轨道使用硬件缓存层会放大宽纹理合成成本，手动用 `Choreographer` 在高刷屏限频也会增加截止帧；当前继续使用系统 `ObjectAnimator`，不保留这两类试验代码
- 未锁定时，按下跑马灯会暂停动画，超过触摸阈值后只允许上下拖动，松手保存位置并继续滚动；锁定后窗口不可触摸但行情与动画继续运行
- 通知栏支持临时隐藏 / 恢复显示，以及拖动开关
- 只有在悬浮窗权限满足时，应用才会把悬浮窗正式标记为启用
- 悬浮窗设置页顶部只保留“悬浮币对”入口；独立的 `OverlayItemsSettingsActivity` 和 `OverlayItemsSettingsViewModel` 承载选择、10 项上限与排序业务，避免外观设置页过长
- 悬浮币对页将已选币对和可添加币对分区：已选区显示全局序号、交易来源、市场类型、当前显示范围与专用拖动把手，可添加区继续按交易所/链上分组。拖动把手从按下起独占指针，拖动期间父级滚动停用；列表项使用稳定 ID 保存 Compose 节点身份，换位时同步更新相邻项坐标缓存，显示范围标记不改变行高，结束后一次性持久化
- 悬浮币对页只观察一份 Room 关注列表快照，再按 `overlaySelected/overlayOrder` 原子拆分已选区和可添加区；非拖动状态直接渲染最新快照，本地副本仅在拖动期间存在，避免开关币对时上下分区不同步或晚一帧刷新
- 悬浮窗顺序由 Room 中独立的可空 `overlayOrder` 决定，不再复用 `homePinned`、`homeOrder` 或 `homePinnedOrder`；首页排序只影响对应市场分页，悬浮排序可以跨市场调整且不回写首页字段
- 开启悬浮币对时在同一事务内检查 10 项上限并追加到顺序末尾；关闭时同时清除选择状态和顺序，重新开启仍追加末尾。拖动结束后统一重编号，排列型从上到下、跑马灯型从左到右消费同一顺序
- 排列型不按 `5` 个一组分页轮播，而是直接按当前 `maxItems` 铺开显示
- 排列型靠边轮播不使用整串 `TextView marquee`，而是单项切换：当前“图标 + 价格”向左滑出，下一个 item 从右侧滑入；WSS 更新只刷新内容，不重置切换节奏

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
- 链上价格先由 Router 按用户优先级和 Provider 能力绑定来源，再由各 Provider 的策略按链拆批：DexScreener 每批最多 30 个不同地址，OKX DEX 每批最多 100 个。新增 Provider 只需提供自己的批量上限、智能周期、最小请求间隔和退避策略
- “智能刷新”由每个 Provider 独立计算周期；当批次数无法在默认周期和最小请求间隔内完成时，策略会自动拉长该 Provider 的轮转周期。固定模式同样不会突破 Provider 的安全下限
- OKX `price-info` 单次最多查询 100 个代币，且属于 Premium API；免费订阅每月包含 100,000 次 Premium 调用。客户端以 31 天窗口给后台轮询分配 80,000 次预算，预留约 20% 给搜索、手动刷新和其他用户操作，因此 1 个 OKX 批次最低约 34 秒一轮，2 个批次最低约 67 秒一轮，并随批次数自动增长。额度依据见 [OKX Token Price Info](https://web3.okx.com/zh-hans/onchainos/dev-docs/market/market-token-price-info) 与 [OKX Market API Fee](https://web3.okx.com/fi/onchainos/dev-docs/market/market-api-fee)
- 首页手动刷新复用同一条链上请求队列，30 秒内已成功刷新的批次不会重复发送；单批失败按 `5 / 10 / 20 / 30 秒`独立退避，其他批次继续轮转
- DexScreener 客户端统一限制在每分钟最多 240 次请求，为公开接口限额保留余量；429 会优先遵守 `Retry-After`，否则执行带随机抖动的指数退避
- 链上 K 线固定使用 `GeckoTerminal`，按已保存的池地址和目标代币方向查询，并在客户端限制为每分钟最多 8 次
- 链上刷新设置页使用智能/固定分段按钮与固定周期选项，并展示当前请求批次数、批次间隔、轮转周期和失败重试批次数
- HTTP / WSS 网络日志会脱敏 API Key、签名、Passphrase、鉴权头与 Cookie；钱包 Portfolio、逐链 RPC、活动、手续费、广播和状态查询复用同一带拦截器的 `OkHttpClient`

## Self-custody Wallet

- Wallet Core `4.8.3` 负责 BIP39、EVM/Solana 地址派生与交易签名；网络数据与广播不进入 Wallet Core。
- `DefaultWalletVaultRepository` 管理加密的多钱包保险库；`DefaultWalletNetworkSettingsRepository` 独立管理 Alchemy Key、动态 EVM 目录、启用网络与逐链 RPC，二者都拒绝在 Android 安全存储不可用时降级。
- `DefaultSelfCustodyWalletRepository` 执行 `custom RPC > Alchemy RPC` 的交易节点路由；OKX Onchain Balance/Token API 是资产、价格、元数据与风险标记索引层，Alchemy Transfers 只保留为活动历史索引。缓存按 wallet ID 隔离。
- 钱包刷新按 EVM 地址与 Solana 地址分别查询当前启用网络，并与 OKX 实时支持链取交集。OKX 整体失败时保留最后缓存；部分地址体系或不支持网络只记录到对应资产失败集合。活动索引失败保持独立，不会把正常空资产误报为错误。
- 自托管钱包首次默认启用 Ethereum、BNB Chain、Robinhood Chain 与 Solana；网络栏复用观察地址页的紧凑编辑样式。停用网络会从持久化的启用列表移除并停止后续拉取，恢复入口统一放在钱包网络设置中。
- 自托管钱包复用观察地址的总资产卡、紧凑资产行与 `AssetAmountFormatter`。默认隐藏估值小于 1 美元和 OKX 标记的风险资产；缺少可靠 Token 精度的资产可展示但不可进入转账流程。
- 默认 EVM 网络元数据来自应用内种子与 `docs/wallet-evm-networks.json` 远程目录，使用 CAIP-2 风格 `eip155:<chainId>` 作为稳定标识；用户也可添加经 `eth_chainId` 验证的任意标准 EVM RPC。资产覆盖由 OKX 实时支持链决定，Alchemy Portfolio 标记不再控制资产可见性。
- 原 `WalletWatchRoute` 由独立 `WalletWatchActivity` 承载；观察地址与自托管钱包复用 OKX 客户端实现和同一套加密凭证，但各自持有客户端实例，并保持独立模型、筛选与缓存。
- 详细规则、构建凭证和验证边界见 [SELF_CUSTODY_WALLET.md](SELF_CUSTODY_WALLET.md)。

## DApp Browser

- `MainShell` 的浏览 destination 只渲染 `DappDiscoveryRoute`，MainActivity 不创建或持有 WebView。发现页由 `DappDiscoveryRepository` 读取 `res/raw/dapp_catalog_v1.json`，搜索历史通过 Room `dapp_search_history` 保存；点击目录条目或输入 HTTPS 地址后启动独立 `DappBrowserActivity`。
- `DappBrowserActivity` 独占一个 `DappWebView` 与 `AndroidView` host，并在 Activity 销毁时统一释放。`DappBrowserRoute` 负责可编辑 HTTPS 地址栏、历史导航、加载进度和链图标菜单；返回键优先回退网页历史，没有网页历史时结束 Activity。这样浏览器渲染 surface、Provider 弹窗和网页生命周期不会进入 MainActivity 的 NavHost。
- `DappWebView` 由 Activity 提前创建后再交给 Compose `AndroidView` 承载，因此自身必须显式使用 `MATCH_PARENT × MATCH_PARENT` 的原生布局参数，避免 Chromium 的布局 viewport 高度为 0，进而把依赖 `vh/dvh/svh/lvh` 的 DApp 底部弹层压缩掉。Provider document-start 注入仍在首次导航前完成。
- AndroidX WebKit 通过 document-start script 与 web message listener 注入 Trust Web3 Provider `4.9.4`，不使用 `addJavascriptInterface`。浏览器对任意页面注册 Provider，但 WebView 仅允许 HTTPS 导航，原生消息入口只接受 HTTPS 主 frame，并按发起 origin 投递异步响应。最小 bootstrap 初始化 Trust Core/Ethereum Provider、建立原生回调、暴露标准 `window.ethereum`、Trust 兼容命名空间并发布 EIP-6963 Provider；EIP-6963 身份保持 CoinMonitor，图标在运行时从 Android 应用图标生成 PNG Data URI，并通过 Trust Provider 自带的 `setOverwriteMetamask(true)` 兼容旧版 MetaMask Connector，不注入 `window.metamask`、MetaMask 专属 API、`window.web3` 或页面 DOM/CSS 修补。内置目录只负责发现页展示，手输网址在导航前显示第三方风险提示。
- Provider 暴露 EIP-1193 `window.ethereum` 和 EIP-6963 发现入口。`DappProviderProtocol` 只解析并路由 Trust Provider callback；交易预检/广播、消息签名、钱包控制分别收口在 `DappBrowserRepository`、`DappSigningRepository`、`DappWalletControlRepository`，原生确认 UI 独立放在 `DappApprovalDialogs`，避免协议、密码学、RPC 和 Compose 状态混在一个类中。交易与消息签名确认同时支持钱包密码和已启用的强生物识别；`rememberWalletBiometricAuthorization` 对每个新请求自动触发一次指纹，取消、失败或凭证失效后展开密码输入，也允许用户主动点击“输入密码”切换。生物识别只解包 Android Keystore 保护的派生密钥，ViewModel 使用后立即清零，再沿原执行器取得私钥并完成签名。统一授权规则是密码与生物识别默认同级；删除单个钱包、重置保险库、显示/备份钱包密钥以及停用生物识别仍强制使用钱包密码。
- RPC 复用 `DappBrowserRepository -> WalletRpcClient` 与钱包的 `custom RPC > Alchemy RPC` 路由，只开放显式只读方法白名单。`eth_sendTransaction` 会校验活动地址、Chain ID、交易类型和 calldata，由 RPC 补齐 nonce、估算 Gas、获取费用并检查原生币余额；用户在原生弹窗输入钱包密码后，由 Wallet Core 签名 Legacy 或无 access list 的 EIP-1559 交易，再通过当前 RPC 广播并向网页返回交易哈希。
- `signPersonalMessage / signMessage / signTypedMessage` 分别映射 `personal_sign`、原始 `eth_sign`、EIP-712 V3/V4。Typed Data 重新解析 `raw` JSON 并校验地址、Chain ID、`primaryType` 与验证合约；确认后使用 Wallet Core `EthereumMessageSigner` 或 secp256k1 原始摘要签名，只返回标准 `0x` 65 字节签名，不触发网络广播。私钥字节在调用完成后清零。
- `wallet_addEthereumChain` 仅接受 HTTPS RPC，确认后由 `WalletNetworkSettingsRepository` 实际校验 RPC Chain ID 再保存和切换；`wallet_watchAsset` 将 ERC20/BEP20 元数据按钱包持久化，资产刷新通过 RPC 读取余额并合并为 `userAdded` 资产。EIP-712 V1、access list 和非白名单 RPC 仍返回 `4200`；用户拒绝任一原生确认返回 `4001`。
- WebView 允许 HTTPS 主 frame 在内置地址栏中导航，禁止 HTTP、mixed content、文件和 content URI 访问；非 Web scheme 交给系统处理，TLS 错误沿用 WebView 默认拒绝行为。用户从链图标菜单选择已启用 EVM 网络后，ViewModel 更新 RPC 路由并向网页发送 EIP-1193 `chainChanged`。

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
- `智能` 模式只为交易所行情选择 `WSS / API`；链上调度使用 Provider 自己的 `OnchainPollingStrategy`。首选失败时 Router 为本轮未返回标的生成一次性备用批次，不修改观察项持久化来源
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
- 首页交易所和链上列表统一使用 Coil Compose 请求、解码并维护代币图标的内存与磁盘缓存；项目代码只提供代币图标、链图标和 Symbol 查询 URL 的回退顺序。非 Compose 原生悬浮窗继续使用 `CoinIconService` 位图缓存。
- 首页实时价格读取下沉到单行价格子树；每个 item 只订阅自己的 quote flow，避免任意一个币价变化时唤醒整屏可见项。
- 首页与两种悬浮窗的文本币价统一经过 `QuoteFormatter.formatPrice`；悬浮窗只在 `formatOverlayPrice` 外层补充合约标识，不另做数字格式化。小数部分连续前导 0 达到 3 个时使用 Unicode 下标计数压缩，例如 `0.0001234` 显示为 `0.0₃1234`。
- 观察钱包与自托管钱包的法币、Token 数量、Token 价格和网络费统一通过 `AssetAmountFormatter` 的 `BigDecimal` 规则显示；行情 `QuoteFormatter` 保持独立，因为其输入精度和极小价格展示语义不同。
- 首页列表项手势统一收口在自定义 `awaitEachGesture` 流程里：点击、拖动和长按菜单共用一套状态机，避免多套手势监听互相抢占。
- 首页拖动入口为整卡长按，交互时序为 `350ms` 进入拖动、`900ms` 弹出快捷菜单。
- 首页与搜索页共用 `MarketModeTabs`；首页用 `HorizontalPager` 承载两个分类页面，并为每页维护独立的 `LazyListState` 和拖动状态。
- 首页右下角币对操作菜单使用重叠的触发按钮和关闭按钮承接展开动画；两层按钮固定使用同一 `16dp` 圆角，避免关闭按钮变为纯圆后露出底层按钮四角。图标和容器颜色继续随展开进度过渡。
- 搜索页、悬浮窗设置页、悬浮币对设置页和关于页使用独立 `Activity`，避免和主 `NavHost` 的底部导航、转场动画、窗口 inset 相互耦合。
- 应用更新检查绑定主界面组合生命周期，每次主界面创建只请求一次；请求取消会继续向上抛出 `CancellationException`，普通网络或解析失败才静默忽略。
- 首页刷新使用 `PullToRefreshBox`，ViewModel 里维护手动刷新态，避免手势刷新和后台轮询互相打架。
- 第三方 API 设置页、悬浮窗设置页、悬浮币对设置页与关于页使用和网络日志页一致的 `Scaffold(topBar = CenterAlignedTopAppBar)` 结构，滚动内容只放在 content 区域，避免下方内容滚动时顶部栏被带走。
- 设置页里涉及 `Switch` 的横向行都支持整行点击，不只靠右侧小开关命中。
- 悬浮窗使用 `WindowManager + View`，没有改成 Compose，以降低系统悬浮场景下的重排、生命周期和兼容性风险。
- 顶层 `OverlayWindowController` 只根据显示类型路由行情和切换窗口；排列型与跑马灯型分别拥有窗口根节点、`WindowManager.LayoutParams`、渲染状态、动画及触摸处理，两个实现互不调用。跑马灯使用直接作用于 `translationX` 的属性动画，不再通过业务层逐帧回调写入位移。
- 类型切换时协调器先同步移除旧类型窗口，再显示新类型；同类型行情更新不会重建窗口。
- 悬浮窗”临时隐藏”建模为运行态，不落库；隐藏时立即 `removeViewImmediate`，保证原位置点击可以穿透到底层应用。
- 标准悬浮窗行视图复用已有 `ImageView` / `TextView`，避免高频价格刷新时反复重建 leading 区域导致图标闪动。
- 吸附侧边栏 ticker 做了图标 bitmap 复用和宽度按内容自适应，避免图标闪烁和右侧留白过宽。
- 悬浮窗图标在 `WindowManager` 视图层单独做圆形裁剪，保留外层定宽布局，避免改成圆形后把价格列对齐打乱。
- 悬浮窗全部外观配置统一由 Preferences DataStore 管理：启停、锁定与类型为公共键；旧透明度、字体、数量、吸附方式、边条及坐标键继续作为排列型配置；跑马灯使用独立的透明度、字体、数量、速度和 Y 坐标键。在悬浮窗链路中，Room 管理币对实体、`overlaySelected` 选择状态和 `overlayOrder` 全局顺序，选择与顺序写入保持同一事务。
- 旧边缘模式字符串 `TICKER` 兼容读取为排列型内部的 `DOCKED` 靠边轮播；跑马灯首次没有专属 Y 坐标时继承旧窗口 Y，仍不存在时使用 `180dp` 默认值。
- 透明度范围升级后，旧配置中原先代表滑杆 `0%` 的 `0.16` 会一次性解释并保存为真实 `0`；新范围通过版本键区分，因此之后仍可准确保存 `0%–72%` 内的任意值。
- 旧 `overlay_settings` 数据会先暂存并通过 `SharedPreferencesMigration` 一次性导入 DataStore；已经安装过旧 version 8 的开发包也会在 Room 打开前执行兼容导入。
- 前台通知使用自定义 `RemoteViews` 内容布局，统一正文与操作按钮的对齐方式。
- 数据库移除默认破坏性迁移，开启 Room schema 导出，为后续显式 migration 留出接口。
- Room schema 为 `v11`，迁移路径：v4→v5（悬浮窗字体/吸附）→v6（旧链上字段）→v7（首页排序与置顶 + AI 聊天表）→v8（通用链上来源、固定池地址与代币方向 + 旧悬浮窗配置导出）→v9（悬浮币对独立全局顺序）→v10（观察项可空 `marketCap`）→v11（链上观察项 Provider 归属）。
- v7→v8 继续负责保留旧链上观察项 ID、迁移通用 `ONCHAIN` 来源、清理旧价格快照并把旧悬浮配置暂存给 DataStore；v8→v9 只增加可空 `overlayOrder`，v9→v10 只增加可空 `marketCap`，v10→v11 增加非空 `onchainDataProvider` 并将历史项默认归为 `DEX_SCREENER`，均不破坏已有观察列表与排序状态。
- `androidTest` 使用 Room `MigrationTestHelper` 和仓库内导出的 v7/v8/v9/v10/v11 schema，覆盖 v7→v8、v8→v9、v9→v10、v10→v11 与连续升级；测试数据库、偏好和 DataStore 目录全部隔离，不读写正式用户配置。
- 为兼容已经运行过早期 version 8 的开发包，v8 schema 暂时保留空的 `overlay_settings` 表壳，但运行时已删除对应 DAO，迁移后也会清空旧行；这张表不再是悬浮窗配置的数据源。
- 调试网络日志只在 Debug 构建输出，Release 默认关闭。
- AI 聊天复用同一套带网络日志拦截器的 `OkHttpClient`，`K线 AI` 请求也会进入网络日志页。
- HTTP 网络日志将请求、响应和失败保存为带类型的内存事件；页面分别统计 HTTP 请求次数、失败次数、WSS 事件数和日志条数。一次成功 HTTP 请求通常对应两条日志但只计一次请求。记录最多保留 800 条，进程结束后清空。
- HTTP 日志记录请求头与请求体预览；`Authorization` 和 URL 中的 API Key 会脱敏。响应体不记录正常业务数据，只通过 `peekBody` 记录非 2xx 或包含 `error` 的有限预览，因此不会消费流式响应。
- 悬浮窗启停规则已统一，避免 UI 开关状态和真实运行状态不一致。
- 搜索页的交易所模式和链上模式使用独立查询状态；链上模式不会混发交易所请求，交易所模式也不会触发 DexScreener 或 OKX DEX Provider。
- 链上搜索采用“代币作为结果、池子作为可切换属性”的模型：观察项 ID 与语义去重仍基于链和合约，池地址只决定报价与 K 线来源。
- 首页列表和搜索结果页共用同一套交易所 badge 视觉：`Binance / Binance Alpha / OKX` 都按统一的强调色标签渲染，避免跨页面样式漂移。
- 行情刷新拆成”全局协调器 + 可替换刷新引擎”两层结构；交易所保留 `WSS`，链上保持独立 REST 轮询。
- 流式引擎内部对订阅集合做指纹比较，避免价格回流导致重复重建长连接。
- vendored chart wrapper 关闭了 `WebView` 自身页面缩放，避免系统层缩放和图表手势混在一起。
- vendored wrapper 生成产物直接提交 `src/main/assets/com/tradingview/lightweightcharts/scripts/app/main.js`，不执行 `npm run compile` 时也能直接构建运行。
