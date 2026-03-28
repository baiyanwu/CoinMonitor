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
- 长按单个币对会弹出跟手快捷菜单，支持删除，以及加入或移出悬浮窗

### Search

- 支持关键字搜索
- 交易所模式覆盖 `Binance Alpha / Binance / OKX`
- 链上模式当前通过 `OKX DEX Market API` 搜索代币，并按单链过滤结果
- 链上搜索支持币名 / Symbol / 合约地址输入
- 搜索结果按来源和链稳定排序，便于快速筛选

### On-chain

- 当前链上能力只做搜索和最新价格展示，不提供交易执行
- `OKX` 凭证由用户在设置页本地填写
- 凭证通过本地加密存储管理，并由链上仓库统一读取
- 链上搜索当前面向 `EVM` 与 `Solana`，其中 EVM 再按具体链 `chainIndex` 精确请求
- 链上代币缺少自身图标时，会回退到链图标，并在缓存阶段生成灰阶版本复用

### Overlay

- 支持选择要展示的币对
- 支持锁定拖动、透明度调节、最大展示数量限制
- 支持字体大小调节，并同步缩放左侧图标 / 名称区比例
- 左侧展示默认使用图标，也可以切换成币对名称
- 支持吸附靠边，吸附后切换成边栏跑马灯样式
- 通知栏支持临时隐藏 / 恢复显示，以及拖动开关
- 选择数量超过 `5` 个时按每批 `5` 个轮播
- 只有在悬浮窗权限满足时，应用才会把悬浮窗正式标记为启用
- 悬浮窗设置页中的币对选择列表会带上交易所来源副标题，避免同名币对辨识成本过高

### Refresh Strategy

- 全局刷新间隔统一配置
- 当前支持：
  - 自定义 `3-10 秒`
  - `30 秒`
  - `1 分钟`
- 首页和悬浮窗只保留一套全局刷新协调器
- 当前底层默认实现仍是轮询，但已经抽出可替换的刷新引擎接口，后续切 `WSS` 时可以直接替换实现

## Implementation Notes

- 首页长按快捷菜单挂在同一棵 Compose 树里渲染，不走独立 `PopupWindow`；菜单会先测量真实宽度，再按手指落点附近定位，并补一段轻量的入场动画。
- 首页列表在 ViewModel 首次收到本地数据前会先展示加载态，避免把默认空列表误判为空页面。
- 搜索页和悬浮窗设置页使用独立 `Activity`，避免和主 `NavHost` 的底部导航、转场动画、窗口 inset 相互耦合。
- 首页刷新已经切换成 `PullToRefreshBox`，并在 ViewModel 里补了手动刷新态，避免手势刷新和后台轮询互相打架。
- 悬浮窗仍然使用 `WindowManager + View`，没有改成 Compose，以降低系统悬浮场景下的重排、生命周期和兼容性风险。
- 悬浮窗当前把“临时隐藏”单独建模为运行态，不落库；隐藏时会立即 `removeViewImmediate`，保证原位置点击可以穿透到底层应用。
- 标准悬浮窗行视图会复用已有 `ImageView` / `TextView`，避免高频价格刷新时反复重建 leading 区域导致图标闪动。
- 前台通知使用自定义 `RemoteViews` 内容布局，统一正文与操作按钮的对齐方式。
- 数据库已移除默认破坏性迁移，并开启 Room schema 导出，为后续显式 migration 留出接口。
- 当前 Room schema 已升级到 `v5`，用于承载悬浮窗字体大小和吸附靠边配置。
- 当前 Room schema 已升级到 `v6`，用于承载链上观察项所需的链信息与图标字段。
- 调试网络日志只在 Debug 构建输出，Release 默认关闭。
- 悬浮窗启停规则已经统一，避免 UI 开关状态和真实运行状态不一致。
- 搜索页的交易所模式和链上模式已经彻底分流，链上模式不会再混发交易所搜索请求。
- 行情刷新已经拆成“全局协调器 + 可替换刷新引擎”两层结构，为交易所与链上的 `WSS` 接入预留接口。

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
