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

## 版本号

修改：

- `app/build.gradle.kts`

规则：

- `versionName`: 例如 `1.0.4`
- `versionCode`: 单调递增

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

## 发布前本地检查

```bash
git status --short -b
./gradlew :app:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:lintDebug
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

示例：

```bash
gh release create v1.0.4 --title v1.0.4 --generate-notes
```

Release 创建后 GitHub Actions 会自动打包上传 APK。**必须等待打包成功后再合入 main**，否则打包失败时 main 上已经有错误版本。

等待方式：创建 Release 后，每 2 分钟检查一次打包状态，直到成功或失败。

```bash
# 每2分钟检查一次
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
- 不要在 `main` 直接做日常开发修复
- 发布阻塞问题优先修在 `release/x.y.z`
- 线上紧急问题优先修在 `hotfix/x.y.z`
- `release/x.y.z` 是后续规范，历史版本不追溯调整

