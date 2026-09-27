# Third-party notices

CoinMonitor includes the following direct third-party components in addition to the dependencies declared by Gradle:

## Trust Wallet Core

- Project: <https://github.com/trustwallet/wallet-core>
- Version: `4.8.3`
- Copyright: Trust Wallet
- License: Apache License 2.0

Wallet Core is used for local key generation, address derivation, address validation, and EVM/Solana transaction signing. CoinMonitor does not modify or redistribute Wallet Core source in this repository; the Android artifact is resolved from Trust Wallet's GitHub Packages registry during the build.

## Trust Web3 Provider

- Project: <https://github.com/trustwallet/trust-web3-provider>
- Version: `4.9.4`
- Copyright: Trust Wallet
- License: MIT License

Trust Web3 Provider supplies the compiled JavaScript provider injected into the restricted DApp WebView. CoinMonitor owns the native approval, RPC routing, and signing policy; the provider never receives wallet seed phrases or private keys.
