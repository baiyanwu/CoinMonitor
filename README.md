# CoinMonitor

`CoinMonitor` 是一个基于 Android 的轻量级盯盘应用，聚焦“观察列表 + 悬浮窗盯盘”这条核心路径。

它支持从 `Binance Alpha`、`Binance`、`OKX` 搜索现货交易对，加入观察列表后可以在应用内查看，也可以选择加入系统悬浮窗，配合前台服务持续刷新价格。

## Highlights

- Jetpack Compose 构建主界面，搜索页和悬浮窗设置页使用独立 `Activity` 承接
- 支持 `Binance Alpha`、`Binance`、`OKX` 三个数据来源
- 支持观察列表、手动刷新、长按加入悬浮窗
- 悬浮窗支持启用、锁定拖动、透明度、最大展示数量、图标/币对名切换
- 全局刷新间隔支持自定义 `3-10 秒`、`30 秒`、`1 分钟`
- 悬浮窗通过前台服务维持运行，并在符合条件时尝试自恢复

## Project Status

项目仍处于早期阶段，当前更偏向可用的 MVP / Beta 版本。

现阶段已经完成的工程收口：

- 去除了默认破坏性数据库迁移策略，为后续正式 migration 留出空间
- Room schema 已导出，便于后续版本演进
- 悬浮窗启停规则已经统一，避免 UI 开关状态和真实运行状态不一致
- 调试网络日志仅在 Debug 构建输出，Release 默认关闭

## Screenshots / Assets

当前仓库已附带应用图标素材：

- [coinmonitor_app_icon.svg](./artwork/coinmonitor_app_icon.svg)

如果你准备正式开源，建议后续再补 2-4 张截图：

- 首页观察列表
- 搜索页
- 悬浮窗设置页
- 实际悬浮窗效果

这会显著提升 GitHub 首页的可读性和项目完成度。

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

## Quick Start

### 1. Clone

```bash
git clone <your-repo-url>
cd CoinMonitor
```

### 2. Build Debug APK

```bash
./gradlew :app:assembleDebug
```

### 3. Run Tests

```bash
./gradlew testDebugUnitTest :app:lintDebug
```

### 4. Install on Device

```bash
./gradlew :app:installDebug
```

## Features

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

## Architecture

```text
app/src/main/java/io/baiyanwu/coinmonitor/
  boot/        开机恢复、升级恢复、自恢复广播
  data/        数据库、网络、仓库实现
  domain/      核心模型和仓库接口
  overlay/     悬浮窗控制器、前台服务、轮询协调器
  ui/          Compose 页面、主题、Activity 宿主
```

整体设计偏向简单直接的单模块结构，优先保证可维护性和演进空间，而不是过早做复杂模块拆分。

## Open Source Notes

当前仓库适合公开，但在真正推到 GitHub 前后，建议保持下面这些约束：

- 不要提交 `local.properties`
- 不要提交签名文件、私钥、发行版 keystore
- 不要把任何私有 API Key、鉴权头或内部环境地址写入仓库
- 当前数据源均为公开接口，不依赖额外密钥
- 若后续接入第三方密钥，请统一改为 `local.properties` / CI secrets 注入

## Roadmap

- 补充 GitHub 首页截图
- 增加更多单元测试和 UI 测试
- 评估是否接入正式的版本发布流程
- 后续版本数据库演进时补充显式 migration
- 根据开源反馈继续优化多语言、可访问性和异常处理

## Contributing

欢迎提交 Issue 和 Pull Request。

在发起 PR 前，请至少确认：

```bash
./gradlew testDebugUnitTest :app:assembleDebug :app:lintDebug
```

更多说明请查看 [CONTRIBUTING.md](./CONTRIBUTING.md)。

## Disclaimer

- 本项目仅用于技术交流与个人学习，不构成任何投资建议
- `Binance`、`OKX` 等名称和接口归各自平台所有
- 加密资产价格波动较大，请谨慎使用

## License

本项目采用 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证，详情见 [LICENSE](./LICENSE)。
