# 变更记录

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
