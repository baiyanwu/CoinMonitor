# 自托管钱包

底部“钱包”页是本机自托管热钱包。首次默认启用 Ethereum、BNB Chain、Robinhood Chain 和 Solana Mainnet；Base、Arbitrum、Optimism、Polygon 等目录网络可由用户继续启用。Solana 是固定网络；EVM 网络由可更新目录与用户自定义 RPC 动态扩展，不要求每增加一条标准 EVM 链就发布新版 App。

## 钱包主页 UI

- 使用通用蓝紫局部主题和 14dp 页面边距；小标题、钱包选择卡、余额卡、分组资产/活动列表和观察地址快捷卡采用紧凑圆角布局。币种图标 28dp，资产行最小 52dp；长文案和大字体允许撑开。
- 钱包选择卡展示当前网络的地址并支持复制；全网络模式优先展示该钱包支持的 EVM 地址，没有 EVM 时展示 Solana 地址。切换钱包及增加钱包沿用现有 ViewModel 和独立详情导航。
- “可见资产估值”沿用风险/小额过滤后的全网络汇总，与下面列表的网络筛选独立；展示支持网络数、可见资产数和真实快照更新时间。金额保持 BigDecimal；未加载或全部无法估值时显示“—”，已加载的空结果显示零，部分无法估值会明确提示数量。
- 眼睛按钮隐藏/显示主页余额、资产估值/数量与活动金额；该显示状态只保留于当前主页会话。刷新、显示筛选、网络/API 配置及钱包管理/备份/安全位于“更多”，下拉刷新继续有效。
- 网络胶囊打开可滚动的选择面板，支持全部网络、单个网络、移除已启用网络和进入既有网络管理。收款按钮先选择网络，再进入既有收款页面；转账继续进入既有签名/广播流程。
- 资产/活动保留各自滚动位置和横向滑动切换。后台刷新或恢复缓存期间不清空已有快照；全网络模式也展示失败网络提示。图表和第三方 Provider、密码/生物识别、备份安全策略未调整。

2026-10-10 主页校对：顶部复用通用标题栏及白色操作按钮；资产/活动采用单一滑动胶囊和 280ms 横滑，连续点击取消旧任务，停稳后同步 Tab。钱包内容 Tab 切换只修改 `contentTab`，不调用刷新；相邻页面预组合使用已有公开快照。网络筛选与收款选择面板使用统一紧凑行，保留全部/单网络选择、编辑移除及管理入口。资产估值/活动金额列统一 110dp，大字体、窄屏或长金额改到下一行，不截断金额；未验证、不可转账、未知估值及活动状态仍显示。提示/空列表采用无阴影卡片，日期和未变资产的筛选/汇总避免因切换重复计算。规则及预览范围见 [UI_DESIGN_SYSTEM.md](./UI_DESIGN_SYSTEM.md#钱包主页校对2026-10-10)。

本轮 322 个单元测试、Debug 静态检查（无错误，166 条警告）与构建通过；确认变体、包名、证书和版本兼容后，同签名 Debug 1.0.13/13 已覆盖安装至 Seeker 并保留数据。安装后停止设备操作，未执行钱包界面、密码、生物识别、转账或签名验证；组件 Preview 通过编译，不等同于实际渲染或手机视觉验收。构建/安装记录保留在 `app/build/ui-review/wallet-20261010-142840/`。

同日用户解锁并要求检查活动页后，按原始实屏调整区块间距，紧凑按钮采用 40dp 可见底盘/48dp 点击区，活动记录恢复两行主体文字并去掉每行圆形箭头底盘。观察地址快捷卡位于资产列表末尾及更多菜单，活动页不再固定显示该卡；入口仍打开原 `WalletWatchActivity`。详细诊断见 [UI_DESIGN_SYSTEM.md](./UI_DESIGN_SYSTEM.md#钱包解锁实屏反馈修正2026-10-10)。当前只确认修改前的实屏问题，修正后的观感需重新解锁后验收。

修正版通过 322 个测试与 Debug 静态检查/构建，并同签名覆盖安装。用户重新解锁后已截图复核资产页及活动页；上述区块间隔、两行活动和观察地址位置在本机浅色常规字号下生效。原图和构建/安装记录保留于 `app/build/ui-review/wallet-live-20261010-154459/`。仅执行一次活动 Tab 导航，未操作钱包资金/设置或录制动效；此记录不替代用户对观感的验收。

2026-10-11 UI 反馈修正：资产与活动共用固定圆角白色 Surface，内部 LazyColumn 保留独立滚动位置、稳定条目 ID 和内容类型；取消行间分割线，内容首尾各留 8dp。切换胶囊直接跟随分页位置，普通按钮保留单层原生涟漪。Seeker 实际检查资产短列表和活动往返滚动，圆角保持固定；6 项隔离 UI 回归覆盖钱包两页和热门四页的滚动裁剪。主页可截图，导入和备份页防截屏逻辑保留；未执行转账或签名。最终 UI 交付与验证边界见 [UI_DESIGN_SYSTEM.md](./UI_DESIGN_SYSTEM.md#当前交付与用户确认2026-10-11)。

## 能力边界

- 助记词钱包使用一组 12 词 BIP39 助记词，派生首个 EVM 地址和首个 Solana 地址。
- 原始私钥导入按密钥体系选择 `EVM` 或 `Solana`。EVM 私钥派生出的同一地址适用于全部已启用 EVM 网络；Solana 私钥只用于 Solana，不把 ETH、BNB Chain、Base 等 EVM 网络拆成重复钱包。
- 支持多钱包创建、助记词/原始私钥导入、唯一命名、切换、备份、删除、收款与转账。
- “设置 → 钱包设置”复用钱包页右上角菜单中的钱包管理、备份钱包和安全设置功能；钱包管理与安全设置直接进入既有页面，备份钱包会先要求选择目标钱包。钱包页右上角三个原入口继续保留，并直接作用于当前钱包。
- 支持 EVM 原生币与 ERC20、SOL 与 SPL Token。资产详情仍可通过系统浏览器打开 Uniswap“兑换”和 OKX Bridge“跨链桥”；底部“浏览”Tab 已移除，DApp 入口位于“钱包 → 更多 → DApp 浏览器”。独立 Activity 的受限 WebView 和 EIP-1193 Provider 保留，开放连接钱包、账户/Chain ID、网络切换、只读 RPC、标准 EVM 交易、`personal_sign`、原始 `eth_sign`、EIP-712 V3/V4，以及经原生确认的网络和 ERC20 Token 添加请求。
- Trust Wallet Core 负责密钥、地址、App 内转账及 DApp 交易/消息的本地签名；Trust Web3 Provider 只负责浏览页的 JavaScript Provider 协议层，不接触助记词或私钥。OKX Onchain API 负责发现资产、聚合余额、价格、Token 元数据和风险标记；Alchemy 或用户配置的 RPC 负责交易前链上余额校验、手续费、广播和状态查询。

## 网络模式

在“设置 → 第三方 API 设置”配置并启用 OKX Onchain API 后，自托管钱包使用同一套本机加密凭证查询资产。再在“Alchemy key”填写 Alchemy API Key，应用会为目录中带 Alchemy 节点映射的网络自动组成交易 RPC 地址，用户不需要逐条填写。网络管理页可以启用或停用目录中的网络，也可以手动刷新由仓库维护的 EVM 网络目录。

第三方 API 设置中的凭证区块依次为 OKX、GMGN、Alchemy key；三个区块均提供“去申请Key”按钮，分别打开 [OKX Developer Portal](https://web3.okx.com/onchainos/dev-portal)、[GMGN API 申请页](https://gmgn.ai/ai)和 [Alchemy Dashboard](https://dashboard.alchemy.com/)。Alchemy key 区块保留网络管理与自定义 RPC 功能，钱包的网络设置入口直接定位到该区块。

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

点击资产行先进入对应资产详情，不再直接进入转账表单。详情页展示当前网络、余额、估值以及“收款 / 转账 / 兑换 / 跨链桥”四个入口：收款和转账使用 App 内现有流程；兑换仅对 Uniswap 已支持的 EVM 网络开放，并通过系统浏览器打开 `app.uniswap.org`；跨链桥通过系统浏览器打开 OKX Bridge。这两个系统浏览器快捷入口不会获得本机助记词、私钥或签名能力；App 内独立 DApp 浏览器提供受原生确认保护的 Web3 Provider，钱包中的入口位于“更多 → DApp 浏览器”。

详情页交易历史与钱包“活动”页使用同一份 Alchemy + 本地交易数据，不额外混入 OKX 历史接口。EVM 按合约地址、Solana 按 Mint、原生币按网络和 Symbol 精确过滤，不用 Symbol 猜测同名 Token。钱包主页活动与资产列表使用通用紧凑字号与圆角分组卡片；资产/活动使用胶囊选项与 HorizontalPager，各自保留滚动位置。独立资产详情继续复用既有操作和交易历史流程。

## DApp 浏览器

底部“浏览”Tab 和对应主导航发现页 route 已移除。“钱包 → 更多 → DApp 浏览器”打开 `DappDiscoveryActivity`，复用 Compose 发现页和本地数据：从随 APK 打包的版本化 JSON 读取常用 DApp，支持本地搜索、分类筛选和 Room 搜索历史。选择 DApp 或确认手输 HTTPS 地址后再打开独立浏览器。独立 `DappBrowserActivity` 及热门页面现有调用继续保留；该 Activity 单独持有 `DappWebView`、地址栏、网页历史、链选择和 Provider 确认，不与 MainActivity 的 NavHost 或底部导航共享 WebView 生命周期。

内置目录只负责推荐、分类、图标和默认打开地址，不参与钱包权限判定。浏览器内任意 HTTPS 主页面都能发现 Provider；EIP-6963 始终以 CoinMonitor 身份公布，钱包选择器图标从 Android 应用图标实时生成，并调用 Trust Provider 自带的 `setOverwriteMetamask(true)` 兼容只识别旧版 MetaMask Connector 的 DApp，但不注入 `window.metamask` 或伪造 MetaMask 专属 API。用户手动输入网址时先显示第三方网站风险提示，网页请求账户时再按实际 origin 展示连接确认。原生桥拒绝 iframe 与非 HTTPS 来源，Provider 响应也绑定发起请求的 origin，页面跳转后不会把旧请求结果投递给新站点。不使用 `addJavascriptInterface`，禁止 HTTP 导航、混合内容、文件访问与内容 URI 访问，也不会绕过 TLS 错误。只读 RPC 通过现有 `custom RPC > Alchemy RPC` 路由和统一 `OkHttpClient` 转发，并使用显式方法白名单。

Uniswap 等 DApp 发出的 `eth_sendTransaction` 会进入原生确认流程：校验请求地址和当前 Chain ID，通过 RPC 获取 nonce、估算 Gas、读取费用与原生币余额，展示目标合约、发送金额和最大网络费，再用已启用的强生物识别或钱包密码重新验证；确认后由 Wallet Core 对 Legacy 或 EIP-1559（无 access list）交易签名，经当前 RPC 广播，并把交易哈希返回网页。

链下签名走完全独立的路径：`personal_sign` 只接受有效 UTF-8，原始 `eth_sign` 明确显示不可读数据高风险提示；EIP-712 V3/V4 从 Provider 的 `raw` JSON 重新解析并校验活动地址、当前 Chain ID、`primaryType` 和 `verifyingContract`，不信任网页提供的预计算哈希。三类签名都展示独立原生确认页，通过已启用的强生物识别或钱包密码重新验证，由 Wallet Core 执行并只返回 65 字节签名，绝不调用广播 RPC。Permit2 属于这条 EIP-712 路径。

`wallet_addEthereumChain` 只接受无凭证的 HTTPS RPC，经原生确认后必须实际请求并核对节点 Chain ID，验证成功才保存、启用并切换网络。`wallet_watchAsset` 当前接受 ERC20/BEP20 请求，经确认后按钱包和网络持久化；钱包刷新时会通过当前 RPC 读取该合约余额，即使余额为零也保留用户添加的 Token。EIP-712 V1、带 access list 的交易及非白名单 RPC 仍返回 Provider 错误。

## 本机安全

- 钱包密码经 PBKDF2-HMAC-SHA256（随机盐、210,000 次）派生 256 位密钥；钱包载荷再以 AES-GCM 加密。
- 首次创建保险库的密码至少 10 位，且须满足大写字母、小写字母、数字、符号中的至少 3 类，不允许空格；创建和导入表单会实时提示强度与两次输入是否一致。
- 加密后的钱包载荷放在 `EncryptedSharedPreferences`，其主密钥由 Android Keystore 管理；安全存储不可用时拒绝创建钱包，不降级到明文。
- 生物识别只包装密码派生密钥，Keystore key 要求每次使用强生物识别授权，并在生物特征变更后失效。
- 助记词/私钥展示页启用 `FLAG_SECURE`，不提供默认复制按钮；签名结束后尽可能清零内存中的私钥字节。
- App 在前台期间不会因页面切换或闲置自动锁定；只有连续进入后台满 30 分钟，或用户主动点击“立即锁定”时才锁定。进入浏览器或钱包功能时若保险库已经锁定，会先要求指纹或密码解锁。锁定会清除解密后的钱包载荷和密钥，但保留当前导航位置以及公开的资产/活动快照；重新验证后恢复原资产详情页，内存快照缺失时先从本地缓存恢复。
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

钱包主页底部“观察地址”快捷卡打开原 `WalletWatchActivity`。旧 OKX 查询、最后地址、隐藏链/资产、小额与风险过滤、缓存及 OKX 凭证均复用原实现；该页面没有转账入口，也不会读取或合并本地钱包。

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

2026-10-09 钱包主页 UI 改造：304 个 App Debug 单元测试通过；静态检查无错误（166 条警告）；Debug 构建完成，并在确认签名和构建类型匹配后覆盖安装至 Seeker，安装包 SHA-256 与产物一致。提供浅色/深色/大字体组件 Preview，未启动 App 或执行实体机视觉、签名、转账验收。

同日，用户再次安装后手动查看并确认钱包主页 UI 可接受。签名/主网转账仍未执行验收。

必须先配置 Wallet Core GitHub Packages 只读凭证，再运行：

```bash
./gradlew :app:testDebugUnitTest --no-parallel
./gradlew :app:lintDebug --no-parallel
./gradlew :app:assembleDebug --no-parallel
```

代码级验证不等同于真实主网小额转账验证。涉及真实资金前，仍应在隔离钱包中逐链执行最小金额的原生币、ERC20、SOL 与 SPL 端到端测试。
