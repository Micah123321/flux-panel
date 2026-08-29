# 转发管理模块

## 概览
转发（Forward）是面板核心资源：用户将入口端口流量转发到目标地址。后端实体 `Forward`，
控制器 `ForwardController`（`/api/v1/forward/*`），服务层 `ForwardService`/`ForwardServiceImpl`。
删除转发涉及两步：先删 GOST 节点上的服务（`deleteGostServices`），再删数据库记录；
强制删除（force-delete）跳过 GOST 验证直接删记录，用于节点失联时的兜底。

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

## 已知限制
- 批量新增和批量删除都逐条处理不引入事务边界（与单条操作语义一致；GOST+DB 两步本身非原子），
  部分失败时已成功项不回滚，通过 success/failed 或 successIds/failed 明细告知前端。
