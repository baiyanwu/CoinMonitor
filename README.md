<div align="center">
  <img src="./artwork/coinmonitor_app_icon.svg" alt="CoinMonitor app icon" width="96" height="96" />
  <h1>CoinMonitor</h1>
  <p>A lightweight Android crypto price monitor built around a watchlist and system overlay workflow.</p>
</div>

<p align="center">
  <a href="./README.zh-CN.md">简体中文</a>
  ·
  <a href="./docs/TECHNICAL.md">Technical Notes</a>
  ·
  <a href="./LICENSE">Apache-2.0</a>
</p>

`CoinMonitor` is an Android app focused on one simple path: search assets, add them to a watchlist, and optionally pin selected items into a floating overlay for quick monitoring across apps. The project is built and iterated primarily through vibecoding.

<div align="center">
  <img src="./artwork/screenshot.png" alt="CoinMonitor preview" width="960" />
</div>

---

## Core Features

- Search `Binance Alpha`, Binance spot and USDT-M futures, plus OKX spot and USDT swaps in parallel, then merge, sort, and display one result set after all sources finish
- Search on-chain tokens by name, symbol, or contract address without selecting a chain; every network returned by `DexScreener` is accepted without a local allowlist
- Manage a watchlist with quick actions, live quote refresh, and stable icon/badge presentation across the app
- Pin selected items into a floating overlay with drag lock, adaptive layouts, and foreground-service persistence; edge docking can keep the ticker visible or collapse it into a slim edge tab
- Tune overlay behavior with a fixed-header settings flow for permissions, body opacity, font size, selected symbols, edge-tab opacity, and a configurable `1–5 second` auto-collapse delay
- Keep exchange quotes flowing through `WSS` first, while DexScreener prices use an independent smart or fixed refresh cycle with sequential request pacing

## On-chain Notes

- On-chain search, latest price, 24h change, liquidity and volume come from `DexScreener`; candlesticks come from `GeckoTerminal`.
- Results are deduplicated by network and token contract. Each token row shows the selected pair, chain logo, DEX, liquidity, and shortened contract address.
- When multiple valid pools exist, users can expand the row and switch among the most liquid alternatives. The selected pool is then reused for both quote refreshes and candlesticks.
- Icons fall back from known local mappings to online chain-icon candidates and finally to the app's built-in placeholder.
- Both on-chain sources are public and require no API key. The app does not provide swap, order, or execution capabilities.

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

- Chinese README: [README.zh-CN.md](./README.zh-CN.md)
- Technical implementation: [TECHNICAL.md](./docs/TECHNICAL.md)
- Contributing guide: [CONTRIBUTING.md](./docs/CONTRIBUTING.md)

## Disclaimer

- This project is for technical exploration and personal learning only and does not constitute investment advice.
- `Binance`, `OKX`, and other platform names or APIs belong to their respective owners.
- The app does not provide trading execution. It only displays reference prices, and crypto assets are highly volatile.

## License

This project is licensed under [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0). See [LICENSE](./LICENSE) for details.
