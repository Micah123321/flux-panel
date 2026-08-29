# 维护与发布通道

## 当前策略

- `main` 是唯一维护分支。
- 项目只保留单一公开安装入口。
- 前端只展示单一项目版本，不按客户端环境切换版本号。
- 仓库不再包含 Android / iOS 原生客户端；移动端通过浏览器 H5 布局访问。
- 对外一键安装命令均指向当前仓库。
- CI 每次 `main` push 或手动触发都会构建 GOST 二进制、前端镜像、后端镜像，并刷新 `latest` release 资产。
- GOST 构建使用 Go 1.23.4 + setup-go 缓存，并按平台并行编译；前端/后端镜像使用 Buildx GHA 层缓存，Dockerfile 拆分依赖层，Docker context 排除本地产物。
- 前端镜像使用 `package-lock.json` + `npm ci`；HeroUI 包版本需保持 lockfile 兼容，当前 `@heroui/input` 固定为 `2.4.29` 以匹配 `@heroui/system` 的 `useLabelPlacement` 导出。
- 面板安装/更新会检测所有 Docker IPv4 IPAM 子网，自动避开重叠 CIDR，并将选中的 `DOCKER_IPV4_SUBNET` 写入 `.env`；Docker 检查失败时安装会显式停止，不会猜测可用网段。

## 安装资产

- 面板 compose 与初始化 SQL 直接从 raw main 获取，避免 release 附件滞后导致一键安装失败。
- 节点端 GOST 二进制从 latest release 获取，需在公开发布后确认 latest release 中存在 `gost-amd64` 和 `gost-arm64`。
- GOST 二进制要求工作目录（cwd）下存在 `config.json`；`install.sh` 在 `-V` 版本探测前写入该文件并 `cd "$INSTALL_DIR"` 后执行探测，新旧二进制均兼容；`go-gost` 包内不得在 `init()` 阶段强制读取配置（会阻断 `-V`/`--help` 等所有命令路径）。
- GHCR package 首次发布后可能需要在 GitHub Packages 中设为 public，否则 Docker Compose 拉取会失败。
