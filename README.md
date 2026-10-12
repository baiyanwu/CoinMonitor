<div align="center">
  <img src="./artwork/coinmonitor_app_icon.svg" alt="CoinMonitor" width="96" height="96" />
  <h1>CoinMonitor</h1>
  <p>An Android crypto monitor with watchlists, floating prices, and a multichain wallet.</p>
  <p><a href="./README.zh-CN.md">简体中文</a> · <a href="https://github.com/baiyanwu/CoinMonitor/releases">Download APK</a> · <a href="https://github.com/baiyanwu/CoinMonitor/issues">Report an issue</a></p>
</div>

## Screenshots

<table>
  <tr>
    <th>Markets & floating prices</th>
    <th>Trending tokens</th>
    <th>Settings</th>
  </tr>
  <tr>
    <td><img src="./artwork/screenshots/markets.png" alt="Markets & floating prices" width="240" /></td>
    <td><img src="./artwork/screenshots/trending.png" alt="Trending tokens" width="240" /></td>
    <td><img src="./artwork/screenshots/settings.png" alt="Settings" width="240" /></td>
  </tr>
</table>

Captured on a Seeker device running version 1.0.14.

## Features

- **Watchlists**: Track Binance, OKX, and on-chain tokens in one place, with search, sorting, pinning, and contract-address copying.
- **Floating prices**: Monitor prices above other apps with a panel or scrolling ticker. Customize opacity, font size, and position.
- **Trending tokens**: Browse GMGN, PumpFun, and OKX rankings, including tokens trending across multiple sources.
- **Quick lookup**: Look up contract addresses from the clipboard, open market websites, and add address tracking.
- **Multichain wallet**: Manage a local self-custody EVM and Solana wallet, view assets, receive and send funds, browse DApps, or observe other addresses in read-only mode.
- **Personalization**: Light and dark themes, English and Chinese, configurable data sources and refresh settings.

Some data sources and wallet queries require your own credentials in Third-party API settings. Floating prices require Android's display-over-other-apps permission.

## Build

Use JDK 17 and Android SDK 35, or open the project in Android Studio. The app supports Android 8.0 and later.

```bash
git clone https://github.com/baiyanwu/CoinMonitor.git
cd CoinMonitor
```

Wallet Core is downloaded from GitHub Packages. Add a GitHub classic PAT with `read:packages` permission to your local `~/.gradle/gradle.properties` (do not commit credentials):

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_READ_PACKAGES_TOKEN
```

```bash
./gradlew :app:assembleDebug
# Optional: run unit tests and lint
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The APK is generated in `app/build/outputs/apk/debug/`. See [release instructions](./docs/RELEASE.md) for release signing and publishing.

## Documentation

[Technical notes](./docs/TECHNICAL.md) · [Contributing](./docs/CONTRIBUTING.md) · [Changelog](./docs/CHANGELOG.md)

## License

Licensed under [Apache-2.0](./LICENSE). See [third-party notices](./docs/THIRD_PARTY_NOTICES.md) for dependencies. Market data is provided for reference and is not investment advice.
