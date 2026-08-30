# 聚合转发

## 范围

- 前端入口：`vite-frontend/src/pages/aggregate-forward.tsx` 的 `/aggregate-forward` 页面。
- 后端入口：`springboot-backend/src/main/java/com/admin/service/impl/AggregateForwardServiceImpl.java`。
- 聚合转发以入口节点组监听端口范围为基础，为每个入口节点、每个入口端口创建 GOST 服务，并按入口端口偏移映射到出口端口范围。

## 端口推荐

- 新增聚合转发时默认选择入口/出口节点组，并从两组成员节点的公共端口范围计算推荐范围。
- 推荐范围长度取入口公共范围、出口公共范围与 `MAX_PORT_SPAN` 的最小值；入口和出口端口数量始终一致。
- 推荐按钮展示 `起始-结束 · N 个`，点击后自动填入入口/出口四个端口字段。
- 当前 `MAX_PORT_SPAN = 10001`，用于覆盖默认节点端口段 `50000-60000`。超过该量级仍应改造为后台批处理、进度展示与失败回滚。

## 验证

- 静态自检：`node tests/aggregate_forward_ui_check.mjs`。
- 前端构建：在 `vite-frontend` 执行 `npm run build`。