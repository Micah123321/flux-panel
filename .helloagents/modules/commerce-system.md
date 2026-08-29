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

## 验证

- 前端执行 `npm run build` 通过。
- 后端当前环境没有 `mvn`/`javac`，用 `node tests/commerce_feature_check.mjs` 做关键接入点自检。
- 每日流量限制链路用 `node tests/daily_flow_limit_check.mjs` 验证（覆盖 SQL/实体/DTO/发放/上报执行/重置恢复/前端展示的静态断言）。
