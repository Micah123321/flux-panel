# 变更记录

## 2026-08-29

- 新增管理员全站视图：后端 `POST /api/v1/user/admin/summary`（`@RequireRole`）返回用户/转发/隧道/节点统计、隧道用量排行、用户用量 Top10 与全站小时流量趋势；管理员仪表板新增全站概览区块（统计卡、全站24h趋势图、用户 Top10、隧道用量表格），原个人概览保留。转发管理平铺模式放开为展示全部用户转发（后端本就按角色区分范围）。隧道页隧道卡片对管理员叠加 `N 用户 · M 转发 · 流量` 占用徽标。前端补声明 `@heroui/divider`、`@heroui/checkbox`、`@internationalized/date`、`@react-aria/i18n` 四个此前被幽灵依赖引用的包，修复 tsc 构建。

- 修复节点换机/重装后配置丢失：节点 10 分钟配置上报（/flow/config）时，除清理孤立配置外，恢复 syncLimiters 调用补建缺失限流器，并新增 syncMissingServices 按 `in_node_id` 隧道下的启用转发对比节点上报服务名（`_tcp`/`_udp`/`_tls`），缺失即通过 updateForwardA 整组重建（update 失败回退 create），限速规则随 user_tunnel.speed_id 一并恢复；updateForwardA 增加失败日志。新增 tests/node_config_sync_check.mjs 自检。

- 修复 CI 构建失败：上一变更移除 `go-gost/x/service` 的 `init()` 时遗留了已无使用的标准库 `"log"` import（`log.Fatal` 仅存在于被删代码中，`log.Errorf` 等调用实际指向局部 logger 变量），触发 `"log" imported and not used` 编译错误；删除该 import 并通过 `go build ./...` 全量验证。

- 修复一键安装 GOST 失败：`go-gost/x/service` 的 `init()` 在解析任何命令行参数前强制加载 `config.json`，导致 `install.sh` 的 `-V` 版本探测必然触发 `配置文件不存在` 并退出。移除该预检（主流程 `main()` 中已有同等校验，预检无任何下游作用），并修正 `main.go` 两处 `fmt.Println` 误用格式化占位符的输出；`install.sh` 将 `config.json` 写入提前到 `-V` 探测前，兼容线上旧版二进制。

- 移除 Android / iOS 原生客户端、`flux.ipa`，以及前端 WebView 桥接和面板设置页；面板仅保留 Web / H5。
- 优化 GitHub Actions 构建：GOST 对齐 Go 1.23.4 并使用 setup-go 内置缓存、并行编译双架构；Docker 构建接入 GHA 层缓存，前端/后端拆分依赖层并补充 `.dockerignore` 以降低构建上下文传输。
- 新增聚合转发：支持节点组、多入口 IP/域名、入口/出口端口范围、负载均衡/主备切换、倍率、备注、聚合流量统计和老部署幂等建表迁移。
- 新增商业化基础能力：管理员可维护套餐、设备组、用户组、兑换码、订单和邀请比例；用户可购买套餐、兑换兑换码、创建邀请链接并查看返现。
- 新增商业化数据库表与 `user` 扩展字段，并同步 `gost.sql` 与 `panel_install.sh` 的幂等迁移。
- 新增前端 `/commerce-admin`、`/shop`、`/register` 页面和商业化 API 封装。
- 收紧订单完成权限：普通用户只能创建待支付订单，套餐发放只能由管理员确认或后续支付回调触发。
- 新增四渠道支付接入：EasyPay 易支付聚合、支付宝官方、微信支付官方、Stripe；管理员可编辑付款方式，用户下单选择渠道，支付回调验签成功后自动到账发放套餐。

## 2026-08-28

- 修复一键安装在已有 Docker 网络时的 IPv4 子网冲突：安装脚本自动检测重叠 CIDR、选择可用子网并持久化到 `.env`，Compose 改为读取 `DOCKER_IPV4_SUBNET`。
- 修复前端镜像构建中 `@heroui/input` 与 `@heroui/system` 的导出不匹配问题，将 `@heroui/input` 锁定到 `2.4.29` 并刷新 npm lockfile。
- 新增转发批量新增功能：后端 `/forward/batch-create` 接口逐条复用单条创建链路并返回成功/失败明细，前端“批量新增”弹窗一次提交多条转发。
- 新增转发批量删除功能：后端 `/forward/batch-delete` 接口（逐条权限校验、force 兜底、成功/失败明细），前端多选模式（全选/半选、悬浮操作条）与隧道分组快捷清空入口。

## 2026-08-29 每日流量限制（全链路）

- 套餐设置新增"每日流量限制"（daily_flow，GiB，0=不限制）：package_plan 表、后端实体/DTO/校验、commerce-admin 表单、commerce 购买页展示。
- 套餐购买/续费时 dailyFlow 写入 user 与 user_tunnel 并清零日计数（CommerceServiceImpl.applyPackage / syncUserGroupTunnels）。
- 网关执行：FlowController 流量上报链路同步累加 daily_in_flow/daily_out_flow（用户/隧道/转发三处），并在用户与隧道检查链中追加日限检查，超限复用 PauseService 通道（go-gost 零改动）。
- 每日重置：ResetFlowAsync 每天 0 点清零日计数；恢复采用"清零前捕获已超日限候选集 + 恢复前全条件校验"策略，避免误恢复手动暂停的转发。
- 用户侧展示：dashboard 已用流量卡片新增"今日流量"进度条。
- 自检：tests/daily_flow_limit_check.mjs（全链路静态断言，已通过）；前端 tsc 全量通过。
