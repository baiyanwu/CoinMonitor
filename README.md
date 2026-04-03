<div align="center">
  <img src="./artwork/coinmonitor_app_icon.svg" alt="CoinMonitor app icon" width="96" height="96" />
  <h1>CoinMonitor</h1>
  <p>A lightweight Android crypto price monitor built around a watchlist and system overlay workflow.</p>
</div>

<p align="center">
  <a href="./README.zh-CN.md">简体中文</a>
  ·
  <a href="./TECHNICAL.md">Technical Notes</a>
  ·
  <a href="./LICENSE">Apache-2.0</a>
</p>

`CoinMonitor` is an Android app focused on one simple path: search assets, add them to a watchlist, and optionally pin selected items into a floating overlay for quick monitoring across apps.

It supports spot pair search from `Binance Alpha`, `Binance`, and `OKX`, and also supports on-chain token search and price tracking through `OKX DEX Market API`. Once an item is added, you can track it inside the app or send it to the overlay, where a foreground service keeps prices refreshed in the background.

This project is currently designed and iterated primarily through `vibecoding`, while still being kept in a conventional Android project structure so it stays runnable, readable, and maintainable.

<div align="center">
  <img src="./artwork/screenshot.png" alt="CoinMonitor preview" width="960" />
</div>

---

## Core Features

- Search and track spot pairs from `Binance Alpha`, `Binance`, and `OKX`, plus on-chain tokens through `OKX DEX Market API`
- Manage a watchlist with quick actions, live quote refresh, and stable icon/badge presentation across the app
- Pin selected items into a floating overlay with drag lock, edge snapping, adaptive layouts, and foreground-service persistence
- Browse a dedicated `Kline` tab with multi-source switching, interval selection, and configurable `MA / EMA / BOLL / VOL / MACD / RSI / KDJ`
- Built-in AI analysis on the Kline page, powered by user-configured OpenAI-compatible endpoints with streaming responses and chat history
- Keep quotes flowing through `WSS` first with snapshot fallback, while local settings control refresh behavior and on-chain credentials

## On-chain Notes

- On-chain support is intentionally limited to `search + latest price`; the app does not provide swap, order, or execution capabilities.
- `OKX` credentials are entered by the user inside app settings and stored only on the local device.
- Current architecture already routes home-screen refresh and overlay refresh through one global coordinator, making a later move from polling to `WSS` easier.

## Requirements

- Android Studio Koala or newer
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

## Documentation

- Technical implementation: [TECHNICAL.md](./TECHNICAL.md)
- Chinese README: [README.zh-CN.md](./README.zh-CN.md)
- Contributing guide: [CONTRIBUTING.md](./CONTRIBUTING.md)

## Disclaimer

- This project is for technical exploration and personal learning only and does not constitute investment advice.
- `Binance`, `OKX`, and other platform names or APIs belong to their respective owners.
- The app does not provide trading execution. It only displays reference prices, and crypto assets are highly volatile.

## License

This project is licensed under [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0). See [LICENSE](./LICENSE) for details.
