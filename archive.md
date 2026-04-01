# 发布流程

说明：

- 历史版本保持现状，不追溯修改旧分支名或旧 tag
- 从下一个版本开始，统一使用 `release/x.y.z` 分支模式
- 从下一个版本开始，tag 示例统一写成 `vX.Y.Z`

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

- `versionName`: 例如 `1.0.3`（示例）
- `versionCode`: 单调递增

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

### 3. 发布分支验证通过后，合入 `main`

示例：

```bash
git checkout main
git pull
git merge release/1.0.4
git push origin main
```

### 4. 打 tag

规则：

- tag: `vX.Y.Z`（示例：`v1.0.4`）

示例：

```bash
git tag v1.0.4
git push origin v1.0.4
```

### 5. 创建 GitHub Release

示例：

```bash
gh release create v1.0.4 --title v1.0.4 --generate-notes
```

### 6. 把发布分支回合并到 `dev`

示例：

```bash
git checkout dev
git pull
git merge release/1.0.4
git push origin dev
```

## GitHub Actions

自动 workflow：

- `.github/workflows/android.yml`
- `.github/workflows/android-release.yml`

Release 创建后会自动：

1. 跑 `testDebugUnitTest`
2. 跑 `:app:lintDebug`
3. 跑 `:app:assembleRelease`
4. 上传 APK 到 GitHub Release

补充：

- `release/*`、`hotfix/*`、`dev`、`main` 的 push 都会触发 `Android CI`
- 仅推送 tag 不等于自动发布
- 当前自动打包入口是 GitHub Release 发布，或手动触发 `workflow_dispatch`

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

- 单测失败会直接导致 `Android CI` 和 `Android Release` 失败
- 普通 push 不会自动重跑已经存在的旧 release tag
- 不要在 `main` 直接做日常开发修复
- 发布阻塞问题优先修在 `release/x.y.z`
- 线上紧急问题优先修在 `hotfix/x.y.z`
- `release/x.y.z` 是后续规范，历史版本不追溯调整

## 最小命令顺序

下面命令中的版本号、tag、commit message、分支名都是示例，需要按当次发布替换：

```bash
git checkout dev
git pull
git checkout -b release/1.0.4

./gradlew :app:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:lintDebug

git add app/build.gradle.kts
git commit -m "Bump version to 1.0.4"
git push origin release/1.0.4

git checkout main
git pull
git merge release/1.0.4
git push origin main

git tag v1.0.4
git push origin v1.0.4

gh release create v1.0.4 --title v1.0.4 --generate-notes

git checkout dev
git pull
git merge release/1.0.4
git push origin dev
```
