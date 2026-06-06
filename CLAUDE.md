# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

CoinMonitor — Android crypto price monitoring app (Kotlin + Jetpack Compose). Watchlist + floating overlay workflow. Tracks Binance, Binance Alpha, OKX spot pairs and on-chain tokens via OKX DEX Market API.

## Build & Test Commands

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (requires signing config in local.properties or env vars)
./gradlew :app:assembleRelease

# Unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Full pre-commit check
./gradlew testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## Modules

- **`:app`** — Main Android application
- **`:lib`** — Pure Kotlin/JVM library for AI analysis agents (no Android dependencies)
- **`:third_party:lightweightlibrary`** — Vendored TradingView Lightweight Charts Android wrapper

## Architecture

Source root: `app/src/main/java/io/baiyanwu/coinmonitor/`

```
boot/        Boot/upgrade broadcast receivers
data/        Database (Room), network (Retrofit/OkHttp), repositories, refresh engines
domain/      Core models (WatchItem, MarketQuote, etc.) and repository interfaces
overlay/     Floating overlay: foreground service, WindowManager views, permission handling
ui/          Compose screens, ViewModels, themes
```

`lib/` module: `lib/src/main/kotlin/io/baiyanwu/coinmonitor/lib/agents/` — AI analysis agents (IndicatorAgent, MarketAgent, SynthesisAgent).

### DI

Manual DI via `AppContainer` (created in `CoinMonitorApp`). Access globally via `Context.appContainer()`.

### Data Flow

```
GlobalQuoteRefreshCoordinator
  → StreamingQuoteRefreshEngine (4 concurrent WSS connections: Binance Spot, Binance Alpha, OKX Spot, OKX On-chain)
  → InMemoryQuoteRepository (StateFlow-based in-memory state)
  → UI / Overlay subscribe to per-item quote flows
```

WSS-first with REST snapshot fallback on disconnect. Subscription fingerprinting prevents unnecessary reconnections. Quotes batched with 350ms flush window. Price snapshots persisted to Room every 15 minutes.

### Key Design Decisions

- Prices flow through in-memory `QuoteRepository` via StateFlow, not Room on every WSS push (reduces scroll jank)
- Overlay uses `WindowManager + View` (not Compose) for stability
- Room schema version 7, explicit migrations only (no destructive migrations)
- Secondary screens (Search, Overlay Settings, etc.) are separate Activities, not Compose destinations
- OKX on-chain credentials stored locally via AndroidX Security Crypto, never uploaded

## Commit Style

Prefix-based: `fix:`, `feat:`, `docs:`, etc.
