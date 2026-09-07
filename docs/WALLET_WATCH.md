# 观察地址

“观察地址”是独立的只读钱包资产页。首页“链上”分页底部提供常驻摘要横栏，显示当前观察地址的头尾缩写和经过用户隐藏、风险及小额筛选后的可见资产总额；点击整条横栏进入 `WalletWatchActivity`，右侧刷新按钮可直接更新当前地址的快照。第一版支持 EVM 与 Solana 地址，不提供交易、转账、盈亏分析、地址命名、多地址管理、定时轮询或悬浮窗展示。

## 使用流程

1. 在 OKX [Developer Portal](https://web3.okx.com/onchainos/dev-portal) 创建 API 凭证，取得 API Key、Secret Key 和 Passphrase。
2. 打开应用“设置 → 第三方 API 设置 → OKX 钱包资产 API”，填写三项凭证并启用。鉴权规则见 [OKX API access and usage](https://web3.okx.com/zh-hans/onchainos/dev-docs/home/api-access-and-usage)。
3. 回到首页“链上”分页，点击底部“观察地址”摘要横栏。
4. 输入 `0x` 加 40 个十六进制字符的 EVM 地址，或 Base58 解码后为 32 字节的 Solana 地址，然后查询。

最后一次有效地址保存于普通应用偏好中，重新进入页面后会自动查询。API 凭证只保存在 `EncryptedSharedPreferences`，主密钥由 Android Keystore 管理；安全存储不可用时应用拒绝保存和使用凭证，不会降级为明文。

## 数据范围

明细调用 OKX [Get Total Token Balances](https://web3.okx.com/onchainos/dev-docs/wallet/balance-api-all-token-balances)：

```text
GET /api/v6/dex/balance/all-token-balances-by-address
address=<wallet>&chains=<chain indexes>&excludeRiskToken=1
```

总资产调用 OKX [Get Total Value](https://web3.okx.com/onchainos/dev-docs/wallet/balance-api-total-value)：

```text
GET /api/v6/dex/balance/total-value-by-address
address=<wallet>&chains=<chain indexes>&assetType=1&excludeRiskToken=<true|false>
```

客户端先调用 OKX `/api/v6/dex/balance/supported/chain` 获取余额接口当前支持的链。EVM 地址使用本地 EVM 注册表与实时支持列表的交集（包括 Robinhood `4663`）；Solana 只使用实时列表中的 `501`。这样单个已下线或尚未开放的 ChainIndex 不会让整组查询失败。注册表集中在 `OkxWalletChainRegistry`，不复用 DexScreener chain ID。明细保留全部非零资产，同一代币在不同链上分别展示。页面根据实际有资产的链生成横向切换栏，默认选中持仓价值最高的链，并只显示当前链的资产；页头总资产仍采用 OKX 返回的全链总值。风险资产始终位于当前链列表末尾；默认不计入总额，用户可在当前页面会话中开启计入。

OKX 明细不提供单项美元总值，因此单项价值仅使用 OKX 数据计算：

```text
holdingValue = balance × tokenPrice
```

余额、价格、单项价值和总值均使用 `BigDecimal`。无价格资产仍保留在查询结果中；“隐藏小于 1U”开启时按小额资产隐藏，关闭后显示并将单价与价值标为“暂无价格”。页头优先采用 OKX `totalValue`；总值请求失败而明细成功时，临时汇总当前风险选项下的可定价明细，并明确标注来源。

## 鉴权和安全

客户端在最终 URL 生成后签名，保证签名中的查询顺序与编码和实际请求一致：

```text
requestPath = encodedPath + "?" + encodedQuery
preHash = timestamp + HTTP_METHOD + requestPath + body
signature = Base64(HMAC-SHA256(secretKey, preHash))
```

请求携带 `OK-ACCESS-KEY`、`OK-ACCESS-SIGN`、`OK-ACCESS-PASSPHRASE`、`OK-ACCESS-TIMESTAMP`。首次请求通过 OKX 公共 `/api/v5/public/time` 计算服务端时钟偏移，再生成 UTC ISO 8601 时间戳，避免设备自动时间与 OKX 网关时间不一致时触发 `50102`。网络日志统一把三个敏感 Header 显示为 `***`；Secret Key 不进入请求 Header，也不进入业务日志。

新的加密文件名为 `okx_wallet_credentials_secure`，与历史 `okx_api_credentials_secure` 隔离，避免旧链上设置迁移删除当前凭证。

## 页面状态

- 未配置或未启用凭证：显示设置说明和“前往第三方 API 设置”。
- 首次成功查询后保存最近一次钱包资产快照。再次进入页面时先展示同一地址的本地快照，同时在后台刷新 OKX 数据；新请求成功后原地覆盖，失败时保留缓存内容并显示错误。没有可用缓存时才显示进度与列表占位。
- 下拉刷新和风险总值刷新保留已有内容。
- 空地址：显示输入引导；格式错误时在本地拦截，不发网络请求。
- 无资产：显示“该地址暂未查询到代币资产”。
- 请求失败：保留上一次成功快照，显示错误和重试按钮。
- 鉴权、签名和设备时间错误：显示针对性提示，引导用户检查凭证或系统时间。
- 资产列表使用不可展开的紧凑行；合约地址或 Mint 显示头尾缩写，点击可复制完整地址。原生代币不提供复制操作。
- 总资产卡右上角的显示设置提供“隐藏小于 1U”和“隐藏风险资产”两个勾选项，两项默认开启。前者隐藏持仓价值低于 1U 以及无法取得价格或持仓价值的资产；后者隐藏风险资产。页头总资产和资产数量按当前全部可见链、可见资产及两个筛选项实时汇总，隐藏链或单币后立即从总额中移除，恢复后重新计入。
- 长按资产行会在按压位置附近弹出带闭眼图标的“隐藏”菜单，主列表不常驻显示隐藏按钮。手动隐藏状态按钱包地址持久保存。
- 链横向栏最前方提供编辑按钮。进入编辑状态后，每条可见链的右上角显示小型红色隐藏按钮；隐藏链后该链及其全部资产从主列表移除，并自动选择下一条可见链。
- 页面底部以“已隐藏 · X 条链 · Y 个币”吸底入口汇总用户主动隐藏的内容。底部面板分为“隐藏的链”和“隐藏的币”：链显示名称和资产数量，币显示名称、所属链、格式化数量和缩写合约地址，两类内容都可单独恢复。恢复链不会同时恢复此前逐币隐藏的资产；小于 1U 和风险资产的临时显示筛选不计入该入口，但会影响页头可见资产总额。
- 钱包资产列表使用 Coil Compose 异步加载代币图标，由 Coil 负责图片请求、解码及内存/磁盘缓存；项目原有 `CoinIconService` 只负责解析 Symbol 对应的远程图标 URL，并继续服务非 Compose 悬浮窗。

## 代码结构

```text
domain/model/WalletWatchModels.kt
domain/repository/WalletWatchRepositories.kt
data/network/OkxWalletClient.kt
data/repository/DefaultOkxWalletCredentialsRepository.kt
data/repository/DefaultWalletPortfolioRepository.kt
data/repository/DefaultWalletPortfolioCacheRepository.kt
ui/walletwatch/WalletWatchActivity.kt
ui/walletwatch/WalletWatchViewModel.kt
ui/walletwatch/WalletWatchRoute.kt
```

`WalletPortfolioRepository` 负责地址分类、链分流、明细与总值并发、DTO 映射、排序和部分失败。`WalletWatchViewModel` 维护单一 UI 状态，负责首次自动查询、刷新、风险开关、当前链选择，以及按地址持久化隐藏链和隐藏资产。该功能与现有 DexScreener/GeckoTerminal 行情、CEX WSS、观察币对和悬浮窗数据流彼此独立。

## 验证

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew :app:assembleDebug
```

测试覆盖地址识别、ChainIndex 分流、固定 HMAC 签名向量、最终编码查询签名、OKX 字段映射、风险参数、`BigDecimal` 金额计算、排序、总值失败兜底和鉴权 Header 脱敏。
