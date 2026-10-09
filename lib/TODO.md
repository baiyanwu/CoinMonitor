# TODO

## Deferred

- Android 钱包改造：在“钱包 → 更多 → DApp 浏览器”补回入口。底部“浏览”Tab 与主导航发现页 route 已移除；保留的 `DappDiscoveryRoute`、目录/搜索历史、`DappBrowserActivity` 和 Web3 Provider 可继续复用。后续明确目录发现页与手输网址如何呈现，沿用既有原生确认，不把 WebView 放回主 Tab。热门现有浏览器调用仍保留。
- Android UI 后续逐页接入：底部导航、首页、热门页（列表/筛选/详情）与设置首页已完成，通用金融列表行和走势图已实现。后续按 `docs/UI_DESIGN_SYSTEM.md` 接入钱包及二级设置页，逐步补齐页面容器与数据状态反馈；数据请求、现有全局主题映射与钱包 DApp 入口 TODO 继续保留。
- 将 `AgentResult` 进一步泛型化或分型化，减少外部在消费 `typedOutput` 时需要自行做类型判断。
