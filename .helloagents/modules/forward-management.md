# 转发管理模块

## 概览
转发（Forward）是面板核心资源：用户将入口端口流量转发到目标地址。后端实体 `Forward`，
控制器 `ForwardController`（`/api/v1/forward/*`），服务层 `ForwardService`/`ForwardServiceImpl`。
删除转发涉及两步：先删 GOST 节点上的服务（`deleteGostServices`），再删数据库记录；
强制删除（force-delete）跳过 GOST 验证直接删记录，用于节点失联时的兜底。

## 聚合转发（2026-08-29 新增）

### 数据模型
- `AggregateNodeGroup`（表 `aggregate_node_group`）保存节点组名称、成员 `nodeIds` 和备注；成员是现有 `Node` 记录，因此同一物理节点可通过多次安装形成多个设备成员。
- `AggregateForward`（表 `aggregate_forward`）保存入口节点组、出口节点组、手动入口 IP/域名列表、入口/出口端口范围、模式、倍率、网卡、备注、状态与聚合流量。
- 初始化安装用 `gost.sql`，老部署更新用 `panel_install.sh` 的 `CREATE TABLE IF NOT EXISTS` 幂等迁移创建两张表。

### 后端接口
- 节点组：`POST /api/v1/aggregate-node-group/create|list|update|delete`，仅管理员可操作。运行中的聚合转发引用节点组时禁止修改成员，避免 GOST 服务与数据库状态分叉。
- 聚合转发：`POST /api/v1/aggregate-forward/create|list|update|delete|pause|resume`，仅管理员可操作。
- 模式：`load_balance` 映射 GOST selector `round`；`failover` 映射 GOST selector `fifo`，按出口组成员顺序主备。
- 服务编排：每个入口节点、每个入口端口创建 TCP/UDP 服务，远端目标展开为出口组所有节点的 `serverIp/ip:targetPort`。一次端口跨度上限为 200。
- 端口占用：聚合转发创建会检查入口节点允许端口范围、普通转发已占用端口、其他聚合转发入口范围；普通转发分配端口时也会纳入聚合占用。
- 流量统计：聚合服务名为 `agf_<聚合ID>_<入口端口>`，`FlowController` 对该前缀单独累加 `aggregate_forward.in_flow/out_flow` 并应用倍率，不扣普通用户/隧道配额。

### 前端
- `/aggregate-forward` 页面同时管理节点组和聚合转发。
- 入口 IP/域名由用户手动填写，支持逗号、空格、换行分隔；系统不托管 DNS、TLS 证书或反代。
- 聚合规则卡片展示入口/出口组、端口范围、模式、倍率、服务数量、入口地址和聚合流量。


## 批量新增（2026-08-28 新增）

### 后端
- `POST /api/v1/forward/batch-create`，请求体 `{ forwards: ForwardDto[] }`。
- `ForwardServiceImpl.createForwards(forwards)` 逐条做最小字段校验后复用 `createForward`，因此沿用单条创建的隧道校验、权限/额度检查、端口分配、GOST 服务创建和失败回滚。
- 返回约定：全部成功 `code=0`；部分/全部失败 `code=-1`，`data` 包含 `{ total, successCount, failedCount, success: [{index, name}], failed: [{index, name, reason}] }`。

### 前端（vite-frontend/src/pages/forward.tsx）
- 工具栏“批量新增”按钮打开批量输入弹窗。
- 输入格式沿用导出格式：`目标地址|转发名称|入口端口`，每行一条；入口端口留空时后端自动分配。
- 前端先做格式校验，再一次调用 `batchCreateForwards`；结果列表按行展示成功/失败原因，至少一条成功后刷新列表。

## 批量删除（2026-08-28 新增）

### 后端
- `POST /api/v1/forward/batch-delete`，请求体 `{ ids: number[], force?: boolean }`。
- `ForwardController.batchDelete`：解析 ids（`Long.valueOf(rawId.toString())`），force 默认 false。
- `ForwardServiceImpl.deleteForwards(ids, force)`：ids 去重后逐条复用 `deleteForward(id)` /
  `forceDeleteForward(id)`，汇总返回 `{ total, successIds, failed: [{id, reason}] }`。
- 返回约定：全部成功 `code=0`；部分/全部失败 `code=-1`，但 `data` 始终携带明细供前端展示。
- 权限：不加 `@RequireRole`（与单条删除一致）；服务层 `validateForwardExists` 保证普通用户
  只能操作自己的转发，越权 id 返回失败明细。

### 前端（vite-frontend/src/pages/forward.tsx）
- 工具栏"批量管理"按钮进入/退出多选模式（`batchMode`）。
- 多选模式下：卡片头部显示 Checkbox（`selectedForwardIds: Set<number>`），列表顶部全选栏
  （支持半选态），底部悬浮操作条显示已选数量 + 批量删除 + 取消；拖拽排序入口隐藏避免冲突。
- 隧道分组（grouped 视图）AccordionItem 头部新增红色小删除按钮：删除该隧道下全部转发
  （`handleTunnelGroupDelete` → 确认弹窗 → `confirmTunnelGroupDelete`）。
- 删除失败自动降级：`window.confirm` 询问后对失败项调用 `batchDeleteForwards(ids, true)`
  强制删除；结果以 toast 汇报成功/失败数量。
- API 封装：`api/index.ts` 的 `batchDeleteForwards(ids, force)`。

## 节点配置自动同步（2026-08-30 新增）

- 背景：节点服务/链/限速器存放在节点本地 `gost.json`，节点换机或重装后本地配置全部丢失，面板此前只做单向清理。
- 触发：节点每 10 分钟向 `/flow/config` 上报当前配置（`StartConfigReporter`），面板 `CheckGostConfigAsync.cleanNodeConfigs` 处理。
- 限流器：`syncLimiters`（已恢复调用）对比数据库 `speed_limit` 与上报 limiters，缺失的通过 `updateSpeedLimit` 补建（节点端 Update 不存在时面板回退 Add）。
- 转发服务：`syncMissingServices` 查询以该节点为入口的隧道下的启用转发，对比上报服务名（`_tcp`/`_udp`，隧道转发另查 `_tls`），缺失任一即调用 `ForwardService.updateForwardA` 整组重建；暂停/异常转发不重建。
- 顺序保证：清理孤立配置在前、补建在后，同一次上报内完成；`updateForwardA` 重建失败仅记 warn 日志，等待下次上报重试。
- ha-min: 同步串行执行且依赖 10 分钟上报周期，节点转发数量极大时收敛较慢；需要实时性时可另行增加手动触发入口。

## 已知限制
- 批量新增和批量删除都逐条处理不引入事务边界（与单条操作语义一致；GOST+DB 两步本身非原子），
  部分失败时已成功项不回滚，通过 success/failed 或 successIds/failed 明细告知前端。
