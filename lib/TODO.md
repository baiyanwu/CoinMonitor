# TODO

## Deferred

- Android UI 后续逐页接入：按 `docs/UI_DESIGN_SYSTEM.md` 完成通用列表行、主 Tab/详情页容器、首次加载/后台刷新/保留旧数据失败提示和弹层反馈。底部导航、钱包主 Tab、钱包 DApp 目录页和通用列表行已接入；其他页面继续逐页迁移，独立钱包详情的业务流程保持原样。
- 将 `AgentResult` 进一步泛型化或分型化，减少外部在消费 `typedOutput` 时需要自行做类型判断。

## Completed

- 2026-10-09：钱包主页接入紧凑圆润蓝紫样式；“钱包 → 更多 → DApp 浏览器”通过独立 `DappDiscoveryActivity` 复用目录/搜索历史，并打开既有独立浏览器与 Provider。
