# Technical Notes

这个文档用于集中说明 `CoinMonitor` 的技术实现细节、工程结构和当前设计取舍。

项目当前主要通过 `vibecoding` 的方式完成设计、实现和迭代，但代码层面仍然按 Android 常规工程结构收口，优先保证可运行、可维护和便于后续继续演进。

## Tech Stack

- Kotlin
- Jetpack Compose
- Room
- Retrofit + OkHttp + Kotlinx Serialization
- Foreground Service
- WindowManager Overlay

## Requirements

- Android Studio Koala 及以上版本
- JDK 17
- Android `minSdk 26`
- Android `targetSdk 35`

## Architecture

```text
app/src/main/java/io/baiyanwu/coinmonitor/
  boot/        开机恢复、升级恢复、自恢复广播
  data/        数据库、网络、仓库实现
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
- 交易所模式覆盖 `Binance Alpha / Binance / OKX`
- 链上模式当前通过 `OKX DEX Market API` 搜索代币，并按单链过滤结果
- 链上搜索支持币名 / Symbol / 合约地址输入
- 搜索结果按来源和链稳定排序，便于快速筛选
- 搜索页当前按入口模式分流：
  - 从首页进入时，结果页继续承担观察列表的 `添加 / 删除` 管理
  - 从 K 线页进入时，结果页隐藏增删按钮，点击单条结果后会回填到 K 线页并立即关闭搜索页

### K-line

- 底部导航新增独立 `K线` tab，K 线页和首页/设置页并列
- 图表内核当前基于仓库内 vendored 的 `TradingView Lightweight Charts Android wrapper` 源码模块
- 第三方图表源码当前直接放在 `third_party/lightweightlibrary`，应用不再依赖外部 `aar`，方便直接调试 wrapper 和内嵌 JS core
- K 线数据统一走 `MarketKlineRepository`，对 `Binance / Binance Alpha / OKX / OKX On-chain` 做统一 candle 映射
- 主图支持 `MA / EMA / BOLL`，副图支持 `VOL / MACD / RSI / KDJ`
- 指标设置使用独立 `Activity`，通过本地偏好持久化完整配置模型
- 当前图表已经消费 `开关 / 参数 / 颜色 / 基础样式`，并按配置重绘，不会因为改指标参数而重新请求行情接口
- 图表实现和页面实现之间通过 `KlineChartContract` 解耦，方便后续继续替换成自研 K 线内核
- 图表颜色当前通过独立 `KlineChartPalette` 管理，不直接复用应用页面主题色；夜间模式下网格、文字和主副图颜色可以独立调整
- 当前实现已经从“双 `ChartsView` 手工同步”切到“单 chart + pane”结构，主图和副图共用同一套十字线、时间轴和缩放逻辑
- wrapper 本地补齐了官方已有但 Android 侧未暴露的 pane 和 logical range 能力，用于把副图指标 series 移入独立 pane 并保持时间轴一致
- vendored wrapper 当前内嵌的 JS core 已切到 `lightweight-charts v5.1.0`
- 当前对价格轴手势只做了一处集中修正：在 vendored JS core 内屏蔽价格轴区域的双指放大异常，保留主绘图区的正常 pinch 缩放
- 当前 K 线页通过在 `NavHost` 级别复用 `KlineChartHostView`，避免底部 tab 切换时整块 chart 被销毁重建
- 夜间模式下的 WebView 首帧白底和 pane 分隔白线，当前收口在 vendored wrapper 的加载页与 JS 初始化层做透明背景修正
- 为了隔离 K 线问题，K 线页外层仍暂时移除了下拉刷新和纵向滚动，避免额外手势干扰；周期切换已经恢复为真实生效
- AI 聊天当前已经切到“会话 + 消息”两层持久化模型，K 线页支持新建会话，并通过独立历史页回看和切换旧会话
- K 线页输入框当前使用自定义紧凑 `BasicTextField` 容器，而不是 `OutlinedTextField`，避免 Material 默认最小高度、内部垂直 padding 和尾部标准按钮把输入区撑高
- AI 历史页当前只展示至少有一条消息的会话；空白新会话不会反复计入历史列表

### On-chain

- 当前链上能力只做搜索和最新价格展示，不提供交易执行
- `OKX` 凭证由用户在设置页本地填写
- 凭证通过本地加密存储管理，并由链上仓库统一读取
- 链上搜索当前面向 `EVM` 与 `Solana`，其中 EVM 再按具体链 `chainIndex` 精确请求
- 链上代币缺少自身图标时，会回退到链图标，并在缓存阶段生成灰阶版本复用

### Overlay

- 支持选择要展示的币对
- 当前最多允许选择 `10` 个悬浮窗币对；超过上限时会在入口页给出提示，限制值统一收口在 `OverlaySettings.MAX_SELECTABLE_ITEMS`
- 支持锁定拖动、透明度调节、最大展示数量限制
- 支持字体大小调节，并同步缩放左侧图标 / 名称区比例
- 左侧展示默认使用图标，也可以切换成币对名称
- 悬浮窗中的币种图标统一按圆形裁剪，和应用内列表的视觉语义保持一致
- 支持吸附靠边，吸附后切换成边栏跑马灯样式
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
- 当前底层默认实现已经切到流式引擎，`Binance Spot / Binance Alpha / OKX Spot / OKX On-chain` 优先走 `WSS`
- 当前实时价格主链路已经改成 `WSS / REST -> InMemory QuoteRepository -> UI`，不再每次报价都直接写回 `watch_items`
- `watch_items` 里的价格字段当前只承担启动恢复和低频快照持久化，默认在页面不再活跃时落一次，并在前台运行期间按低频兜底写回
- `OKX On-chain` 当前按官方最新 `price channel` 文档接入，使用 `wss://wsdex.okx.com/ws/v6/dex`，并在登录成功后再发送价格订阅
- 轮询实现仍然保留在工程中，后续可作为 `仅 API` 模式或故障回退方案继续复用

### Upstream Docs And Endpoints

为方便后续继续接手，这里把当前实际接入的上游文档入口、`base URL` 和主要接口路径集中列出。

- `Binance Spot`
  - 官方文档：`https://developers.binance.com/docs/binance-spot-api-docs/rest-api`、`https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams`
  - REST base URL：`https://api.binance.com/`
  - WSS URL：`wss://stream.binance.com:9443/ws`
  - 当前 REST 路径：`GET /api/v3/exchangeInfo`、`GET /api/v3/ticker/24hr`
  - 当前 WSS 订阅：`${symbol.lowercase()}@ticker`

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

- `OKX On-chain / DEX Market API`
  - 英文文档：`https://web3.okx.com/build/dev-docs/dex-api/dex-api-access-and-usage`
  - 英文 WSS 文档：`https://web3.okx.com/build/dev-docs/dex-api/dex-websocket-introduction`
  - 中文开发者入口：`https://web3.okx.com/zh-hans/onchainos/dev-portal`
  - 英文开发者入口：`https://web3.okx.com/onchainos/dev-portal`
  - REST base URL：`https://web3.okx.com/`
  - WSS URL：`wss://wsdex.okx.com/ws/v6/dex`
  - 当前 REST 路径：`GET /api/v6/dex/market/supported/chain`、`GET /api/v6/dex/market/token/search`、`POST /api/v6/dex/market/price`
  - 当前 WSS 登录 path：`/users/self/verify`
  - 当前 WSS 订阅频道：`channel=price`

- 代码对齐位置
  - REST base URL 定义：`app/src/main/java/io/baiyanwu/coinmonitor/data/network/NetworkFactory.kt`
  - REST 路径定义：`app/src/main/java/io/baiyanwu/coinmonitor/data/network/NetworkModels.kt`
  - WSS URL 与订阅实现：`app/src/main/java/io/baiyanwu/coinmonitor/data/refresh/StreamingQuoteRefreshEngine.kt`

## TODO

- 增加“行情刷新方式”设置项，允许用户在 `智能 / 仅 WSS / 仅 API` 三种模式之间切换
- `智能` 模式优先走交易所与链上的 `WSS`，连接异常或当前市场不支持时自动回退到 `API`
- `仅 API` 模式继续复用现有轮询引擎和刷新间隔配置，作为弱网、代理环境和问题排查时的稳定兜底
- 给 `REST` 快照刷新和 `WSS` 推送补统一时序保护，避免手动下拉刷新时旧快照短暂覆盖更晚到达的实时价格
- 可选实现方向包括“按报价时间戳丢弃旧写入”或“把 `REST / WSS` 更新统一串行到同一条写库通道”
- 精简通知栏文案，去掉“每 3 秒刷新一次”这类频率提示，避免在 `WSS` 模式下继续显示过时的轮询描述
- 补齐 `AI 分析` 能力
  - 当前已支持 OpenAI 兼容接口的流式响应、K 线上下文拼装、会话态管理和失败兜底
  - 用户可在设置页自行配置 Base URL、API Key、Model 和 System Prompt

## Implementation Notes

- 首页长按快捷菜单挂在同一棵 Compose 树里渲染，不走独立 `PopupWindow`；菜单会先测量真实宽度，再按手指落点附近定位，并补一段轻量的入场动画。
- 首页列表在 ViewModel 首次收到本地数据前会先展示加载态，避免把默认空列表误判为空页面。
- 首页 `CoinSymbolIcon` 当前会先同步读取本地 / 内存图标缓存，再异步补齐，避免列表滚动时反复闪回占位图。
- 首页实时价格读取已经下沉到单行价格子树；每个 item 只订阅自己的 quote flow，避免任意一个币价变化时唤醒整屏可见项。
- 首页列表项手势当前统一收口在自定义 `awaitEachGesture` 流程里：点击、拖动和长按菜单共用一套状态机，避免多套手势监听互相抢占。
- 首页拖动入口已经改成整卡长按，不再依赖右侧专门的拖动把手；当前交互时序固定为 `400ms` 进入拖动、`650ms` 直接弹出快捷菜单。
- 搜索页和悬浮窗设置页使用独立 `Activity`，避免和主 `NavHost` 的底部导航、转场动画、窗口 inset 相互耦合。
- 首页刷新已经切换成 `PullToRefreshBox`，并在 ViewModel 里补了手动刷新态，避免手势刷新和后台轮询互相打架。
- 设置页里涉及 `Switch` 的横向行当前都支持整行点击，不再只靠右侧小开关命中。
- 悬浮窗仍然使用 `WindowManager + View`，没有改成 Compose，以降低系统悬浮场景下的重排、生命周期和兼容性风险。
- 悬浮窗当前把“临时隐藏”单独建模为运行态，不落库；隐藏时会立即 `removeViewImmediate`，保证原位置点击可以穿透到底层应用。
- 标准悬浮窗行视图会复用已有 `ImageView` / `TextView`，避免高频价格刷新时反复重建 leading 区域导致图标闪动。
- 吸附侧边栏 ticker 当前额外做了图标 bitmap 复用和宽度按内容自适应，避免图标闪烁和右侧留白过宽。
- 悬浮窗图标在 `WindowManager` 视图层单独做圆形裁剪，保留外层定宽布局，避免改成圆形后把价格列对齐打乱。
- 前台通知使用自定义 `RemoteViews` 内容布局，统一正文与操作按钮的对齐方式。
- 数据库已移除默认破坏性迁移，并开启 Room schema 导出，为后续显式 migration 留出接口。
- 当前 Room schema 已升级到 `v5`，用于承载悬浮窗字体大小和吸附靠边配置。
- 当前 Room schema 已升级到 `v6`，用于承载链上观察项所需的链信息与图标字段。
- 当前 Room schema 已升级到 `v7`，用于承载首页排序与置顶字段：`homePinned`、`homeOrder`、`homePinnedOrder`。
- 调试网络日志只在 Debug 构建输出，Release 默认关闭。
- AI 聊天当前已经复用同一套带网络日志拦截器的 `OkHttpClient`，因此 `K线 AI` 请求也会进入网络日志页。
- HTTP 网络日志当前会记录请求头与请求体预览；其中 `Authorization` 会做脱敏，响应体仍不主动展开，避免影响流式 AI 返回。
- 悬浮窗启停规则已经统一，避免 UI 开关状态和真实运行状态不一致。
- 搜索页的交易所模式和链上模式已经彻底分流，链上模式不会再混发交易所搜索请求。
- 首页列表和搜索结果页当前共用同一套交易所 badge 视觉：`Binance / Binance Alpha / OKX` 都按统一的强调色标签渲染，避免跨页面样式漂移。
- 行情刷新已经拆成“全局协调器 + 可替换刷新引擎”两层结构，为交易所与链上的 `WSS` 接入预留接口。
- 流式引擎内部已经对订阅集合做指纹比较，避免价格回流导致重复重建长连接。
- 当前 vendored chart wrapper 关闭了 `WebView` 自身页面缩放，避免系统层缩放和图表手势混在一起。
- vendored wrapper 生成产物当前直接提交 `src/main/assets/com/tradingview/lightweightcharts/scripts/app/main.js`，这样工程在不执行 `npm run compile` 时也能直接构建运行。

## Open Source Notes

仓库公开时建议保持以下约束：

- 不要提交 `local.properties`
- 不要提交签名文件、私钥、发行版 keystore
- 不要把任何私有 API Key、鉴权头或内部环境地址写入仓库
- 交易所现货数据仍然使用公开接口
- 链上搜索与报价依赖用户自行填写的 `OKX` 凭证，项目本身不托管任何密钥
- 不要把测试用 `OKX` 密钥、鉴权头或抓包结果提交进仓库

## Release Signing

本地签名和 CI 签名都已经预留好接入点：

- 本地开发默认从 `local.properties` 读取 release 签名参数
- GitHub Actions 默认从 secrets 注入 release 签名参数

本地参数键如下：

- `release.storeFile`
- `release.storePassword`
- `release.keyAlias`
- `release.keyPassword`

GitHub Actions secrets 约定如下：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

其中 `ANDROID_KEYSTORE_BASE64` 需要由 keystore 文件 base64 编码后写入 GitHub Secrets。

## Release Notes

- 当前 GitHub Release 采用 `releaseX.Y.Z` 的 tag / title 命名
- Android 应用内版本号使用 `versionName` / `versionCode` 单独维护
- 发布新版时建议同步更新：
  - `app/build.gradle.kts` 中的 `versionName`
  - `app/build.gradle.kts` 中的 `versionCode`
  - `README.md` / `README.zh-CN.md` 的更新说明
