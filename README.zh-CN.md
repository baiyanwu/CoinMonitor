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
- 设置页顶部提供持久化的“显示链上市值”通用开关，可让首页、排列型悬浮窗和跑马灯悬浮窗中的全部链上币统一在价格与市值之间切换；交易所行情不受影响
- 首页观察列表拆分为可点击、可左右滑动的“交易所 / 链上”分页；分页内独立排序，采用更紧凑的行情行，并让搜索默认进入当前市场模式
- 首页币对名称可直接打开对应外部行情页；链上币会按当前选中池显示 `目标币 / 另一侧币种`，并提供合约地址缩写与一键复制完整 CA，切池或后续报价刷新时同步更新
- 系统悬浮窗支持“排列型 / 跑马灯型”两种样式：排列型延续原有多行面板，跑马灯型使用无描边的全屏宽单行细条，紧凑滚动币种图标与最新价，并以小圆点标记每轮结束
- 悬浮窗设置页可切换类型；独立的“悬浮币对”页面负责选择与跨交易所/链上拖动排序，两种样式分别保存透明度、字体、展示数量、位置与专属行为
- 排列型继续支持拖动、自适应布局与左右吸附，可保留靠边价格面板或彻底收成屏幕边缘唤出条；两种样式都由前台服务维持，并可从通知栏隐藏或恢复
- 设置中的“关于”页集中展示当前构建版本、项目用途、作者、源码仓库、Apache-2.0 许可、问题反馈入口与使用说明
- 每次主界面创建时通过 GitHub 官方接口检查一次最新已发布 Release；发现更高版本后提示用户打开 Release 页面，不接入第三方更新服务
- 交易所行情优先使用 `WSS`，链上价格使用独立的智能或固定轮转周期，并顺序分散 DexScreener 请求
- 首页“链上”分页提供独立“观察地址”页面，可只读查询 EVM 或 Solana 地址的全部非零代币和美元估值；数据来自 OKX Wallet API，凭证保存在 Android 加密存储中

## 链上说明

- 链上搜索、最新价格、24 小时涨跌、流动性与成交量由 `DexScreener` 提供，K 线由 `GeckoTerminal` 提供。
- 链上结果按“链 + 代币合约”去重，每个代币只展示一条结果；结果会明确显示当前交易对、链 Logo、DEX、流动性和合约缩写。
- 同一代币存在多个有效池时，可以在结果内展开并切换高流动性备选池；所选池会同时用于后续价格刷新与 K 线。
- 首页沿用所选池的真实交易对；历史观察项缺少另一侧币种时，会在下一次有效报价刷新后自动补齐，无需删除重加。
- 市值使用所选 DexScreener 池返回的目标代币市值；仅在目标币位于池子的 base 侧时采用该值，不以 FDV 代替，缺少数据时显示 `--`。
- 切换到市值后仍按真实价格方向触发短暂红绿闪烁；价格未变化的重复刷新不会闪烁，闪烁结束后恢复中性色。
- 图标优先使用 DexScreener 代币图片，再按“已知链图标 → 在线链图标候选 → 应用内置默认占位图”依次回退；已缓存的链图标不会跳过更高优先级的代币图片请求。
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
- 观察地址说明：[WALLET_WATCH.md](./docs/WALLET_WATCH.md)
- 英文 README：[README.md](./README.md)
- 贡献说明：[CONTRIBUTING.md](./docs/CONTRIBUTING.md)

## Disclaimer

- 本项目仅用于技术交流与个人学习，不构成任何投资建议
- `Binance`、`OKX` 等名称和接口归各自平台所有
- 本项目不提供任何交易接口，仅提供价格参考，加密资产价格波动较大，请谨慎使用

## License

本项目采用 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证，详情见 [LICENSE](./LICENSE)。
