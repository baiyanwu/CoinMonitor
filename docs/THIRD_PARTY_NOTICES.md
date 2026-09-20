# Third-party notices

CoinMonitor includes the following direct third-party components in addition to the dependencies declared by Gradle:

## Trust Wallet Core

- Project: <https://github.com/trustwallet/wallet-core>
- Version: `4.8.3`
- Copyright: Trust Wallet
- License: Apache License 2.0

Wallet Core is used for local key generation, address derivation, address validation, and EVM/Solana transaction signing. CoinMonitor does not modify or redistribute Wallet Core source in this repository; the Android artifact is resolved from Trust Wallet's GitHub Packages registry during the build.
