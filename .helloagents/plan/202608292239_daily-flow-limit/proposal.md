# 方案：套餐每日流量限制（全链路）

## 需求
在套餐设置中新增"每日流量限制"，购买/续费后作用于用户，网关在流量上报链路强制执行，每日 0 点重置。

## 设计
- `package_plan.daily_flow` BIGINT，单位 GiB，0=不限制（与 flow/speed_mbps 约定一致）。
- `user` 与 `user_tunnel` 新增 `daily_flow`（GiB）、`daily_in_flow`、`daily_out_flow`（字节），与月度 in_flow/out_flow 平行。
- 套餐发放（applyPackage/syncUserGroupTunnels）写入 daily_flow 并清零日计数。
- FlowController 流量上报时同步累加日计数，并在用户/隧道检查链中追加日限检查，超限复用 PauseService 通道（网关零改动）。
- ResetFlowAsync 每日 0 点 cron：全表清零日计数，随后恢复满足全部限额/状态条件的 status=0 转发。
- 前端：commerce-admin 套餐表单与卡片、commerce 套餐卡片、dashboard 用户侧"今日流量"展示。

## 简化标注
- ha-min: 日限恢复采用"重置后重查全部限额+状态再恢复"策略，月度超限用户恢复后最迟 10 分钟内被下次流量上报再次暂停，不做暂停原因持久化。

## 验收
- mvn compile 通过；tsc build 通过。
- 日限超限暂停 / 每日重置 / 套餐发放写入三条链路代码级自检。