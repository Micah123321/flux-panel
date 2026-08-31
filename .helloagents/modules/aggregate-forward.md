# 聚合转发与节点组

## 范围

- 前端入口：`vite-frontend/src/pages/aggregate-forward.tsx` 的 `/aggregate-forward` 页面只负责节点组管理。
- 后端节点组入口：`AggregateNodeGroupServiceImpl`。节点组是隧道管理可复用的入口/出口资源。
- legacy 聚合转发入口：`AggregateForwardServiceImpl`。独立聚合转发规则已下线，create/update/resume 返回下线提示；delete 仅保留用于清理历史数据。

## 当前模型

- 在节点组页面创建节点组；每组返回成员节点、在线状态和 `portSta`/`portEnd`，前端展示共同可用端口范围。
- 在隧道管理新增隧道时，入口和隧道转发出口都可以选择单节点或节点组；`Tunnel`/`TunnelDto` 使用 `inGroupId`、`outGroupId` 保存组引用，`inNodeId`、`outNodeId` 保留为首个节点兼容字段。
- 节点组调度策略是隧道属性：`Tunnel.strategy` 保存 `round/fifo/rand/hash`，新节点组隧道默认 `round`；转发表单不再配置策略。
- 普通转发仍是一条业务规则。`ForwardServiceImpl` 根据隧道的节点组展开 GOST 下发：入口组每个节点创建主服务；隧道转发出口组每个节点创建远端服务；入口 chain 指向出口组所有节点地址并使用隧道策略选择。
- 转发列表会展开显示入口组全部入口地址；转发/隧道诊断都会覆盖组内入口节点，避免看起来只有首个节点生效。
- 端口分配按组内所有成员的公共端口范围找共同空闲端口，避免只检查首个节点导致组内冲突。
- 节点组被隧道或运行中的历史聚合转发引用时不能删除或修改成员。

## 历史数据清理

- 历史 `aggregate_forward` 记录可能已经铺设大量 `agf_<id>_<port>_{tcp,udp}` 服务。删除接口使用 `GostUtil.DeleteServices` 批量删除服务名，并对 `not found` 做二分收敛；Go 节点端必须路由 `DeleteServices` 到现有批量删除 handler，避免回落为未知命令。
- 若 DB 行已经先被清掉，节点配置上报清理会识别 `agf_` 历史服务；只处理 TCP 侧并调用 `DeleteService` 删除对应 tcp/udp 对。
- 清理顺序：先用后端 legacy delete 清理 GOST 服务，再删除 DB 记录；不要先手动删 DB 行。

## 验证

- 静态自检：`node tests/aggregate_forward_ui_check.mjs`。
- 前端构建：在 `vite-frontend` 执行 `npm run build`。
- 本机没有 Maven/Maven Wrapper 时，后端编译依赖 CI 或远程镜像构建验证。
