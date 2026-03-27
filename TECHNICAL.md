# Technical Notes

这个文档用于集中说明 `CoinMonitor` 的技术实现细节、工程结构和当前设计取舍。

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
- 长按单个币对可快速加入或移出悬浮窗

### Search

- 支持关键字搜索
- 搜索结果覆盖 `Binance Alpha / Binance / OKX`
- 结果按来源稳定排序，便于快速筛选

### Overlay

- 支持选择要展示的币对
- 支持锁定拖动、透明度调节、最大展示数量限制
- 选择数量超过 `5` 个时按每批 `5` 个轮播
- 只有在悬浮窗权限满足时，应用才会把悬浮窗正式标记为启用

### Refresh Strategy

- 全局刷新间隔统一配置
- 当前支持：
  - 自定义 `3-10 秒`
  - `30 秒`
  - `1 分钟`
- 首页轮询和悬浮窗轮询使用同一套全局刷新策略

## Implementation Notes

- 搜索页和悬浮窗设置页使用独立 `Activity`，避免和主 `NavHost` 的底部导航、转场动画、窗口 inset 相互耦合。
- 悬浮窗仍然使用 `WindowManager + View`，没有改成 Compose，以降低系统悬浮场景下的重排、生命周期和兼容性风险。
- 数据库已移除默认破坏性迁移，并开启 Room schema 导出，为后续显式 migration 留出接口。
- 调试网络日志只在 Debug 构建输出，Release 默认关闭。
- 悬浮窗启停规则已经统一，避免 UI 开关状态和真实运行状态不一致。

## Open Source Notes

仓库公开时建议保持以下约束：

- 不要提交 `local.properties`
- 不要提交签名文件、私钥、发行版 keystore
- 不要把任何私有 API Key、鉴权头或内部环境地址写入仓库
- 当前数据源均为公开接口，不依赖额外密钥
- 若后续接入第三方密钥，请统一改为 `local.properties` / CI secrets 注入

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
