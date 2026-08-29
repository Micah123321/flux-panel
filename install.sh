#!/bin/bash

# 获取系统架构
get_architecture() {
    ARCH=$(uname -m)
    case $ARCH in
        x86_64)
            echo "amd64"
            ;;
        aarch64|arm64)
            echo "arm64"
            ;;
        *)
            echo "amd64"  # 默认使用 amd64
            ;;
    esac
}

REPO_OWNER="Micah123321"
REPO_NAME="flux-panel"
RELEASE_BASE_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/latest/download"

# 构建下载地址
build_download_url() {
    local ARCH=$(get_architecture)
    echo "${RELEASE_BASE_URL}/gost-${ARCH}"
}

# 下载地址
DOWNLOAD_URL=$(build_download_url)
INSTALL_DIR="/etc/gost"
COUNTRY=$(curl -fsSL --retry 3 https://ipinfo.io/country 2>/dev/null || true)
if [ "$COUNTRY" = "CN" ]; then
    # 拼接 URL
    DOWNLOAD_URL="https://ghfast.top/${DOWNLOAD_URL}"
fi

download_file() {
  local url="$1"
  local output="$2"

  if ! curl -fL --retry 3 --retry-delay 2 -o "$output" "$url"; then
    echo "❌ 下载失败: $url"
    rm -f "$output"
    return 1
  fi

  if [[ ! -s "$output" ]]; then
    echo "❌ 下载文件为空: $output"
    rm -f "$output"
    return 1
  fi
}

# ---------- 多主控 config.json 操作工具 ----------

# 读取 config.json 到内存处理。输出 JSON 文本；文件不存在或非法时输出空对象。
read_config() {
  local CONFIG_FILE="$INSTALL_DIR/config.json"
  if [[ -f "$CONFIG_FILE" ]]; then
    cat "$CONFIG_FILE"
  else
    echo "{}"
  fi
}

# 迁移旧版单主控格式为 servers 数组（幂等）。
# 优先 jq；无 jq 时降级 python3；都无则返回 1 由调用方提示手动处理。
migrate_config() {
  local CONFIG_FILE="$INSTALL_DIR/config.json"
  [[ -f "$CONFIG_FILE" ]] || return 0

  if command -v jq &> /dev/null; then
    if jq -e 'has("servers")' "$CONFIG_FILE" &> /dev/null; then
      return 0
    fi
    local tmp
    tmp=$(jq '{addr: .addr, secret: .secret, http: (.http // 0), tls: (.tls // 0), socks: (.socks // 0),
      servers: (if has("addr") and .addr != "" then [{addr: .addr, secret: (.secret // ""), ns: ""}] else [] end)}' "$CONFIG_FILE")
    [[ $? -eq 0 ]] && echo "$tmp" > "$CONFIG_FILE"
    return $?
  fi

  if command -v python3 &> /dev/null; then
    python3 - "$CONFIG_FILE" <<'PYEOF'
import json, sys
path = sys.argv[1]
try:
    with open(path) as f:
        cfg = json.load(f)
except Exception:
    sys.exit(1)
if "servers" not in cfg:
    servers = []
    if cfg.get("addr"):
        servers.append({"addr": cfg["addr"], "secret": cfg.get("secret", ""), "ns": ""})
    cfg["servers"] = servers
    cfg.setdefault("http", 0)
    cfg.setdefault("tls", 0)
    cfg.setdefault("socks", 0)
    with open(path, "w") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)
sys.exit(0)
PYEOF
    return $?
  fi

  return 1
}

# 列出已接入主控（idx | addr | ns）
list_servers() {
  if command -v jq &> /dev/null; then
    jq -r '.servers | to_entries[] | "\(.key+1)|\(.value.addr)|\(.value.ns // "")"' "$INSTALL_DIR/config.json"
  else
    python3 - "$INSTALL_DIR/config.json" <<'PYEOF'
import json, sys
with open(sys.argv[1]) as f:
    cfg = json.load(f)
for i, s in enumerate(cfg.get("servers", []), 1):
    print(f"{i}|{s.get('addr','')}|{s.get('ns','')}")
PYEOF
  fi
}

# 计算下一个可用 ns：p2、p3…（首个主控 ns 恒为空）
next_ns() {
  if command -v jq &> /dev/null; then
    jq -r '[.servers[] | select((.ns // "") != "") | .ns | ltrimstr("p") | tonumber] | (max // 1) + 1' "$INSTALL_DIR/config.json"
  else
    python3 - "$INSTALL_DIR/config.json" <<'PYEOF'
import json, sys
with open(sys.argv[1]) as f:
    cfg = json.load(f)
nums = [int(s["ns"][1:]) for s in cfg.get("servers", []) if s.get("ns", "").startswith("p") and s["ns"][1:].isdigit()]
print((max(nums) if nums else 1) + 1)
PYEOF
  fi
}

# 按 addr 查找已存在的主控 ns；不存在输出空
find_ns_by_addr() {
  local target="$1"
  list_servers 2>/dev/null | grep -F "|${target}|" | head -n 1 | cut -d '|' -f 3
}

# 添加或更新一个主控（同 addr 更新 secret，否则追加）
upsert_server() {
  local addr="$1" secret="$2" ns="$3"
  local CONFIG_FILE="$INSTALL_DIR/config.json"
  if command -v jq &> /dev/null; then
    local tmp
    tmp=$(jq --arg addr "$addr" --arg secret "$secret" --arg ns "$ns" '
      .servers = ((.servers // [])
        | if any(.[]; .addr == $addr)
          then map(if .addr == $addr then .secret = $secret else . end)
          else . + [{addr: $addr, secret: $secret, ns: $ns}] end)
      | .addr = .servers[0].addr | .secret = .servers[0].secret' "$CONFIG_FILE")
    [[ $? -eq 0 ]] && echo "$tmp" > "$CONFIG_FILE"
    return $?
  fi
  if command -v python3 &> /dev/null; then
    python3 - "$CONFIG_FILE" "$addr" "$secret" "$ns" <<'PYEOF'
import json, sys
path, addr, secret, ns = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(path) as f:
    cfg = json.load(f)
servers = cfg.get("servers", [])
found = False
for s in servers:
    if s.get("addr") == addr:
        s["secret"] = secret
        found = True
        break
if not found:
    servers.append({"addr": addr, "secret": secret, "ns": ns})
cfg["servers"] = servers
cfg["addr"] = servers[0]["addr"]
cfg["secret"] = servers[0]["secret"]
with open(path, "w") as f:
    json.dump(cfg, f, indent=2, ensure_ascii=False)
sys.exit(0)
PYEOF
    return $?
  fi
  return 1
}

# 按 ns 移除主控（ns 为空串表示无前缀首主控）
remove_server_by_ns() {
  local target_ns="$1"
  local CONFIG_FILE="$INSTALL_DIR/config.json"
  if command -v jq &> /dev/null; then
    local tmp
    tmp=$(jq --arg ns "$target_ns" '
      .servers = ((.servers // []) | map(select((.ns // "") != $ns)))
      | if (.servers | length) > 0 then .addr = .servers[0].addr | .secret = .servers[0].secret else . end' "$CONFIG_FILE")
    [[ $? -eq 0 ]] && echo "$tmp" > "$CONFIG_FILE"
    return $?
  fi
  if command -v python3 &> /dev/null; then
    python3 - "$CONFIG_FILE" "$target_ns" <<'PYEOF'
import json, sys
path, target_ns = sys.argv[1], sys.argv[2]
with open(path) as f:
    cfg = json.load(f)
cfg["servers"] = [s for s in cfg.get("servers", []) if s.get("ns", "") != target_ns]
if cfg["servers"]:
    cfg["addr"] = cfg["servers"][0]["addr"]
    cfg["secret"] = cfg["servers"][0]["secret"]
with open(path, "w") as f:
    json.dump(cfg, f, indent=2, ensure_ascii=False)
sys.exit(0)
PYEOF
    return $?
  fi
  return 1
}

# 显示主控列表
show_servers() {
  echo "==============================================="
  echo "              已接入主控"
  echo "==============================================="
  if [[ ! -f "$INSTALL_DIR/config.json" ]]; then
    echo "（尚未安装，无主控配置）"
    return
  fi
  local rows
  rows=$(list_servers 2>/dev/null)
  if [[ -z "$rows" ]]; then
    echo "（无主控记录，请重新执行安装）"
    return
  fi
  while IFS='|' read -r idx addr ns; do
    if [[ -z "$ns" ]]; then
      echo "  [$idx] $addr （默认主控，无前缀）"
    else
      echo "  [$idx] $addr （命名空间: $ns）"
    fi
  done <<< "$rows"
}

# 确保 config 具备多主控结构；jq/python3 都缺失时给出指引
ensure_multi_master_config() {
  mkdir -p "$INSTALL_DIR"
  if ! migrate_config; then
    echo "❌ 系统缺少 jq 和 python3，无法自动迁移多主控配置。"
    echo "   请安装 jq（apt install -y jq 或 yum install -y jq）后重试，"
    echo "   或手动编辑 $INSTALL_DIR/config.json 将 addr/secret 迁移到 servers 数组。"
    return 1
  fi
  if [[ ! -f "$INSTALL_DIR/config.json" ]]; then
    echo '{}' > "$INSTALL_DIR/config.json"
  fi
  return 0
}

# ---------- 原有脚本功能 ----------

# 显示菜单
show_menu() {
  echo "==============================================="
  echo "              管理脚本"
  echo "==============================================="
  echo "请选择操作："
  echo "1. 安装（首个主控）"
  echo "2. 添加主控（多主控接入）"
  echo "3. 更新"
  echo "4. 移除主控"
  echo "5. 查看已接入主控"
  echo "6. 卸载"
  echo "0. 退出"
  echo "==============================================="
}

# 删除脚本自身
delete_self() {
  echo ""
  echo "🗑️ 操作已完成，正在清理脚本文件..."
  SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")"
  sleep 1
  rm -f "$SCRIPT_PATH" && echo "✅ 脚本文件已删除" || echo "❌ 删除脚本文件失败"
}

# 检查并安装 tcpkill
check_and_install_tcpkill() {
  if command -v tcpkill &> /dev/null; then
    return 0
  fi

  OS_TYPE=$(uname -s)

  if [[ $EUID -ne 0 ]]; then
    SUDO_CMD="sudo"
  else
    SUDO_CMD=""
  fi

  if [[ "$OS_TYPE" == "Darwin" ]]; then
    if command -v brew &> /dev/null; then
      brew install dsniff &> /dev/null
    fi
    return 0
  fi

  if [ -f /etc/os-release ]; then
    . /etc/os-release
    DISTRO=$ID
  elif [ -f /etc/redhat-release ]; then
    DISTRO="rhel"
  elif [ -f /etc/debian_version ]; then
    DISTRO="debian"
  else
    return 0
  fi

  case $DISTRO in
    ubuntu|debian)
      $SUDO_CMD apt update &> /dev/null
      $SUDO_CMD apt install -y dsniff &> /dev/null
      ;;
    centos|rhel|fedora)
      if command -v dnf &> /dev/null; then
        $SUDO_CMD dnf install -y dsniff &> /dev/null
      elif command -v yum &> /dev/null; then
        $SUDO_CMD yum install -y dsniff &> /dev/null
      fi
      ;;
    alpine)
      $SUDO_CMD apk add --no-cache dsniff &> /dev/null
      ;;
    arch|manjaro)
      $SUDO_CMD pacman -S --noconfirm dsniff &> /dev/null
      ;;
    opensuse*|sles)
      $SUDO_CMD zypper install -y dsniff &> /dev/null
      ;;
    gentoo)
      $SUDO_CMD emerge --ask=n net-analyzer/dsniff &> /dev/null
      ;;
    void)
      $SUDO_CMD xbps-install -Sy dsniff &> /dev/null
      ;;
  esac

  return 0
}

# 获取用户输入的配置参数
get_config_params() {
  if [[ -z "$SERVER_ADDR" || -z "$SECRET" ]]; then
    echo "请输入配置参数："

    if [[ -z "$SERVER_ADDR" ]]; then
      read -p "主控面板地址（如 1.2.3.4:3000）: " SERVER_ADDR
    fi

    if [[ -z "$SECRET" ]]; then
      read -p "节点密钥: " SECRET
    fi

    if [[ -z "$SERVER_ADDR" || -z "$SECRET" ]]; then
      echo "❌ 参数不完整，操作取消。"
      exit 1
    fi
  fi
}

# 解析命令行参数
while getopts "a:s:" opt; do
  case $opt in
    a) SERVER_ADDR="$OPTARG" ;;
    s) SECRET="$OPTARG" ;;
    *) echo "❌ 无效参数"; exit 1 ;;
  esac
done

# 下载并放置 gost 二进制（安装/更新共用）
fetch_gost_binary() {
  # 停止并禁用已有服务
  if systemctl list-units --full -all | grep -Fq "gost.service"; then
    echo "🔍 检测到已存在的gost服务"
    systemctl stop gost 2>/dev/null && echo "🛑 停止服务"
    systemctl disable gost 2>/dev/null && echo "🚫 禁用自启"
  fi

  # 删除旧文件
  [[ -f "$INSTALL_DIR/gost" ]] && echo "🧹 删除旧文件 gost" && rm -f "$INSTALL_DIR/gost"

  # 下载 gost
  echo "⬇️ 下载 gost 中..."
  if ! download_file "$DOWNLOAD_URL" "$INSTALL_DIR/gost"; then
    return 1
  fi
  chmod +x "$INSTALL_DIR/gost"
  if ! GOST_VERSION=$(cd "$INSTALL_DIR" && ./gost -V 2>&1); then
    echo "❌ 下载的 gost 无法执行：$GOST_VERSION"
    return 1
  fi
  echo "✅ 下载完成"
  echo "🔎 gost 版本：$GOST_VERSION"
  return 0
}

# 创建/刷新 systemd 服务
setup_service() {
  SERVICE_FILE="/etc/systemd/system/gost.service"
  cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Gost Proxy Service
After=network.target

[Service]
WorkingDirectory=$INSTALL_DIR
ExecStart=$INSTALL_DIR/gost
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable gost
  systemctl restart gost

  echo "🔄 检查服务状态..."
  if systemctl is-active --quiet gost; then
    echo "✅ 操作完成，gost服务已启动并设置为开机启动。"
    echo "📁 配置目录: $INSTALL_DIR"
    echo "🔧 服务状态: $(systemctl is-active gost)"
  else
    echo "❌ gost服务启动失败，请执行以下命令查看日志："
    echo "journalctl -u gost -f"
    return 1
  fi
}

# 安装功能（首个主控，或重跑时按 addr 追加/更新）
install_gost() {
  echo "🚀 开始安装 GOST..."
  get_config_params

  check_and_install_tcpkill
  if ! ensure_multi_master_config; then
    exit 1
  fi

  if ! fetch_gost_binary; then
    exit 1
  fi

  # 写入 gost.json
  GOST_CONFIG="$INSTALL_DIR/gost.json"
  if [[ -f "$GOST_CONFIG" ]]; then
    echo "⏭️ 跳过配置文件: gost.json (已存在)"
  else
    echo "📄 创建新配置: gost.json"
    echo "{}" > "$GOST_CONFIG"
  fi
  chmod 600 "$INSTALL_DIR"/*.json

  # 追加或更新主控：同 addr 更新密钥（保留原命名空间）；新 addr 分配下一个命名空间
  local ns
  if [[ -n "$(find_ns_by_addr "$SERVER_ADDR")" ]]; then
    ns=""
    echo "🔁 该主控已接入，更新其密钥"
  elif list_servers 2>/dev/null | grep -q .; then
    ns="p$(next_ns)"
    echo "➕ 追加主控，分配命名空间: $ns"
  else
    ns=""
    echo "➕ 写入默认主控（无前缀）"
  fi
  if ! upsert_server "$SERVER_ADDR" "$SECRET" "$ns"; then
    echo "❌ 写入主控配置失败"
    exit 1
  fi

  if ! setup_service; then
    exit 1
  fi
}

# 添加主控（要求已安装）
add_server() {
  if [[ ! -f "$INSTALL_DIR/gost" ]]; then
    echo "❌ GOST 未安装，请先选择安装。"
    return 1
  fi
  get_config_params

  if ! ensure_multi_master_config; then
    return 1
  fi

  local ns
  ns=$(find_ns_by_addr "$SERVER_ADDR")
  if [[ -n "$ns" ]]; then
    echo "🔁 该主控已接入（ns: ${ns:-默认}），将更新其密钥"
  else
    ns="p$(next_ns)"
    echo "➕ 分配命名空间: $ns"
  fi
  if ! upsert_server "$SERVER_ADDR" "$SECRET" "$ns"; then
    echo "❌ 写入主控配置失败"
    return 1
  fi
  echo "🔍 下载最新版本 gost..."
  if ! download_file "$DOWNLOAD_URL" "$INSTALL_DIR/gost.new"; then
    return 1
  fi
  chmod +x "$INSTALL_DIR/gost.new"
  if GOST_VERSION=$(cd "$INSTALL_DIR" && ./gost.new -V 2>&1); then
    if systemctl list-units --full -all | grep -Fq "gost.service"; then
      systemctl stop gost 2>/dev/null
    fi
    mv "$INSTALL_DIR/gost.new" "$INSTALL_DIR/gost"
    echo "🔎 新版本：$GOST_VERSION"
  else
    echo "⚠️ 新版本下载校验失败，保留现有二进制继续接入主控"
    rm -f "$INSTALL_DIR/gost.new"
  fi
  setup_service
}

# 更新功能
update_gost() {
  echo "🔄 开始更新 GOST..."

  if [[ ! -d "$INSTALL_DIR" ]]; then
    echo "❌ GOST 未安装，请先选择安装。"
    return 1
  fi

  echo "📥 使用下载地址: $DOWNLOAD_URL"

  check_and_install_tcpkill

  echo "⬇️ 下载最新版本..."
  if ! download_file "$DOWNLOAD_URL" "$INSTALL_DIR/gost.new"; then
    return 1
  fi
  chmod +x "$INSTALL_DIR/gost.new"
  if ! GOST_VERSION=$(cd "$INSTALL_DIR" && ./gost.new -V 2>&1); then
    echo "❌ 下载的新版本 gost 无法执行：$GOST_VERSION"
    rm -f "$INSTALL_DIR/gost.new"
    return 1
  fi

  if systemctl list-units --full -all | grep -Fq "gost.service"; then
    echo "🛑 停止 gost 服务..."
    systemctl stop gost
  fi

  mv "$INSTALL_DIR/gost.new" "$INSTALL_DIR/gost"

  echo "🔎 新版本：$GOST_VERSION"

  echo "🔄 重启服务..."
  systemctl start gost

  echo "✅ 更新完成，服务已重新启动。"
}

# 移除主控
remove_server() {
  if [[ ! -f "$INSTALL_DIR/config.json" ]]; then
    echo "❌ 未找到配置文件，请先安装。"
    return 1
  fi
  if ! ensure_multi_master_config; then
    return 1
  fi

  show_servers
  local idx
  read -p "请输入要移除的主控序号: " idx
  local target_ns
  target_ns=$(list_servers | while IFS='|' read -r i addr ns; do
    if [[ "$i" == "$idx" ]]; then
      echo "$ns"
    fi
  done)
  local total
  total=$(list_servers | wc -l)
  if [[ -z "$target_ns" && "$idx" != "1" ]]; then
    echo "❌ 无效序号: $idx"
    return 1
  fi
  if [[ "$total" -le 1 ]]; then
    echo "❌ 至少保留一个主控；如需全部移除请选择卸载。"
    return 1
  fi

  local confirm
  read -p "确认移除该主控吗？其面板下的转发请先在面板侧删除 (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "❌ 取消移除"
    return 0
  fi

  if ! remove_server_by_ns "$target_ns"; then
    echo "❌ 移除失败（缺少 jq/python3？）"
    return 1
  fi
  setup_service
}

# 卸载功能
uninstall_gost() {
  echo "🗑️ 开始卸载 GOST..."

  read -p "确认卸载 GOST 吗？此操作将删除所有相关文件 (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "❌ 取消卸载"
    return 0
  fi

  if systemctl list-units --full -all | grep -Fq "gost.service"; then
    echo "🛑 停止并禁用服务..."
    systemctl stop gost 2>/dev/null
    systemctl disable gost 2>/dev/null
  fi

  if [[ -f "/etc/systemd/system/gost.service" ]]; then
    rm -f "/etc/systemd/system/gost.service"
    echo "🧹 删除服务文件"
  fi

  if [[ -d "$INSTALL_DIR" ]]; then
    rm -rf "$INSTALL_DIR"
    echo "🧹 删除安装目录: $INSTALL_DIR"
  fi

  systemctl daemon-reload

  echo "✅ 卸载完成"
}

# 主逻辑
main() {
  # 如果提供了命令行参数，直接执行安装（重跑 = 追加/更新主控）
  if [[ -n "$SERVER_ADDR" && -n "$SECRET" ]]; then
    install_gost
    delete_self
    exit 0
  fi

  while true; do
    show_menu
    read -p "请输入选项: " choice

    case $choice in
      1)
        install_gost
        delete_self
        exit 0
        ;;
      2)
        add_server
        delete_self
        exit 0
        ;;
      3)
        update_gost
        delete_self
        exit 0
        ;;
      4)
        remove_server
        delete_self
        exit 0
        ;;
      5)
        show_servers
        ;;
      6)
        uninstall_gost
        delete_self
        exit 0
        ;;
      0)
        echo "👋 退出脚本"
        delete_self
        exit 0
        ;;
      *)
        echo "❌ 无效选项，请重新输入"
        echo ""
        ;;
    esac
  done
}

# 执行主函数
main