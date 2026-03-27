# Contributing

感谢你对 `CoinMonitor` 的关注。

这个仓库目前仍在快速迭代阶段，欢迎通过 Issue、Discussion、Pull Request 的方式参与改进。

## Before You Start

请先确认：

- 改动目标明确，尽量聚焦单一主题
- 没有提交任何本地环境文件、签名文件、密钥或私有配置
- 对 UI、状态流转、悬浮窗行为的修改，尽量补充必要说明

## Development Setup

```bash
git clone <your-repo-url>
cd CoinMonitor
./gradlew :app:assembleDebug
```

## Recommended Checks

提交前请至少执行：

```bash
./gradlew testDebugUnitTest :app:assembleDebug :app:lintDebug
```

如果改动涉及以下内容，建议额外关注：

- 悬浮窗行为：检查权限缺失、启用/关闭、最近任务移除后的行为
- 设置页：检查语言、主题、刷新间隔是否正确回流
- 文案与资源：尽量同步维护中英文资源

## Pull Request Guidelines

- PR 标题请尽量清晰说明改动目标
- 描述里建议写明：
  - 为什么要改
  - 改了什么
  - 如何验证
  - 是否影响 UI 或行为
- 如果是 UI 改动，建议附截图或录屏
- 如果是行为修复，建议说明复现路径

## Code Style

- Kotlin / Android 代码请保持现有风格
- 注释尽量专业、克制，优先解释状态边界、设计约束和非显然行为
- 优先保持代码可读性和行为一致性，避免为了“更抽象”而过度设计

## Commit Style

推荐使用简洁的前缀式提交信息，例如：

- `fix: harden overlay runtime flow`
- `docs: refresh open source readme`
- `feat: add custom refresh interval mode`

## Questions

如果你不确定某个改动是否合适，欢迎先提 Issue 讨论，再提交 PR。
