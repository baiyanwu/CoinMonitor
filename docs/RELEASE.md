# 发布流程

## 分支约定

- `dev`：日常开发分支
- `release/x.y.z`：当前版本发布分支
- `main`：正式发布分支
- `hotfix/x.y.z`：线上紧急修复分支

原则：

- 日常功能只进 `dev`
- 发版内容从 `dev` 切到 `release/x.y.z`
- `main` 只接收已经准备发布完成的版本
- 紧急修复从 `main` 切 `hotfix/x.y.z`
- 旧 tag 命名保持不动，新版本按当次约定执行，不回改历史
- 不按分发渠道维护长期 `GP` 分支：同一源码树通过 `github` / `play` product flavor 生成渠道包；临时准备工作遵循现有开发和发布分支流程

## 版本号

修改：

- 产品版本名称：`app/build.gradle.kts` 中的 `appVersionName`
- 渠道构建代码：`config/distribution.properties`，也可在打包时传入另一份外部配置文件

规则：

- `versionName`: 例如 `1.0.4`
- `versionCode`: 按渠道配置，每个渠道的新发布代码必须递增，不必与 `versionName` 的末段相同

同一产品版本在两个渠道使用相同 `versionName`，可以有不同的 `versionCode`。Play 重打包时只增加 `play.versionCode`，GitHub 渠道读取 `github.versionCode`。Gradle 读取配置文件，不自动修改其中的值；上传前必须把该渠道代码改为更高且未使用的值。若用户跨渠道覆盖安装，目标包的代码还必须不低于设备已安装包的代码，因此需要跨渠道切换时应协调两个渠道的数值。

当前默认配置：

```properties
github.versionCode=17
play.versionCode=20
```

配置文件只存公开构建参数，不放签名密码或凭证。配置需包含 `github.versionCode` 和 `play.versionCode`，两个 flavor 分别读取自己的值，数值必须是 `1..2100000000` 的整数。可用 `-PdistributionConfigFile=/absolute/path/distribution.properties` 指定另一份文件。

仅渠道更新来源、商店兼容或重新打包调整可保留产品版本名称；用户可感知的功能或行为修复仍按正常发版决定新版本名称。每个发布产物应记录 `versionName`、`versionCode`、渠道和 SHA-256，保留现有 tag 的历史身份。当前 Play 构建使用 `1.0.15 / 20`；本地只保留本次发布最后确认的交付产物，旧 AAB 与过程核验文件在最终包验证后删除。

## 分发渠道

渠道由 `productFlavors` 中的 `github` / `play` 决定；在 Android Studio 的 Build Variants 面板或通过带 flavor 名的 Gradle 任务选择。包名均为 `io.baiyanwu.coinmonitor`，版本名称共用 `appVersionName`，正式签名共用已有 release keystore。

- `github`：通过 `assembleGithubRelease` 生成 APK，保留启动时的 GitHub Release 更新检查。
- `play`：通过 `bundlePlayRelease` 生成 AAB，关闭 GitHub 更新请求和提示，更新由 Google Play 负责；暂不接入 Play 应用内更新弹窗。
- App 使用 Gradle 自动生成的 `BuildConfig.FLAVOR` 区分更新来源。GitHub 发布 CI 只构建 `githubRelease`，日常 CI 验证 GitHub Debug 的单测、lint 和构建，以及 Play Release 的单测和 lint。

使用 JDK 17 / SDK 36，在项目根目录直接运行 Gradle。以下打包命令要求已有正式签名配置齐全。在 macOS 上可先设置 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。

```bash
# GitHub 正式 APK
./gradlew :app:assembleGithubRelease -PrequireReleaseSigning=true --no-parallel

# Play 正式 AAB
./gradlew :app:bundlePlayRelease -PrequireReleaseSigning=true --no-parallel

# 使用另一份外部配置文件（相对路径以项目根目录为准）
./gradlew :app:bundlePlayRelease \
  -PdistributionConfigFile=/absolute/path/distribution.properties \
  -PrequireReleaseSigning=true --no-parallel
```

GitHub APK 在 `app/build/outputs/apk/github/release/`，例如 `coinmonitor-v1.0.15-github-release.apk`；Play AAB 在 `app/build/outputs/bundle/playRelease/`，例如 `coinmonitor-v1.0.15-play-release.aab`。首次配置 Play App Signing 必须导入现有应用签名密钥，核对 **App signing key certificate** 的 SHA-256 与 GitHub 正式 APK 一致；上传证书一致本身不能证明分发 APK 的应用签名一致。

## Play 本地交付产物

本次 Play 发布文件统一放在 `release-artifacts/1.0.15/`，该目录加入 `.gitignore`。AAB、更新日志、视频、商店图片及上传清单不提交到开发分支；不再另外保留 `artwork/google-play/` 导出副本。

- 正式 AAB：`coinmonitor-v1.0.15-play-code20-20261012-150247.aab`，包名 `io.baiyanwu.coinmonitor`，版本名称 `1.0.15`，Version Code `20`，target SDK `36`。
- 更新日志：与 AAB 同名的 `.release-notes.txt`，包括 19 个语言区块，描述首次 Play 发布；内容不列举具体行情服务品牌，直接修改该文件，不另外生成多份。
- 商店素材：`coinmonitor-app-icon-512.png`、`coinmonitor-feature-graphic-1024x500.png` 和 `coinmonitor-phone-01-markets.png`、`coinmonitor-phone-02-trending.png`、`coinmonitor-phone-03-settings.png`。截图来自已有的 1.0.14 中文界面素材，导出尺寸为 1080×1920。
- 前台服务演示视频：`coinmonitor-foreground-service-demo.mp4`。
- 上传清单：`coinmonitor-play-upload-files.json`，记录当前文件名、大小和 SHA-256。

最终 AAB 的 SHA-256：`039e8468f06cf5e7d8acc8d35b5ef36bafdaab1b5374e19781df5952ca29a435`。正式签名证书 SHA-256：`C2:F5:91:7D:59:12:0D:35:D7:4E:E2:C5:22:96:F6:1E:91:34:83:55:33:CD:1C:75:09:E9:C5:6E:DA:B0:0C:AE`，与已有 GitHub 正式包一致。

旧 AAB、重复图片、支持咨询截图、临时预览、Manifest/Dex 核验副本及本次 Play 构建过程文件在交付后清理，不作为最终产物保留。正式 keystore 与本地签名配置继续保存于各自已有位置并保持忽略。

## Play 权限声明

- 悬浮前台服务使用 `specialUse`，Manifest 内通过 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明用户主动开启的跨应用持续行情展示；保留 `SYSTEM_ALERT_WINDOW` 和通知中的停止入口。
- 当前 Code 20 AAB 不含 `FOREGROUND_SERVICE_DATA_SYNC`。若 Console 仍要求该声明，先检查所有生效发布与测试轨道中的旧包，用新包替换或停用相应旧发布；仅删除本地文件不会改变商店轨道。
- 当前包未使用广告 ID，也不含 `com.google.android.gms.permission.AD_ID`，Advertising ID 声明选择 `No`。
- 演示视频公开链接：`https://baiyanwu.github.io/CoinMonitor/assets/foreground-service-demo.mp4`。视频部署在已有 Pages 的 `release-web:/docs/assets/`，本地归档副本保持忽略。

权限声明的范围与流程见 [Google 官方说明](https://support.google.com/googleplay/android-developer/answer/9214102?hl=en)，前台服务功能说明和视频要求见 [Foreground service requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)。构建和本地核验成功不代表商店审核已通过。

2026-10-12 提交前验证：GitHub Debug 单元测试、完整 lint 与 APK 构建通过；Play Release 单元测试、发布 lint 与正式 AAB 构建通过。两个渠道各 401 项测试，均为 0 失败、0 错误、1 项既有测试跳过。最终 AAB 的 524 个载荷条目签名与摘要通过核验；从 Play Console 下载的 Code 20 包与本地归档 SHA-256 相同。验证范围为源码、构建、包内容和签名，过程报告在整理后删除。

## 隐私政策

Play Console 的隐私政策 URL 为 `https://baiyanwu.github.io/CoinMonitor/privacy.html`，英文页为 `https://baiyanwu.github.io/CoinMonitor/privacy-en.html`；App 内入口位于“设置 → 关于 → 隐私政策”。政策源文件是 `docs/privacy.html` 和 `docs/privacy-en.html`，共用现有首页样式。现有 GitHub Pages 使用 `release-web` 分支的 `/docs` 目录，网页更新发布到该分支。

## 签名配置

本地签名和 CI 签名都已经预留好接入点：

- 本地开发默认从 `local.properties` 读取 release 签名参数
- GitHub Actions 默认从 secrets 注入 release 签名参数

本地参数键（写入 `local.properties`）：

- `release.storeFile`
- `release.storePassword`
- `release.keyAlias`
- `release.keyPassword`

GitHub Actions secrets 约定：

- `ANDROID_KEYSTORE_BASE64`（keystore 文件 base64 编码）
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

## GitHub Release 更新日志规范

GitHub Release 正文是给产品用户看的最终更新日志，不是 Git 提交记录。每个新版本都必须写清用户能感知到的新增、调整、移除和改进，不能只放 `Full Changelog`、提交列表或 PR 标题。

发布日志以 `docs/CHANGELOG.md`、上一个已发布 tag 到当前发布提交的实际差异为事实来源，但必须改写成产品语言：

- 必须同时提供英文和中文内容，格式参考历史 `release1.0.2`
- 优先描述用户现在能做什么、界面或行为发生了什么变化
- 有意义的新增、调整、移除和兼容性影响不能遗漏
- 无需逐项披露的缺陷可以统一写成 `Bug fixes and stability improvements.` / `修复已知问题并提升稳定性。`
- 不要把类名、内部架构、重构、commit hash、PR 标题或纯实现细节当作产品日志
- `Full Changelog` 比较链接可以保留，但只能放在产品日志末尾作为补充
- 发布时不得保留占位内容

标准结构如下；没有内容的类别直接省略，不要为了凑格式编造：

```markdown
## X.Y.Z

### What's New

- Added ...
- Improved ...
- Bug fixes and stability improvements.

### 更新内容

- 新增……
- 优化……
- 修复已知问题并提升稳定性。

**Full Changelog**: https://github.com/baiyanwu/CoinMonitor/compare/PREVIOUS_TAG...vX.Y.Z
```

## 发布前本地检查

```bash
git status --short -b
./gradlew :app:compileGithubDebugKotlin
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:lintGithubDebug
```

## 标准发版流程

### 1. 从 `dev` 切发布分支

示例：

```bash
git checkout dev
git pull
git checkout -b release/1.0.4
```

### 2. 在发布分支处理发版内容

包括：

- 修改版本号
- 修发版阻塞问题
- 跑本地检查

示例：

```bash
git add app/build.gradle.kts
git commit -m "Bump version to 1.0.4"
```

### 3. 发布分支验证通过后，打 tag

规则：

- tag: `vX.Y.Z`（示例：`v1.0.4`）

示例：

```bash
git tag v1.0.4
git push origin v1.0.4
```

### 4. 创建 GitHub Release 并等待打包成功

先根据本页规范完成并检查最终产品更新日志，再通过 `--notes-file` 创建 Release。不要使用 `--generate-notes` 代替产品日志。

示例：

```bash
gh release create v1.0.4 --verify-tag --title v1.0.4 \
  --notes-file /absolute/path/to/reviewed-release-notes.md
gh release view v1.0.4 --json body,url
```

创建后必须回读 Release 正文，确认中英文产品日志完整、没有占位符，并且正文不是只有 `Full Changelog` 链接。

Release 创建后 GitHub Actions 会自动打包上传 APK。**必须等待打包成功后再合入 main**，否则打包失败时 main 上已经有错误版本。

所有 CI 状态监控统一每 4 分钟检查一次，包括发布分支 Android CI、Android Release 和 main/dev 合并后的 Android CI。

等待方式：创建 Release 后，每 4 分钟检查一次打包状态，直到成功或失败。

```bash
# 每4分钟检查一次
gh run list --workflow=android-release.yml --limit 1
# 或直接查看 Release 页面的 Actions 状态
```

- 打包成功：继续下一步合入 main
- 打包失败：在 release 分支上修复，重新提交，重新创建 Release

### 5. 打包成功后，合入 `main`

示例：

```bash
git checkout main
git pull
git merge release/1.0.4
git push origin main
```

### 6. 把发布分支回合并到 `dev`

示例：

```bash
git checkout dev
git pull
git merge release/1.0.4
git push origin dev
```

## 热修流程

如果正式发布后需要紧急修复：

1. 从 `main` 切 `hotfix/x.y.z`
2. 在 `hotfix/x.y.z` 上修复并验证
3. 合回 `main`
4. 重新打对应版本 tag 或发补丁版本
5. 再把 `hotfix/x.y.z` 回合并到 `dev`

示例：

```bash
git checkout main
git pull
git checkout -b hotfix/1.0.4
```

## 关键注意点

- CI 流程细节见 TECHNICAL.md「CI/CD」章节
- 单测失败会直接导致 `Android CI` 和 `Android Release` 失败
- 普通 push 不会自动重跑已经存在的旧 release tag
- GitHub Release 必须包含最终的中英文产品更新日志；代码比较链接只能作为末尾补充
- 不要在 `main` 直接做日常开发修复
- 发布阻塞问题优先修在 `release/x.y.z`
- 线上紧急问题优先修在 `hotfix/x.y.z`
- `release/x.y.z` 是后续规范，历史版本不追溯调整
