# 发布流程

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

## 提交版本号

示例：

```bash
git add app/build.gradle.kts
git commit -m "Bump version to 1.0.3"
```

## 分支同步

当前约定：

- 开发分支：`dev`
- 发布分支：`main`

发布时把 `dev` 同步到 `main`：

```bash
git checkout main
git merge --ff-only dev
git push origin main
git push origin dev
```

## 打 tag

规则：

- tag: `releaseX.Y.Z`（示例：`release1.0.3`）

示例：

```bash
git tag release1.0.3
git push origin release1.0.3
```

## 创建 GitHub Release

示例：

```bash
gh release create release1.0.3 --title release1.0.3 --generate-notes
```

## GitHub Actions

自动 workflow：

- `.github/workflows/android-release.yml`

Release 创建后会自动：

1. 跑 `testDebugUnitTest`
2. 跑 `:app:lintDebug`
3. 跑 `:app:assembleRelease`
4. 上传 APK 到 GitHub Release

## 关键注意点

- 单测失败会直接导致 `Android CI` 和 `Android Release` 失败
- 普通 push 不会自动重跑已经存在的旧 release tag
- 如果重写过历史，`main`、`dev`、`releaseX.Y.Z` 要一起同步

## 最小命令顺序

下面命令中的版本号、tag、commit message 都是示例，需要按当次发布替换：

```bash
./gradlew :app:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:lintDebug

git add app/build.gradle.kts
git commit -m "Bump version to 1.0.3"

git checkout main
git merge --ff-only dev
git push origin main
git push origin dev

git tag release1.0.3
git push origin release1.0.3

gh release create release1.0.3 --title release1.0.3 --generate-notes
```
