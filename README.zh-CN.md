<div align="center">
  <img src="./artwork/coinmonitor_app_icon.svg" alt="CoinMonitor app icon" width="96" height="96" />
  <h1>CoinMonitor</h1>
  <p>一个专注于观察列表与悬浮窗盯盘体验的 Android 币价监控应用。</p>
</div>

<p align="center">
  <a href="./README.md">English</a>
  ·
  <a href="./docs/TECHNICAL.md">Technical Notes</a>
  ·
  <a href="./LICENSE">Apache-2.0</a>
</p>

`CoinMonitor` 是一个基于 Android 的轻量级盯盘应用，聚焦”观察列表 + 悬浮窗盯盘”这条核心路径，主要通过 vibecoding 的方式迭代推进。

<div align="center">
  <img src="./artwork/screenshot.png" alt="CoinMonitor homepage preview" width="960" />
</div>

---

## 核心功能

- 交易所搜索并行覆盖 `Binance Alpha`、`Binance` 现货与 USDT 合约、`OKX` 现货与 USDT 合约，等待全部来源结束后统一合并、排序并一次性展示
- 链上搜索无需选链：输入币名、Symbol 或合约地址后，接受 `DexScreener` 返回的全部网络，不再使用本地链白名单过滤
- 提供观察列表、快捷操作和稳定的实时行情刷新，并统一了列表与搜索结果中的图标和来源标签体验
- 支持系统悬浮窗盯盘，包含锁定拖动、自适应布局、通知栏隐藏恢复与前台服务保活；吸附靠边可选择继续显示跑马灯，或彻底收成屏幕边缘唤出条
- 悬浮窗设置页支持权限、主体透明度、字体大小、展示币种、吸附条透明度与 `1–5 秒` 自动收回时间配置，并采用固定顶部栏布局
- 交易所行情优先使用 `WSS`，链上价格使用独立的智能或固定轮转周期，并顺序分散 DexScreener 请求

## 链上说明

- 链上搜索、最新价格、24 小时涨跌、流动性与成交量由 `DexScreener` 提供，K 线由 `GeckoTerminal` 提供。
- 链上结果按“链 + 代币合约”去重，每个代币只展示一条结果；结果会明确显示当前交易对、链 Logo、DEX、流动性和合约缩写。
- 同一代币存在多个有效池时，可以在结果内展开并切换高流动性备选池；所选池会同时用于后续价格刷新与 K 线。
- 图标按“已知本地映射 → 在线链图标候选 → 应用内置默认占位图”依次回退。
- 两个链上来源都无需 API Key；应用不提供交易、下单或路由执行能力。

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

- 技术实现说明：[TECHNICAL.md](./docs/TECHNICAL.md)
- 英文 README：[README.md](./README.md)
- 贡献说明：[CONTRIBUTING.md](./docs/CONTRIBUTING.md)

## Disclaimer

- 本项目仅用于技术交流与个人学习，不构成任何投资建议
- `Binance`、`OKX` 等名称和接口归各自平台所有
- 本项目不提供任何交易接口，仅提供价格参考，加密资产价格波动较大，请谨慎使用

## License

本项目采用 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证，详情见 [LICENSE](./LICENSE)。
