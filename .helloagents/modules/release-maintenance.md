# 维护与发布通道

## 当前策略

- `main` 是唯一维护分支。
- 项目只保留单一公开安装入口。
- 前端只展示单一项目版本，不按客户端环境切换版本号。
- 对外一键安装命令均指向当前仓库。
- CI 每次 `main` push 或手动触发都会构建 GOST 二进制、前端镜像、后端镜像，并刷新 `latest` release 资产。

## 安装资产

- 面板 compose 与初始化 SQL 直接从 raw main 获取，避免 release 附件滞后导致一键安装失败。
- 节点端 GOST 二进制从 latest release 获取，需在公开发布后确认 latest release 中存在 `gost-amd64` 和 `gost-arm64`。
- GHCR package 首次发布后可能需要在 GitHub Packages 中设为 public，否则 Docker Compose 拉取会失败。
