# TODO

## Deferred

- Android 钱包改造：在“钱包 → 更多 → DApp 浏览器”补回入口。底部“浏览”Tab 与主导航发现页 route 已移除；保留的 `DappDiscoveryRoute`、目录/搜索历史、`DappBrowserActivity` 和 Web3 Provider 可继续复用。后续明确目录发现页与手输网址如何呈现，沿用既有原生确认，不把 WebView 放回主 Tab。热门现有浏览器调用仍保留。
- Android UI 后续逐页接入：底部导航与设置首页已完成，紧凑品牌标题和导航行已加入通用层。后续按 `docs/UI_DESIGN_SYSTEM.md` 接入行情、热门、钱包与二级设置页，补齐金融数据列表、主 Tab/详情页容器、加载/后台刷新/保留旧数据失败提示和弹层反馈；未接入页面继续保持现有内容与主题映射。
- 将 `AgentResult` 进一步泛型化或分型化，减少外部在消费 `typedOutput` 时需要自行做类型判断。
