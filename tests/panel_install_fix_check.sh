#!/bin/bash
# panel_install.sh 更新修复自检：.env 加载/校验、项目名推导、残留容器清理。
set -e
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"

bash -n "$REPO_DIR/panel_install.sh" && echo "PASS: bash -n syntax"

UPDATE_BLOCK="$(sed -n '/^update_panel()/,/^}/p' "$REPO_DIR/panel_install.sh")"
MYSQL_LINE=$(echo "$UPDATE_BLOCK" | grep -n 'up -d --remove-orphans mysql' | head -n1 | cut -d: -f1)
MIGRATION_LINE=$(echo "$UPDATE_BLOCK" | grep -n '执行数据库结构更新' | head -n1 | cut -d: -f1)
BACKEND_LINE=$(echo "$UPDATE_BLOCK" | grep -n 'up -d --remove-orphans backend frontend' | head -n1 | cut -d: -f1)
if [[ -n "$MYSQL_LINE" && -n "$MIGRATION_LINE" && -n "$BACKEND_LINE" && $MYSQL_LINE -lt $MIGRATION_LINE && $MIGRATION_LINE -lt $BACKEND_LINE ]]; then
  echo "PASS: update_panel migrates before backend start"
else
  echo "FAIL: update_panel migration order mysql=$MYSQL_LINE migration=$MIGRATION_LINE backend=$BACKEND_LINE"
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
cd "$TMP_DIR"

cat > .env <<'EOF'
DB_NAME=nexusdb
DB_USER=nexususer
DB_PASSWORD=nexuspw
JWT_SECRET=nexusjwt
FRONTEND_PORT=6366
BACKEND_PORT=6365
DOCKER_IPV4_SUBNET=172.21.0.0/16
EOF

# docker 桩：gost-mysql/vite-frontend 归属异项目（残留容器），springboot-backend 不存在。
docker() {
  local sub="$1"; shift
  case "$sub" in
    inspect)
      local name="${@: -1}"
      case "$name" in
        gost-mysql) echo "old-project" ;;
        vite-frontend) echo "nexus-terminal" ;;
        *) return 1 ;;
      esac ;;
    *) return 0 ;;
  esac
}

# shellcheck disable=SC1091
source "$REPO_DIR/panel_install.sh"

# 1) load_env_file 读取 .env 并导出
load_env_file
[[ "$DB_NAME" == "nexusdb" && "$FRONTEND_PORT" == "6366" ]] \
  && echo "PASS: load_env_file" || { echo "FAIL: load_env_file"; exit 1; }

# 2) validate_env 配置齐全时通过
validate_env && echo "PASS: validate_env complete" || { echo "FAIL: validate_env"; exit 1; }

# 3) validate_env 缺项时报错
unset FRONTEND_PORT
if validate_env 2>/dev/null; then echo "FAIL: should detect missing"; exit 1; \
else echo "PASS: validate_env detects missing FRONTEND_PORT"; fi

# 4) compose_project_name：固定目录名 + 硬编码期望（t-EST_9x -> test9x，验证小写化与非法字符剔除）
mkdir "$TMP_DIR/t-EST_9x"
cd "$TMP_DIR/t-EST_9x"
NAME="$(compose_project_name)"
[[ "$NAME" == "test9x" ]] \
  && echo "PASS: compose_project_name -> $NAME" || { echo "FAIL: got $NAME want test9x"; exit 1; }
cd "$TMP_DIR"

# 5) remove_stale_containers 只清理异项目同名容器
OUT="$(remove_stale_containers)"
echo "$OUT" | grep -q "gost-mysql" && echo "$OUT" | grep -q "vite-frontend" \
    && ! echo "$OUT" | grep -q "springboot-backend" \
    && echo "PASS: remove_stale_containers" || { echo "FAIL: remove_stale_containers: $OUT"; exit 1; }

echo "ALL PASS"