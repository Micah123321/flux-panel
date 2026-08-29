# 变更记录

## 2026-08-29

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

- 将公开仓库引用统一为当前仓库 `Micah123321/flux-panel`。
- 移除 README 中的历史个人维护说明、赞助钱包、双通道安装入口和旧 Star History 链接。
- 将安装脚本收敛为 `main` 单通道，并增强下载失败检查。
- 将 Docker Compose 镜像切换到 GHCR latest 镜像。
- 将 CI 改为 main 单通道构建并发布 latest release 资产。
- 统一前端版本展示，移除 WebView 与网页的展示版本分流。
- 修复 CI 多架构镜像构建缺少 QEMU 初始化的问题，并关闭 GOST 构建的 VCS stamping。
- 修复前端镜像构建的 npm peer dependency 冲突，提交 npm lockfile 并改用 `npm ci`。
- 移除后端 Java 文件中的历史作者注释和代码生成器作者配置。
