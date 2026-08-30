# 商业化与邀请系统

## 范围

- 后端商业化能力集中在 `CommerceServiceImpl`，控制器分为管理员 `/api/v1/admin/commerce/**` 与用户 `/api/v1/commerce/**`。
- 数据表包括 `package_plan`、`device_group`、`user_group`、`user_group_device_group`、`order_record`、`redeem_code`、`invite_record`、`invite_reward_record`、`payment_config`。
- `user` 表扩展 `package_plan_id`、`user_group_id`、`speed_mbps`、`ip_limit`、`connection_limit`、`invite_code`、`inviter_user_id`、`invite_balance`。
- 每日流量限制：`package_plan.daily_flow`（GiB，0=不限制），发放后落到 `user`/`user_tunnel` 的 `daily_flow`，并配 `daily_in_flow`/`daily_out_flow` 日计数（字节）。
- 前端管理员入口为 `/commerce-admin`，用户入口为 `/shop`，公开邀请注册入口为 `/register?invite=CODE`。

## 行为约定

- 套餐时长按 `duration_multiplier * 30` 天发放；流量沿用现有用户流量字段的 GiB 语义。
- 每日流量限制与月度流量（`flow` + `flow_reset_time`）相互独立：日限每天 0 点由 `ResetFlowAsync` 清零计数；`FlowController` 上报链路在用户与隧道检查链中执行日限检查，超限复用 `PauseService` 通道；每日重置后只恢复"清零前已超日限"的转发（候选集 + `isForwardAllowed` 全条件校验），避免误恢复手动暂停的转发。
- 隐藏套餐不显示在购买列表，但可通过兑换码兑换；停用套餐不可购买或兑换。
- 创建购买订单只生成待支付订单；普通用户不能自助完成订单，套餐发放只能由管理员 `/api/v1/admin/commerce/order/complete` 确认或可信支付回调 `/api/v1/payment/notify/{channel}` 触发。
- 支付渠道配置存放在 `payment_config`，当前支持 `easypay`、`alipay`、`wechat`、`stripe`；后端不向用户端返回密钥明文，管理员保存空值或掩码时保留原密钥。
- 自动到账必须通过渠道验签和商户身份校验：EasyPay 用 MD5 签名和 `pid`，支付宝用 RSA2 和 `app_id`，微信支付用平台证书/RSA2、APIv3 解密、`appid/mchid/trade_state`，Stripe 用 webhook secret HMAC。
- 用户组绑定设备组，设备组保存可用隧道 ID 列表；套餐发放时同步创建或更新用户的 `user_tunnel` 授权。
- 邀请返现比例存放在 `vite_config` 的 `invite_ratio` 和 `invite_renewal_ratio`，升级迁移只在缺失时插入默认值，不覆盖已有配置。

## 引导与体验（2026-08-30 增补）

- 新手引导：`/guide` 页 + `guide-checklist.tsx` 清单组件（管理员 7 步实时接口检测 / 用户 3 步）；dashboard 按角色展示引导卡片，只有点击关闭才写入 localStorage `guide_checklist_closed_admin|user`，并清理旧版自动写入的 `guide_checklist_seen_admin|user`，刷新页面不会自动消失；商店/转发页首访 driver.js 气泡（`shop_tour_v1` / `forward_tour_v1`）。
- 余额抵扣：`CreateOrderRequest.useInviteBalance`；`deduction = clamp(余额, 0, 应付)`；`order_record.invite_deduction` 落库；抵扣后应付为 0 时订单直接完成发放（balance-only），部分抵扣时四渠道支付金额为 `netAmount = 应付 - 抵扣`；余额扣减用 `invite_balance >= amount` 条件更新（乐观扣减），金额用 `toPlainString` 拼接。
- 邀请返现奖励基数改为实付口径（应付 - 抵扣），无抵扣时与旧行为一致；`completePaidOrder` 到账校验与管理员确认的 `paidAmount` 同步改为实付口径。
- 商店页购买进入支付模态框，3 秒轮询 `getMyOrders` 匹配订单状态（最长 5 分钟），到账自动刷新；`commerce-admin.tsx` 已拆分为 `commerce-admin/` 目录（index + constants + 六个 Section 子组件）。

## 验证

- 前端执行 `npm run build` 通过。
- 后端当前环境没有 `mvn`/`javac`，用 `node tests/commerce_feature_check.mjs`、`node tests/invite_balance_check.mjs` 与 `node tests/dashboard_empty_package_check.mjs` 做关键接入点、金额语义、dashboard 空套餐/引导回归，以及 `/user/package` 空 DTO 兜底自检。
- 每日流量限制链路用 `node tests/daily_flow_limit_check.mjs` 验证（覆盖 SQL/实体/DTO/发放/上报执行/重置恢复/前端展示的静态断言）。
