# 项目上下文

flux-panel 当前维护仓库为 `Micah123321/flux-panel`，主分支 `main` 是唯一维护与发布通道。项目只保留单一公开安装入口。

安装链路约定：

- README 和后端节点安装命令从 `https://raw.githubusercontent.com/Micah123321/flux-panel/refs/heads/main/` 获取安装脚本。
- 面板安装脚本从当前仓库 `main` 分支获取 `docker-compose-v4.yml`、`docker-compose-v6.yml` 和 `gost.sql`。
- 节点安装脚本从当前仓库 GitHub latest release 下载 `gost-amd64` 或 `gost-arm64`。
- Docker Compose 使用 `ghcr.io/micah123321/springboot-backend:latest` 和 `ghcr.io/micah123321/vite-frontend:latest`。
