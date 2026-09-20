# 自托管钱包

底部“钱包”页是本机自托管热钱包。首次默认启用 Ethereum、BNB Chain、Robinhood Chain 和 Solana Mainnet；Base、Arbitrum、Optimism、Polygon 等目录网络可由用户继续启用。Solana 是固定网络；EVM 网络由可更新目录与用户自定义 RPC 动态扩展，不要求每增加一条标准 EVM 链就发布新版 App。

## 能力边界

- 助记词钱包使用一组 12 词 BIP39 助记词，派生首个 EVM 地址和首个 Solana 地址。
- 原始私钥导入按密钥体系选择 `EVM` 或 `Solana`。EVM 私钥派生出的同一地址适用于全部已启用 EVM 网络；Solana 私钥只用于 Solana，不把 ETH、BNB Chain、Base 等 EVM 网络拆成重复钱包。
- 支持多钱包创建、助记词/原始私钥导入、唯一命名、切换、备份、删除、收款与转账。
- 支持 EVM 原生币与 ERC20、SOL 与 SPL Token。第一版不包含 Swap、跨链、授权管理、NFT、WalletConnect、硬件钱包、多账户索引或手动 Token。
- Trust Wallet Core 只负责密钥、地址与签名。所有余额、Token、手续费、广播和状态查询都由 Alchemy 或用户配置的 RPC 完成。

## 网络模式

在“设置 → 第三方 API 设置 → 钱包网络”填写一个 Alchemy API Key 后，应用会为目录中带 Alchemy 节点映射的网络自动组成 RPC 地址，用户不需要逐条填写。网络管理页可以启用或停用目录中的网络，也可以手动刷新由仓库维护的 EVM 网络目录。

Alchemy 尚未收录或目录尚未更新的标准 EVM 网络，可以直接填写名称、Chain ID、原生币符号、浏览器地址和 RPC 添加。自定义 EVM 网络立即进入网络列表，不依赖发版；保存前必须通过 `eth_chainId` 验证。单条网络也可以覆盖为自建或第三方 RPC，自定义 RPC 永远优先于 Alchemy RPC。Solana 自定义节点仍验证 Mainnet genesis hash。

| 配置 | 节点调用 | Token/价格/活动 |
| --- | --- | --- |
| 仅 Alchemy | Alchemy RPC | Alchemy Portfolio/Transfers |
| 自定义 RPC + Alchemy | 自定义 RPC | Alchemy 索引 |
| 仅自定义 RPC | 自定义 RPC | 只显示原生币；活动只含本 App 发起的交易 |
| 未配置 | 不请求 | 地址仍可离线查看，不显示伪造的零余额 |

Alchemy 失败而自定义 RPC 可用时，应用保留缓存 Token 列表，只刷新原生余额；失败不会把历史 Token 余额覆盖为零。聚合 Portfolio/价格读取整体失败只在底部短暂提示“价格暂时读取失败”，不把空资产误判为失败，也不把错误归到任意一条链。部分 Token 索引、原生余额节点和活动索引失败会分别记录到实际网络，并只在该网络的“资产”或“活动”页面显示；顶部和“全部网络”不堆叠跨链警告。

资产区默认隐藏估值小于 1 美元的资产，并默认隐藏“未验证资产”。Alchemy 的可替代 Token 数据没有与 OKX 等价的风险分类，因此这里不伪装成“风险资产”判断。无价格资产不会从缓存或钱包数据中删除；关闭小额过滤后仍可显示，并标记“暂无价格”，但不计入估算总资产。总资产和资产数量始终按当前可见资产计算。

链选择栏复用观察地址页的紧凑样式，左侧编辑按钮用于进入网络编辑状态。移除一条链会持久化为停用状态、清空该链当前展示数据，并停止后续余额和活动拉取；需要恢复时从“设置 → 钱包网络”重新启用。总资产卡、资产行以及法币、Token 数量、Token 价格和网络费格式也与观察地址页共用同一套视觉与 `AssetAmountFormatter` 规则。

钱包 Portfolio、RPC、活动、手续费、广播和状态查询全部复用应用统一的 `OkHttpClient`，因此在用户开启网络日志后会进入同一套脱敏记录。生产仓库构造函数不提供裸 `OkHttpClient()` 默认值，避免漏注入时静默绕过日志、超时和公共网络策略。

远程目录只描述标准 EVM 网络的 Chain ID、Alchemy 主机、原生币和浏览器等公开元数据，不包含用户 API Key。Token/价格/活动能力按网络单独标记；Alchemy 只提供节点能力而没有 Portfolio/Transfers 索引的网络会自动降级为原生币与本地活动，不会伪装成完整索引。

## 转账

转账顺序固定为：选择资产 → 输入收款地址和金额 → 校验地址/余额 → RPC 估算费用 → 展示完整确认信息 → 密码或生物识别重验 → Wallet Core 签名 → RPC 广播 → 本地待确认记录 → RPC/Alchemy 状态归并。

确认后签名使用用户实际确认过的 `WalletTransferEstimate`，不会在确认与签名之间重新估算并静默替换 nonce、blockhash、金额或费用。EVM 会检查原生币是否足够覆盖转账和 Gas；SPL 首次向尚未创建关联 Token Account 的地址转账时，会把账户免租金余额计入费用检查。

观察钱包与自托管钱包的法币金额、Token 数量和价格统一经过 `AssetAmountFormatter` 的 `BigDecimal` 规则；行情报价仍使用适合 `Double` 和极小价格压缩显示的 `QuoteFormatter`，不会用字符串截断钱包余额。

## 本机安全

- 钱包密码经 PBKDF2-HMAC-SHA256（随机盐、210,000 次）派生 256 位密钥；钱包载荷再以 AES-GCM 加密。
- 首次创建保险库的密码至少 10 位，且须满足大写字母、小写字母、数字、符号中的至少 3 类，不允许空格；创建和导入表单会实时提示强度与两次输入是否一致。
- 加密后的钱包载荷放在 `EncryptedSharedPreferences`，其主密钥由 Android Keystore 管理；安全存储不可用时拒绝创建钱包，不降级到明文。
- 生物识别只包装密码派生密钥，Keystore key 要求每次使用强生物识别授权，并在生物特征变更后失效。
- 助记词/私钥展示页启用 `FLAG_SECURE`，不提供默认复制按钮；签名结束后尽可能清零内存中的私钥字节。
- App 离开前台立即锁定；前台连续五分钟无用户交互也锁定。
- 钱包、网络凭证、生物识别包装数据和钱包缓存全部排除 Android 云备份与设备迁移。
- 忘记钱包密码只能重置钱包保险库。重置不触及 OKX 观察地址、行情设置或其他 App 数据。

## Wallet Core 构建凭证

官方 Android AAR 由 GitHub Packages 发布，下载需要 GitHub 身份验证。开发机在未提交的 `local.properties` 中配置只读凭证：

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

也可使用环境变量 `GITHUB_ACTOR` 与 `GITHUB_TOKEN`。Token 只需 `read:packages`，不得提交到仓库。

## 观察地址隔离

右上角“观察地址”打开原 `WalletWatchActivity`。旧 OKX 查询、最后地址、隐藏链/资产、小额与风险过滤、缓存及 OKX 凭证均复用原实现；该页面没有转账入口，也不会读取或合并本地钱包。

## 主要代码

```text
domain/model/SelfCustodyWalletModels.kt
domain/repository/SelfCustodyWalletRepositories.kt
data/wallet/WalletCoreKeyService.kt
data/wallet/WalletTransactionSigner.kt
data/wallet/WalletBiometricManager.kt
data/repository/DefaultWalletVaultRepository.kt
data/repository/DefaultWalletNetworkSettingsRepository.kt
data/repository/DefaultSelfCustodyWalletRepository.kt
data/network/AlchemyWalletClient.kt
data/network/WalletRpcClient.kt
ui/wallet/WalletViewModel.kt
ui/wallet/WalletRoute.kt
```

## 验证

必须先配置 Wallet Core GitHub Packages 只读凭证，再运行：

```bash
./gradlew :app:testDebugUnitTest --no-parallel
./gradlew :app:lintDebug --no-parallel
./gradlew :app:assembleDebug --no-parallel
```

代码级验证不等同于真实主网小额转账验证。涉及真实资金前，仍应在隔离钱包中逐链执行最小金额的原生币、ERC20、SOL 与 SPL 端到端测试。
