<div align="center">
  <img src="./artwork/coinmonitor_app_icon.svg" alt="CoinMonitor app icon" width="96" height="96" />
  <h1>CoinMonitor</h1>
  <p>一个专注于观察列表与悬浮窗盯盘体验的 Android 币价监控应用。</p>
</div>

<p align="center">
  <a href="./README.md">English</a>
  ·
  <a href="./TECHNICAL.md">Technical Notes</a>
  ·
  <a href="./LICENSE">Apache-2.0</a>
</p>

`CoinMonitor` 是一个基于 Android 的轻量级盯盘应用，聚焦“观察列表 + 悬浮窗盯盘”这条核心路径。

它支持从 `Binance Alpha`、`Binance`、`OKX` 搜索现货交易对，也支持通过 `OKX DEX Market API` 搜索链上代币并获取最新价格。加入观察列表后可以在应用内查看，也可以选择加入系统悬浮窗，配合前台服务持续刷新价格。

项目当前主要通过 `vibecoding` 的方式完成设计、实现和迭代，在保证可运行与可维护的前提下快速推进功能落地。

<div align="center">
  <img src="./artwork/screenshot.png" alt="CoinMonitor homepage preview" width="960" />
</div>

---

## 核心功能

- 支持 `Binance Alpha`、`Binance`、`OKX` 现货交易对，以及基于 `OKX DEX Market API` 的链上代币搜索与价格跟踪
- 提供观察列表、快捷操作和稳定的实时行情刷新，并统一了列表与搜索结果中的图标和来源标签体验
- 支持系统悬浮窗盯盘，包含锁定拖动、吸附靠边、自适应布局、通知栏隐藏恢复与前台服务保活
- 提供独立 `K线` 页，支持多来源切换、周期切换，以及 `MA / EMA / BOLL / VOL / MACD / RSI / KDJ` 指标配置
- K线页内置 AI 分析功能，支持用户自行配置 OpenAI 兼容接口，提供流式响应与会话历史
- 行情链路优先使用 `WSS`，并保留快照兜底，同时支持本地配置链上凭证与刷新方式

## 链上说明

- 当前链上能力只做“搜索 + 最新价格”，不提供交易、下单或路由执行能力。
- `OKX` 凭证由用户自行填写，只保存在本地设备，不会上传到项目服务端。
- 首页和悬浮窗已经统一到一个全局刷新协调器，后续切换到 `WSS` 时不需要再维护两套刷新逻辑。

## Requirements

- Android Studio Koala 及以上版本
- JDK 17
- Android `minSdk 26`
- Android `targetSdk 35`

## Quick Start

```bash
git clone https://github.com/baiyanwu/CoinMonitor.git
cd CoinMonitor
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest :app:lintDebug
./gradlew :app:installDebug
```

## 文档

- 技术实现说明：[TECHNICAL.md](./TECHNICAL.md)
- 英文 README：[README.md](./README.md)
- 贡献说明：[CONTRIBUTING.md](./CONTRIBUTING.md)

## Disclaimer

- 本项目仅用于技术交流与个人学习，不构成任何投资建议
- `Binance`、`OKX` 等名称和接口归各自平台所有
- 本项目不提供任何交易接口，仅提供价格参考，加密资产价格波动较大，请谨慎使用

## License

本项目采用 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证，详情见 [LICENSE](./LICENSE)。
