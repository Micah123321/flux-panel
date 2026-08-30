# 变更记录

## 2026-08-30

- 修复 dashboard 无套餐/空套餐数据时误报 `获取套餐信息失败`：前端新增 `normalizePackageInfo` 空态规范化，`/user/package` 成功但 data/userInfo 为空时展示 0 配额/空隧道/空转发，不再因 `data.userInfo` 为空触发 catch；后端 `/user/package` DTO 对可空数字和列表统一做 0/空数组兜底。新手引导卡片改为点击关闭才写入 `guide_checklist_closed_admin|user`，并清理旧版自动写入的 `guide_checklist_seen_admin|user`，刷新页面不会自动消失。自检：`node tests/dashboard_empty_package_check.mjs` 与前端 `npm run build` 通过。

- 修复面板更新脚本在非安装目录执行时的失败链：`update_panel` 此前不加载 `.env`，compose 以空变量渲染（端口映射为空、`FRONTEND_PORT/DB_*/JWT_SECRET` 全部告警），且按当前目录解析项目名（如 `~/nexus-terminal` 下解析为 `nexus-terminal`），与历史安装残留的同名容器（`gost-mysql`）冲突导致 `up -d` 报 `container name already in use`。修复：新增 `load_env_file`（`set -a; source .env; set +a`）与 `validate_env`（校验 `DB_NAME/DB_USER/DB_PASSWORD/JWT_SECRET/FRONTEND_PORT/BACKEND_PORT`，缺失即终止并提示切换到安装目录）；新增 `remove_stale_containers` 按 compose 项目标签识别并清理归属异项目的同名残留容器（数据在命名卷中不受影响）；`down`/`up -d` 追加 `--remove-orphans`；`uninstall_panel` 同样先加载 `.env`。自检：`tests/panel_install_fix_check.sh`（docker 桩模拟残留容器场景，6 项断言全部通过，含 `bash -n` 语法检查）。

- 修复 Docker 前端镜像构建失败：CI `npm ci` 报 `Missing: driver.js@1.8.0 from lock file`（EUSAGE）。根因：新增新手引导功能时 `driver.js` 仅写入 `vite-frontend/package.json`，本地用 pnpm 验证构建（pnpm 不维护 `package-lock.json`），而 Docker 构建走 `npm ci` 严格校验两文件同步。修复：向 `vite-frontend/package-lock.json` 补录根依赖区 `"driver.js": "^1.8.0"` 与 `packages["node_modules/driver.js"]` 完整条目（1.8.0 / registry resolved / sha512 integrity / MIT）；全量比对确认 `package.json` 与 lock 根 manifest 其余依赖完全同步（共 946 包，无其他失步项）。

## 2026-08-30（多主控）

- 新增单节点多主控：节点端（go-gost 3.2.0）支持同时接入多个面板，每个主控独立 WebSocket 命令通道与 HTTP 流量/配置上报通道；新增 `nameshift` 命名空间包（p2/p3… 前缀翻译、流量按服务名路由、配置上报逐面板视图过滤），面板侧零改动。`config.json` 升级为 `servers` 数组（旧格式自动迁移，顶层 addr/secret 保留为首主控镜像，旧版二进制可读）；`install.sh` 重跑即追加/更新主控，新增添加/移除/查看主控菜单（jq→python3 降级）。已知边界：`SetProtocol`（http/tls/socks 屏蔽）为节点全局开关，多主控下后设置者生效。验证：`go build ./...`（root+x）、`go test ./internal/util/nameshift/`（4 用例）、`bash -n install.sh` 通过；未触碰 springboot-backend/vite-frontend。
- README 特性清单补充「单节点多主控」。
- 新增新手引导系统：`/guide` 使用向导页（管理员部署 7 步 / 用户上手 3 步，步骤实时调用接口检测完成状态），导航栏新增「使用向导」入口，仪表板按角色在首次登录时弹出引导卡片（localStorage 记忆，可关闭）；商店页与转发页接入 driver.js 首访气泡引导。
- 商业化体验打磨：购买改为支付模态框并每 3 秒轮询订单状态（最长 5 分钟），到账自动提示并刷新；新增邀请余额抵扣下单（`order_record.invite_deduction` 落库 + 安装脚本幂等迁移），抵扣后全额覆盖时订单直接完成发放，四渠道支付金额改为应付减抵扣的净额；邀请返现奖励基数同步改为实付口径（无抵扣时行为不变）。
- 重构 `commerce-admin.tsx`（30.9KB 单文件）为 `commerce-admin/` 目录：骨架 + 常量 + 套餐/分组/兑换码/订单/支付/邀请六个子组件，状态下放各分区。
- 验证：前端 `pnpm build`（tsc + vite）通过；`tests/commerce_feature_check.mjs` 扩展余额抵扣与引导断言后通过；新增 `tests/invite_balance_check.mjs` 金额语义自检通过。

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
