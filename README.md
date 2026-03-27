# CoinMonitor

`CoinMonitor` 是一个盯盘币价的 Android 项目，重点能力是：

- 首页只保留币对列表和 `+` 搜索入口
- 搜索来源覆盖 Binance Alpha / Binance / OKX
- 支持添加、删除、长按加入悬浮窗
- 悬浮窗配置页支持启用、锁定拖动、透明度、最大展示数量
- 设置页支持通过拖动条调整自动刷新时间，默认每 `3 秒` 刷新一次价格
- 悬浮窗通过前台服务常驻，并跟随全局刷新时间轮询价格
- 主 Activity 被关闭或从最近任务划掉后，前台服务继续维持悬浮窗和轮询

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- Retrofit + OkHttp + Kotlinx Serialization
- ForegroundService + WindowManager Overlay

## 目录结构

```text
app/src/main/java/io/baiyanwu/CoinMonitor/
  boot/        开机恢复、升级恢复、有限自恢复广播
  data/        数据库、网络、仓库实现
  domain/      核心模型和仓库接口
  overlay/     悬浮窗控制器、前台服务、轮询协调器
  ui/          Compose 页面、导航、主题
```

## 运行方式

```bash
cd <project-root>
./gradlew :app:assembleDebug
```

如果需要安装到设备：

```bash
cd <project-root>
./gradlew :app:installDebug
```

## 当前实现说明

- 设置页已开放全局刷新时间配置，当前支持 `1-10 秒` 拖动调整，默认值为 `3 秒`。
- 悬浮窗展示数量上限为 `10`，超过 `5` 个时按每批 `5` 个轮播。
- 权限缺失时，设置页会引导用户去系统授权悬浮窗权限。
- Android 系统设置中的“强行停止”会阻断自动恢复，这是系统限制，不是代码缺陷。
- 首页右下角刷新按钮点击后会执行一次单圈旋转反馈，不会持续自转，避免把“正在刷新”和“可点击入口”混成同一类状态。

## UI 实现约束

- 搜索页的紧凑搜索框使用 `BasicTextField + decorationBox` 实现，而不是继续强压 `OutlinedTextField`。
  这样可以在保持 Android 风格的前提下，稳定控制高度、hint、光标和清空按钮布局，避免出现文本裁切或输入光标显示异常。
- 当控件设计目标和当前库的默认规则存在冲突时，优先改成符合库规则的实现方式，不继续依赖极端内边距、强制高度或关闭最小交互约束等补丁式方案。
- Compose 页面颜色、文案和控件状态都尽量走统一的主题 token 和资源字符串，后续换模板颜色或扩展多语言时不需要逐页回收硬编码。

## 已知设计取舍

- 搜索页的输入框已经改成自定义紧凑实现，这是为了兼顾移动端密度和 Compose 文本输入的稳定性。
- 当前 App 内图标加载没有接入 `Glide`、`Coil` 之类第三方图库，而是使用 `OkHttp + 内存缓存 + 磁盘缓存` 的轻量方案。
- 悬浮窗仍然使用 `WindowManager + View`，没有改成 Compose，这是为了降低系统悬浮场景下的重排、生命周期和兼容性风险。
- 首页刷新按钮的旋转动效使用单次 `Animatable` 驱动，而不是无限循环动画。这样更符合“手动刷新一次”的交互语义，也避免用户误以为后台存在永不结束的刷新任务。
