# 自托管钱包

底部“钱包”页是本机自托管热钱包。首次默认启用 Ethereum、BNB Chain、Robinhood Chain 和 Solana Mainnet；Base、Arbitrum、Optimism、Polygon 等目录网络可由用户继续启用。Solana 是固定网络；EVM 网络由可更新目录与用户自定义 RPC 动态扩展，不要求每增加一条标准 EVM 链就发布新版 App。

## 能力边界

- 助记词钱包使用一组 12 词 BIP39 助记词，派生首个 EVM 地址和首个 Solana 地址。
- 原始私钥导入按密钥体系选择 `EVM` 或 `Solana`。EVM 私钥派生出的同一地址适用于全部已启用 EVM 网络；Solana 私钥只用于 Solana，不把 ETH、BNB Chain、Base 等 EVM 网络拆成重复钱包。
- 支持多钱包创建、助记词/原始私钥导入、唯一命名、切换、备份、删除、收款与转账。
- 支持 EVM 原生币与 ERC20、SOL 与 SPL Token。资产详情提供 Uniswap“兑换”和 OKX Bridge“跨链桥”的外部网页入口，但 App 不注入钱包、不执行合约调用，也不提供 WalletConnect/Web3 Provider；第一版仍不包含内置 Swap、内置跨链、授权管理、NFT、硬件钱包、多账户索引或手动 Token。
- Trust Wallet Core 只负责密钥、地址与签名。OKX Onchain API 负责发现资产、聚合余额、价格、Token 元数据和风险标记；Alchemy 或用户配置的 RPC 负责交易前链上余额校验、手续费、广播和状态查询。

## 网络模式

在“设置 → 第三方 API 设置”配置并启用 OKX Onchain API 后，自托管钱包使用同一套本机加密凭证查询资产。再在“钱包网络”填写 Alchemy API Key，应用会为目录中带 Alchemy 节点映射的网络自动组成交易 RPC 地址，用户不需要逐条填写。网络管理页可以启用或停用目录中的网络，也可以手动刷新由仓库维护的 EVM 网络目录。

Alchemy 尚未收录或目录尚未更新的标准 EVM 网络，可以直接填写名称、Chain ID、原生币符号、浏览器地址和 RPC 添加。自定义 EVM 网络立即进入网络列表，不依赖发版；保存前必须通过 `eth_chainId` 验证。单条网络也可以覆盖为自建或第三方 RPC，自定义 RPC 永远优先于 Alchemy RPC。Solana 自定义节点仍验证 Mainnet genesis hash。

| 配置 | 资产与价格 | 交易执行与状态 | 活动历史 |
| --- | --- | --- |
| OKX + Alchemy | OKX Onchain | Alchemy RPC | Alchemy Transfers + 本地交易 |
| OKX + 自定义 RPC + Alchemy | OKX Onchain | 自定义 RPC | Alchemy Transfers + 本地交易 |
| OKX + 仅自定义 RPC | OKX Onchain | 自定义 RPC | 仅本 App 发起的交易 |
| 仅 OKX | OKX Onchain | 不可交易 | 无远程活动历史 |
| 未配置 OKX | 保留最后缓存，不伪造零余额 | 取决于 RPC 配置 | 取决于 Alchemy 配置 |

OKX 失败时应用保留最后一次资产缓存，不会改用另一套聚合器，也不会把历史 Token 余额覆盖为零。聚合资产/价格读取整体失败只在底部短暂提示；正常返回空列表仍表示确实没有非零资产。OKX 只查询当前钱包支持且用户已启用的网络，并先与实时支持链列表取交集；单个地址体系请求失败或网络不受支持时，只标记对应网络。Alchemy 活动索引失败继续与资产索引失败分开处理。

资产与活动采用 stale-while-revalidate：应用按钱包 ID 保存 OKX 资产快照、Alchemy 远程活动快照和本 App 发起的本地交易；再次进入时先显示缓存并在后台刷新，只有从未产生过缓存的新钱包才显示完整 Loading。Alchemy 单链失败时保留该链旧活动，其他成功链照常覆盖；最多缓存 500 条远程活动和 100 条本地交易。活动不能只按交易哈希去重，因为同一交易可能包含多个 Token 转移；实际身份键为“网络 ID + 交易哈希 + Token 合约/Mint（原生币为 native）+ 收支方向”。

资产区默认隐藏估值小于 1 美元的资产，并默认隐藏 OKX 标记的风险资产。风险识别能力受 OKX 当前链覆盖限制，未命中不等于经过独立安全审计。无价格资产不会从缓存或钱包数据中删除；关闭小额过滤后仍可显示，并标记“暂无价格”，但不计入估算总资产。总资产和资产数量始终按当前可见资产计算。Token 名称、精度与图标优先取 OKX Token Basic Information；元数据暂缺且无法从原始余额可靠推导精度的资产仍可展示，但会标记为“暂不可转账”。

链选择栏复用观察地址页的紧凑样式，左侧编辑按钮用于进入网络编辑状态。移除一条链会持久化为停用状态、清空该链当前展示数据，并停止后续余额和活动拉取；需要恢复时从“设置 → 钱包网络”重新启用。总资产卡、资产行以及法币、Token 数量、Token 价格和网络费格式也与观察地址页共用同一套视觉与 `AssetAmountFormatter` 规则。

钱包 OKX 资产查询、RPC、活动、手续费、广播和状态查询全部复用应用统一的 `OkHttpClient`，因此在用户开启网络日志后会进入同一套脱敏记录。生产仓库构造函数不提供裸 `OkHttpClient()` 默认值，避免漏注入时静默绕过日志、超时和公共网络策略。

远程目录只描述标准 EVM 网络的 Chain ID、Alchemy 主机、原生币和浏览器等公开元数据，不包含用户 API Key。EVM 使用 Chain ID 作为 OKX ChainIndex，Solana 使用 `501`；自定义 EVM 网络只要同时出现在 OKX 实时支持链列表中，也可获得资产聚合。Alchemy 的 Portfolio 能力不再决定资产是否可见，Transfers 能力只影响远程活动历史。

## 转账

转账顺序固定为：选择资产 → 输入收款地址和金额 → RPC 重新校验原生币或 Token 链上余额 → RPC 估算费用 → 展示完整确认信息 → 密码或生物识别重验 → Wallet Core 签名 → RPC 广播 → 本地待确认记录 → RPC/Alchemy 状态归并。OKX 展示余额不会单独决定交易是否可以发送。

确认后签名使用用户实际确认过的 `WalletTransferEstimate`，不会在确认与签名之间重新估算并静默替换 nonce、blockhash、金额或费用。EVM Token 通过 `eth_call balanceOf` 二次确认余额，SPL Token 通过 `getTokenAccountBalance` 二次确认余额；原生币仍由 RPC 检查转账金额和 Gas。SPL 首次向尚未创建关联 Token Account 的地址转账时，会把账户免租金余额计入费用检查。

观察钱包与自托管钱包的法币金额、Token 数量和价格统一经过 `AssetAmountFormatter` 的 `BigDecimal` 规则；行情报价仍使用适合 `Double` 和极小价格压缩显示的 `QuoteFormatter`，不会用字符串截断钱包余额。

## 资产详情与活动

点击资产行先进入对应资产详情，不再直接进入转账表单。详情页展示当前网络、余额、估值以及“收款 / 转账 / 兑换 / 跨链桥”四个入口：收款和转账使用 App 内现有流程；兑换仅对 Uniswap 已支持的 EVM 网络开放，并通过系统浏览器打开 `app.uniswap.org`；跨链桥通过系统浏览器打开 OKX Bridge。网页不会获得本机助记词、私钥或签名能力，返回 App 后仍需按钱包锁定策略重新验证。

详情页交易历史与钱包“活动”页使用同一份 Alchemy + 本地交易数据，不额外混入 OKX 历史接口。EVM 按合约地址、Solana 按 Mint、原生币按网络和 Symbol 精确过滤，不用 Symbol 猜测同名 Token。活动列表与资产列表复用紧凑字号；资产/活动分页复用首页 ViewPager 样式，各自保留滚动位置。

## 本机安全

- 钱包密码经 PBKDF2-HMAC-SHA256（随机盐、210,000 次）派生 256 位密钥；钱包载荷再以 AES-GCM 加密。
- 首次创建保险库的密码至少 10 位，且须满足大写字母、小写字母、数字、符号中的至少 3 类，不允许空格；创建和导入表单会实时提示强度与两次输入是否一致。
- 加密后的钱包载荷放在 `EncryptedSharedPreferences`，其主密钥由 Android Keystore 管理；安全存储不可用时拒绝创建钱包，不降级到明文。
- 生物识别只包装密码派生密钥，Keystore key 要求每次使用强生物识别授权，并在生物特征变更后失效。
- 助记词/私钥展示页启用 `FLAG_SECURE`，不提供默认复制按钮；签名结束后尽可能清零内存中的私钥字节。
- App 离开前台立即锁定；前台连续五分钟无用户交互也锁定。锁定会清除解密后的钱包载荷和密钥，但保留当前导航位置以及公开的资产/活动快照；重新验证后恢复原资产详情页，内存快照缺失时先从本地缓存恢复。
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
data/network/OkxWalletClient.kt
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
