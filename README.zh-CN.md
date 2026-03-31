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

## 功能

- 支持 `Binance Alpha`、`Binance`、`OKX` 三个交易所数据来源
- 支持链上代币搜索与价格跟踪，当前聚焦 `EVM` 与 `Solana`
- 支持用户在设置页本地填写 `OKX` 凭证，用于链上搜索与报价，不依赖自建服务端
- 支持独立 `K线` 页，覆盖 `Binance / Alpha / OKX / On-chain` 多来源 K 线切换
- K 线页支持周期切换、主图/副图指标切换，以及独立的指标参数配置页
- K 线指标设置已支持 `MA / EMA / BOLL / VOL / MACD / RSI / KDJ` 的开关、参数、颜色与样式配置
- 支持观察列表、下拉刷新、长按快捷操作悬浮窗
- 悬浮窗支持启用、锁定拖动、透明度、最大展示数量、图标/币对名切换
- 悬浮窗支持通知栏临时隐藏 / 恢复显示，并且隐藏后立即释放点击区域
- 悬浮窗支持字体大小调节、吸附靠边，以及边栏跑马灯展示
- 首页与悬浮窗共用同一条全局刷新链路，刷新间隔支持自定义 `3-10 秒`、`30 秒`、`1 分钟`
- 悬浮窗通过前台服务维持运行，并在符合条件时尝试自恢复
- 链上代币缺少图标时，会自动回退到对应链图标，并在缓存阶段完成灰阶化处理

## 1.0.2 更新

- 主行情链路切到交易所与链上的 `WSS`，覆盖 `Binance Spot`、`Binance Alpha`、`OKX Spot`、`OKX On-chain`
- 增加 `REST` 快照兜底和订阅指纹比较，避免价格回流时反复重建长连接
- 搜索页加载态改成直接占用搜索框左侧图标位，不再单独在下方显示菊花
- 悬浮窗里的币种图标统一改成圆形裁剪，和应用内列表保持一致

## 1.0.3 开发中

- 新增底部 `K线` tab，并接入 `TradingView Lightweight Charts`
- 新增独立指标设置页，支持主图/副图指标分组配置
- 新增 K 线网络日志联调链路，便于排查不同来源的请求与渲染问题

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

## Roadmap

- 增加用户可选的 `智能 / 仅 WSS / 仅 API` 刷新方式
- 继续打磨 K 线样式与交互细节
- AI 分析能力接入
  - 当前仅保留 `AI 分析` 入口和本地配置结构
  - 实际 AI 对话与分析逻辑尚未完成，后续补齐请求链路、上下文拼装与异常处理
- 继续打磨观察列表与悬浮窗体验

## Disclaimer

- 本项目仅用于技术交流与个人学习，不构成任何投资建议
- `Binance`、`OKX` 等名称和接口归各自平台所有
- 本项目不提供任何交易接口，仅提供价格参考，加密资产价格波动较大，请谨慎使用

## License

本项目采用 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证，详情见 [LICENSE](./LICENSE)。
