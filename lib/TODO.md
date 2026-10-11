# TODO

## Deferred

- Android UI 后续逐页接入：底部导航、首页、热门页、钱包主页、钱包 DApp 目录页与设置首页已完成，通用金融列表行和走势图已实现。后续按 `docs/UI_DESIGN_SYSTEM.md` 接入二级设置及钱包详情样式，逐步补齐页面容器与数据状态反馈；沿用现有数据请求与钱包业务流程。
- 将 `AgentResult` 进一步泛型化或分型化，减少外部在消费 `typedOutput` 时需要自行做类型判断。

## Completed

- 2026-10-09：钱包主页接入紧凑圆润蓝紫样式；“钱包 → 更多 → DApp 浏览器”通过独立 `DappDiscoveryActivity` 复用目录/搜索历史，并打开既有独立浏览器与 Provider。用户手动查看钱包 UI 后确认可接受。
