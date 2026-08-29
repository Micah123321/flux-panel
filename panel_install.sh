#!/bin/bash
set -e

# 解决 macOS 下 tr 可能出现的非法字节序列问题
export LANG=en_US.UTF-8
export LC_ALL=C



# 全局下载地址配置
REPO_OWNER="Micah123321"
REPO_NAME="flux-panel"
RAW_BASE_URL="https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/refs/heads/main"

DOCKER_COMPOSEV4_URL="${RAW_BASE_URL}/docker-compose-v4.yml"
DOCKER_COMPOSEV6_URL="${RAW_BASE_URL}/docker-compose-v6.yml"
GOST_SQL_URL="${RAW_BASE_URL}/gost.sql"

COUNTRY=$(curl -fsSL --retry 3 https://ipinfo.io/country 2>/dev/null || true)
if [ "$COUNTRY" = "CN" ]; then
    # 拼接 URL
    DOCKER_COMPOSEV4_URL="https://ghfast.top/${DOCKER_COMPOSEV4_URL}"
    DOCKER_COMPOSEV6_URL="https://ghfast.top/${DOCKER_COMPOSEV6_URL}"
    GOST_SQL_URL="https://ghfast.top/${GOST_SQL_URL}"
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



# 将 IPv4 地址转换为整数，便于比较 CIDR 区间。
ipv4_to_int() {
  local address="$1"
  local IFS=.
  local first second third fourth extra

  read -r first second third fourth extra <<< "$address"
  if [[ -n "$extra" || -z "$first" || -z "$second" || -z "$third" || -z "$fourth" ]]; then
    return 1
  fi

  local octet
  for octet in "$first" "$second" "$third" "$fourth"; do
    [[ "$octet" =~ ^[0-9]+$ ]] || return 1
    ((10#$octet <= 255)) || return 1
  done

  printf '%s\n' "$(( (10#$first << 24) + (10#$second << 16) + (10#$third << 8) + 10#$fourth ))"
}

# 输出 IPv4 CIDR 的起止整数区间。
ipv4_cidr_to_range() {
  local cidr="$1"
  local address="${cidr%/*}"
  local prefix="${cidr#*/}"

  [[ "$cidr" == */* && "$prefix" =~ ^[0-9]+$ ]] || return 1
  prefix=$((10#$prefix))
  ((prefix >= 0 && prefix <= 32)) || return 1

  local address_int
  address_int=$(ipv4_to_int "$address") || return 1

  local size=$((1 << (32 - prefix)))
  local start=$((address_int & ~(size - 1)))
  local end=$((start + size - 1))
  printf '%s %s\n' "$start" "$end"
}

# 判断两个 IPv4 CIDR 是否有重叠。
ipv4_cidrs_overlap() {
  local first_range second_range
  first_range=$(ipv4_cidr_to_range "$1") || return 1
  second_range=$(ipv4_cidr_to_range "$2") || return 1

  local first_start first_end second_start second_end
  read -r first_start first_end <<< "$first_range"
  read -r second_start second_end <<< "$second_range"
  [[ "$first_start" -le "$second_end" && "$second_start" -le "$first_end" ]]
}

# 获取当前 Docker 网络的 IPv4 子网，忽略 IPv6 与空 IPAM 配置。
get_docker_ipv4_subnets() {
  local network_ids inspected
  network_ids=$(docker network ls -q) || return 1
  [[ -n "$network_ids" ]] || return 0

  inspected=$(docker network inspect $network_ids --format '{{range .IPAM.Config}}{{.Subnet}}{{println}}{{end}}' 2>/dev/null) || return 1
  grep -E '^[0-9]{1,3}(\.[0-9]{1,3}){3}/[0-9]{1,2}$' <<< "$inspected" || true
}

# 返回 0 代表 candidate 未与任何现有 Docker IPv4 子网重叠。
docker_ipv4_subnet_available() {
  local candidate="$1"
  local used_subnets subnet
  used_subnets=$(get_docker_ipv4_subnets) || return 1

  while IFS= read -r subnet; do
    [[ -z "$subnet" ]] && continue
    if ipv4_cidrs_overlap "$candidate" "$subnet"; then
      return 1
    fi
  done <<< "$used_subnets"

  return 0
}

# 从私有地址池选择一个不与现有 Docker 网络重叠的 /16。
select_available_docker_ipv4_subnet() {
  echo "🔍 检测 Docker IPv4 子网冲突..."
  if ! docker network ls >/dev/null 2>&1; then
    echo "❌ 无法读取 Docker 网络，请确认 Docker 服务正在运行且当前用户有权限访问。"
    return 1
  fi

  local second candidate
  for second in {16..31}; do
    candidate="172.${second}.0.0/16"
    if docker_ipv4_subnet_available "$candidate"; then
      DOCKER_IPV4_SUBNET="$candidate"
      echo "✅ 已选择可用 Docker IPv4 子网: $DOCKER_IPV4_SUBNET"
      return 0
    fi
  done

  for second in {240..255}; do
    candidate="10.${second}.0.0/16"
    if docker_ipv4_subnet_available "$candidate"; then
      DOCKER_IPV4_SUBNET="$candidate"
      echo "✅ 已选择可用 Docker IPv4 子网: $DOCKER_IPV4_SUBNET"
      return 0
    fi
  done

  echo "❌ 未找到可用 Docker IPv4 子网，请清理冲突网络或手动指定子网后重试。"
  return 1
}

# 更新时优先复用原子网；子网缺失、非法或冲突时重新选择并持久化。
ensure_docker_ipv4_subnet() {
  local configured_subnet=""
  if [[ -f .env ]]; then
    configured_subnet=$(sed -n 's/^DOCKER_IPV4_SUBNET=//p' .env | tail -n 1)
  fi

  if [[ -n "$configured_subnet" ]] && ipv4_cidr_to_range "$configured_subnet" >/dev/null && docker_ipv4_subnet_available "$configured_subnet"; then
    DOCKER_IPV4_SUBNET="$configured_subnet"
    echo "✅ 复用现有 Docker IPv4 子网: $DOCKER_IPV4_SUBNET"
    return 0
  fi

  if [[ -n "$configured_subnet" ]]; then
    echo "⚠️ 原 Docker IPv4 子网不可用，正在重新选择。"
  fi
  select_available_docker_ipv4_subnet

  if [[ -f .env ]]; then
    local temp_env
    temp_env=$(mktemp .env.XXXXXX)
    if grep -q '^DOCKER_IPV4_SUBNET=' .env; then
      awk -v subnet="$DOCKER_IPV4_SUBNET" '{if ($0 ~ /^DOCKER_IPV4_SUBNET=/) print "DOCKER_IPV4_SUBNET=" subnet; else print}' .env > "$temp_env"
    else
      cat .env > "$temp_env"
      printf '\nDOCKER_IPV4_SUBNET=%s\n' "$DOCKER_IPV4_SUBNET" >> "$temp_env"
    fi
    mv "$temp_env" .env
  fi
}
# 根据IPv6支持情况选择docker-compose URL
get_docker_compose_url() {
  if check_ipv6_support > /dev/null 2>&1; then
    echo "$DOCKER_COMPOSEV6_URL"
  else
    echo "$DOCKER_COMPOSEV4_URL"
  fi
}

# 检查 docker-compose 或 docker compose 命令
check_docker() {
  if command -v docker-compose &> /dev/null; then
    DOCKER_CMD="docker-compose"
  elif command -v docker &> /dev/null; then
    if docker compose version &> /dev/null; then
      DOCKER_CMD="docker compose"
    else
      echo "错误：检测到 docker，但不支持 'docker compose' 命令。请安装 docker-compose 或更新 docker 版本。"
      exit 1
    fi
  else
    echo "错误：未检测到 docker 或 docker-compose 命令。请先安装 Docker。"
    exit 1
  fi
  echo "检测到 Docker 命令：$DOCKER_CMD"
}

# 检测系统是否支持 IPv6
check_ipv6_support() {
  echo "🔍 检测 IPv6 支持..."

  # 检查是否有 IPv6 地址（排除 link-local 地址）
  if ip -6 addr show | grep -v "scope link" | grep -q "inet6"; then
    echo "✅ 检测到系统支持 IPv6"
    return 0
  elif ifconfig 2>/dev/null | grep -v "fe80:" | grep -q "inet6"; then
    echo "✅ 检测到系统支持 IPv6"
    return 0
  else
    echo "⚠️ 未检测到 IPv6 支持"
    return 1
  fi
}



# 配置 Docker 启用 IPv6
configure_docker_ipv6() {
  echo "🔧 配置 Docker IPv6 支持..."

  # 检查操作系统类型
  OS_TYPE=$(uname -s)

  if [[ "$OS_TYPE" == "Darwin" ]]; then
    # macOS 上 Docker Desktop 已默认支持 IPv6
    echo "✅ macOS Docker Desktop 默认支持 IPv6"
    return 0
  fi

  # Docker daemon 配置文件路径
  DOCKER_CONFIG="/etc/docker/daemon.json"

  # 检查是否需要 sudo
  if [[ $EUID -ne 0 ]]; then
    SUDO_CMD="sudo"
  else
    SUDO_CMD=""
  fi

  # 检查 Docker 配置文件
  if [ -f "$DOCKER_CONFIG" ]; then
    # 检查是否已经配置了 IPv6
    if grep -q '"ipv6"' "$DOCKER_CONFIG"; then
      echo "✅ Docker 已配置 IPv6 支持"
    else
      echo "📝 更新 Docker 配置以启用 IPv6..."
      # 备份原配置
      $SUDO_CMD cp "$DOCKER_CONFIG" "${DOCKER_CONFIG}.backup"

      # 使用 jq 或 sed 添加 IPv6 配置
      if command -v jq &> /dev/null; then
        $SUDO_CMD jq '. + {"ipv6": true, "fixed-cidr-v6": "fd00::/80"}' "$DOCKER_CONFIG" > /tmp/daemon.json && $SUDO_CMD mv /tmp/daemon.json "$DOCKER_CONFIG"
      else
        # 如果没有 jq，使用 sed
        $SUDO_CMD sed -i 's/^{$/{\n  "ipv6": true,\n  "fixed-cidr-v6": "fd00::\/80",/' "$DOCKER_CONFIG"
      fi

      echo "🔄 重启 Docker 服务..."
      if command -v systemctl &> /dev/null; then
        $SUDO_CMD systemctl restart docker
      elif command -v service &> /dev/null; then
        $SUDO_CMD service docker restart
      else
        echo "⚠️ 请手动重启 Docker 服务"
      fi
      sleep 5
    fi
  else
    # 创建新的配置文件
    echo "📝 创建 Docker 配置文件..."
    $SUDO_CMD mkdir -p /etc/docker
    echo '{
  "ipv6": true,
  "fixed-cidr-v6": "fd00::/80"
}' | $SUDO_CMD tee "$DOCKER_CONFIG" > /dev/null

    echo "🔄 重启 Docker 服务..."
    if command -v systemctl &> /dev/null; then
      $SUDO_CMD systemctl restart docker
    elif command -v service &> /dev/null; then
      $SUDO_CMD service docker restart
    else
      echo "⚠️ 请手动重启 Docker 服务"
    fi
    sleep 5
  fi
}

# 显示菜单
show_menu() {
  echo "==============================================="
  echo "          面板管理脚本"
  echo "==============================================="
  echo "请选择操作："
  echo "1. 安装面板"
  echo "2. 更新面板"
  echo "3. 卸载面板"
  echo "4. 导出备份"
  echo "5. 退出"
  echo "==============================================="
}

generate_random() {
  LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c16
}

# 删除脚本自身
delete_self() {
  echo ""
  echo "🗑️ 操作已完成，正在清理脚本文件..."
  SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")"
  sleep 1
  rm -f "$SCRIPT_PATH" && echo "✅ 脚本文件已删除" || echo "❌ 删除脚本文件失败"
}



# 获取用户输入的配置参数
get_config_params() {
  echo "🔧 请输入配置参数："



  read -p "前端端口（默认 6366）: " FRONTEND_PORT
  FRONTEND_PORT=${FRONTEND_PORT:-6366}

  read -p "后端端口（默认 6365）: " BACKEND_PORT
  BACKEND_PORT=${BACKEND_PORT:-6365}

  DB_NAME=$(generate_random)
  DB_USER=$(generate_random)
  DB_PASSWORD=$(generate_random)
  JWT_SECRET=$(generate_random)
}

# 安装功能
install_panel() {
  echo "🚀 开始安装面板..."
  check_docker
  get_config_params

  echo "🔽 下载必要文件..."
  DOCKER_COMPOSE_URL=$(get_docker_compose_url)
  echo "📡 选择配置文件：$(basename "$DOCKER_COMPOSE_URL")"
  download_file "$DOCKER_COMPOSE_URL" "docker-compose.yml"

  # 检查 gost.sql 是否已存在
  if [[ -f "gost.sql" ]]; then
    echo "⏭️ 跳过下载: gost.sql (使用当前位置的文件)"
  else
    echo "📡 下载数据库初始化文件..."
    download_file "$GOST_SQL_URL" "gost.sql"
  fi
  echo "✅ 文件准备完成"

  # 自动检测并配置 IPv6 支持
  if check_ipv6_support; then
    echo "🚀 系统支持 IPv6，自动启用 IPv6 配置..."
    configure_docker_ipv6
  fi

  select_available_docker_ipv4_subnet

  cat > .env <<EOF
DB_NAME=$DB_NAME
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD
JWT_SECRET=$JWT_SECRET
FRONTEND_PORT=$FRONTEND_PORT
BACKEND_PORT=$BACKEND_PORT
DOCKER_IPV4_SUBNET=$DOCKER_IPV4_SUBNET
EOF

  echo "🚀 启动 docker 服务..."
  $DOCKER_CMD up -d

  echo "🎉 部署完成"
  echo "🌐 访问地址: http://服务器IP:$FRONTEND_PORT"
  echo "📖 部署完成后请阅读下使用文档，求求了啊，不要上去就是一顿操作"
  echo "📚 文档地址: https://tes.cc/guide.html"
  echo "💡 默认管理员账号: admin_user / admin_user"
  echo "⚠️  登录后请立即修改默认密码！"


}

# 更新功能
update_panel() {
  echo "🔄 开始更新面板..."
  check_docker

  echo "🔽 下载最新配置文件..."
  DOCKER_COMPOSE_URL=$(get_docker_compose_url)
  echo "📡 选择配置文件：$(basename "$DOCKER_COMPOSE_URL")"
  download_file "$DOCKER_COMPOSE_URL" "docker-compose.yml"
  echo "✅ 下载完成"

  # 自动检测并配置 IPv6 支持
  if check_ipv6_support; then
    echo "🚀 系统支持 IPv6，自动启用 IPv6 配置..."
    configure_docker_ipv6
  fi

  echo "🛑 停止当前服务..."
  $DOCKER_CMD down

  ensure_docker_ipv4_subnet

  echo "⬇️ 拉取最新镜像..."
  $DOCKER_CMD pull

  echo "🚀 启动更新后的服务..."
  $DOCKER_CMD up -d

  # 等待服务启动
  echo "⏳ 等待服务启动..."

  # 检查后端容器健康状态
  echo "🔍 检查后端服务状态..."
  for i in {1..90}; do
    if docker ps --format "{{.Names}}" | grep -q "^springboot-backend$"; then
      BACKEND_HEALTH=$(docker inspect -f '{{.State.Health.Status}}' springboot-backend 2>/dev/null || echo "unknown")
      if [[ "$BACKEND_HEALTH" == "healthy" ]]; then
        echo "✅ 后端服务健康检查通过"
        break
      elif [[ "$BACKEND_HEALTH" == "starting" ]]; then
        # 继续等待
        :
      elif [[ "$BACKEND_HEALTH" == "unhealthy" ]]; then
        echo "⚠️ 后端健康状态：$BACKEND_HEALTH"
      fi
    else
      echo "⚠️ 后端容器未找到或未运行"
      BACKEND_HEALTH="not_running"
    fi
    if [ $i -eq 90 ]; then
      echo "❌ 后端服务启动超时（90秒）"
      echo "🔍 当前状态：$(docker inspect -f '{{.State.Health.Status}}' springboot-backend 2>/dev/null || echo '容器不存在')"
      echo "🛑 更新终止"
      return 1
    fi
    # 每15秒显示一次进度
    if [ $((i % 15)) -eq 1 ]; then
      echo "⏳ 等待后端服务启动... ($i/90) 状态：${BACKEND_HEALTH:-unknown}"
    fi
    sleep 1
  done

  # 检查数据库容器健康状态
  echo "🔍 检查数据库服务状态..."
  for i in {1..60}; do
    if docker ps --format "{{.Names}}" | grep -q "^gost-mysql$"; then
      DB_HEALTH=$(docker inspect -f '{{.State.Health.Status}}' gost-mysql 2>/dev/null || echo "unknown")
      if [[ "$DB_HEALTH" == "healthy" ]]; then
        echo "✅ 数据库服务健康检查通过"
        break
      elif [[ "$DB_HEALTH" == "starting" ]]; then
        # 继续等待
        :
      elif [[ "$DB_HEALTH" == "unhealthy" ]]; then
        echo "⚠️ 数据库健康状态：$DB_HEALTH"
      fi
    else
      echo "⚠️ 数据库容器未找到或未运行"
      DB_HEALTH="not_running"
    fi
    if [ $i -eq 60 ]; then
      echo "❌ 数据库服务启动超时（60秒）"
      echo "🔍 当前状态：$(docker inspect -f '{{.State.Health.Status}}' gost-mysql 2>/dev/null || echo '容器不存在')"
      echo "🛑 更新终止"
      return 1
    fi
    # 每10秒显示一次进度
    if [ $((i % 10)) -eq 1 ]; then
      echo "⏳ 等待数据库服务启动... ($i/60) 状态：${DB_HEALTH:-unknown}"
    fi
    sleep 1
  done

  # 从容器环境变量获取数据库信息
  echo "🔍 获取数据库配置信息..."

  # 等待一下让服务完全就绪
  echo "⏳ 等待服务完全就绪..."
  sleep 5

  # 先检查后端容器是否在运行
  if ! docker ps --format "{{.Names}}" | grep -q "^springboot-backend$"; then
    echo "❌ 后端容器未运行，无法获取数据库配置"
    echo "🔍 当前运行的容器："
    docker ps --format "table {{.Names}}\t{{.Status}}"
    echo "🛑 更新终止"
    return 1
  fi

  DB_INFO=$(docker exec springboot-backend env | grep "^DB_" 2>/dev/null || echo "")

  if [[ -n "$DB_INFO" ]]; then
    DB_NAME=$(echo "$DB_INFO" | grep "^DB_NAME=" | cut -d'=' -f2)
    DB_PASSWORD=$(echo "$DB_INFO" | grep "^DB_PASSWORD=" | cut -d'=' -f2)
    DB_USER=$(echo "$DB_INFO" | grep "^DB_USER=" | cut -d'=' -f2)
    DB_HOST=$(echo "$DB_INFO" | grep "^DB_HOST=" | cut -d'=' -f2)

    echo "📋 数据库配置："
    echo "   数据库名: $DB_NAME"
    echo "   用户名: $DB_USER"
    echo "   主机: $DB_HOST"
  else
    echo "❌ 无法获取数据库配置信息"
    echo "🔍 尝试诊断问题："
    echo "   容器状态: $(docker inspect -f '{{.State.Status}}' springboot-backend 2>/dev/null || echo '容器不存在')"
    echo "   健康状态: $(docker inspect -f '{{.State.Health.Status}}' springboot-backend 2>/dev/null || echo '无健康检查')"

    # 尝试从 .env 文件读取配置
    if [[ -f ".env" ]]; then
      echo "🔄 尝试从 .env 文件读取配置..."
      DB_NAME=$(grep "^DB_NAME=" .env | cut -d'=' -f2 2>/dev/null)
      DB_PASSWORD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2 2>/dev/null)
      DB_USER=$(grep "^DB_USER=" .env | cut -d'=' -f2 2>/dev/null)

      if [[ -n "$DB_NAME" && -n "$DB_PASSWORD" && -n "$DB_USER" ]]; then
        echo "✅ 从 .env 文件成功读取数据库配置"
        echo "📋 数据库配置："
        echo "   数据库名: $DB_NAME"
        echo "   用户名: $DB_USER"
      else
        echo "❌ .env 文件中的数据库配置不完整"
        echo "🛑 更新终止"
        return 1
      fi
    else
      echo "❌ 未找到 .env 文件"
      echo "🛑 更新终止"
      return 1
    fi
  fi

  # 检查必要的数据库配置
  if [[ -z "$DB_PASSWORD" || -z "$DB_USER" || -z "$DB_NAME" ]]; then
    echo "❌ 数据库配置不完整（缺少必要参数）"
    echo "🛑 更新终止"
    return 1
  fi

  # 执行数据库字段变更
  echo "🔄 执行数据库结构更新..."

  # 创建临时迁移文件（现在有了数据库信息）
  cat > temp_migration.sql <<EOF
-- 数据库结构更新
USE \`$DB_NAME\`;

-- user 表：删除 name 字段（如果存在）
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'name'
    ),
    'ALTER TABLE \`user\` DROP COLUMN \`name\`;',
    'SELECT "Column \`name\` not exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- node 表：删除 port 字段、添加 server_ip 字段（如果不存在）
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'port'
    ),
    'ALTER TABLE \`node\` DROP COLUMN \`port\`;',
    'SELECT "Column \`port\` not exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'server_ip'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`server_ip\` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;',
    'SELECT "Column \`server_ip\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 将 ip 赋值给 server_ip（如果字段都存在）
UPDATE \`node\`
SET \`server_ip\` = \`ip\`
WHERE \`server_ip\` IS NULL;

-- node 表：修改 ip 字段类型为 longtext
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'ip'
        AND data_type = 'varchar'
    ),
    'ALTER TABLE \`node\` MODIFY COLUMN \`ip\` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;',
    'SELECT "Column \`ip\` not exists or already modified in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- node 表：添加 version 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'version'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`version\` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;',
    'SELECT "Column \`version\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- node 表：添加 port_sta 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'port_sta'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`port_sta\` INT(10) DEFAULT 1000 COMMENT "端口起始范围";',
    'SELECT "Column \`port_sta\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- node 表：添加 port_end 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'port_end'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`port_end\` INT(10) DEFAULT 65535 COMMENT "端口结束范围";',
    'SELECT "Column \`port_end\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有节点设置默认端口范围
UPDATE \`node\`
SET \`port_sta\` = 1000, \`port_end\` = 65535
WHERE \`port_sta\` IS NULL OR \`port_end\` IS NULL;

-- node 表：添加 http、tls、socks 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'http'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`http\` INT(10) DEFAULT 0 COMMENT "HTTP 服务端口";',
    'SELECT "Column \`http\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'tls'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`tls\` INT(10) DEFAULT 0 COMMENT "TLS 服务端口";',
    'SELECT "Column \`tls\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'node'
        AND column_name = 'socks'
    ),
    'ALTER TABLE \`node\` ADD COLUMN \`socks\` INT(10) DEFAULT 0 COMMENT "SOCKS 服务端口";',
    'SELECT "Column \`socks\` already exists in \`node\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有节点设置 http、tls、socks 默认值
UPDATE \`node\`
SET \`http\` = IFNULL(\`http\`, 0),
    \`tls\` = IFNULL(\`tls\`, 0),
    \`socks\` = IFNULL(\`socks\`, 0);

-- tunnel 表：删除废弃字段（如果存在）
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'in_port_sta'
    ),
    'ALTER TABLE \`tunnel\` DROP COLUMN \`in_port_sta\`;',
    'SELECT "Column \`in_port_sta\` not exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'in_port_end'
    ),
    'ALTER TABLE \`tunnel\` DROP COLUMN \`in_port_end\`;',
    'SELECT "Column \`in_port_end\` not exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'out_ip_sta'
    ),
    'ALTER TABLE \`tunnel\` DROP COLUMN \`out_ip_sta\`;',
    'SELECT "Column \`out_ip_sta\` not exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'out_ip_end'
    ),
    'ALTER TABLE \`tunnel\` DROP COLUMN \`out_ip_end\`;',
    'SELECT "Column \`out_ip_end\` not exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- tunnel 表：添加 tcp_listen_addr、udp_listen_addr、protocol（如果不存在）

-- tcp_listen_addr
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'tcp_listen_addr'
    ),
    'ALTER TABLE \`tunnel\` ADD COLUMN \`tcp_listen_addr\` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT "0.0.0.0";',
    'SELECT "Column \`tcp_listen_addr\` already exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- udp_listen_addr
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'udp_listen_addr'
    ),
    'ALTER TABLE \`tunnel\` ADD COLUMN \`udp_listen_addr\` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT "0.0.0.0";',
    'SELECT "Column \`udp_listen_addr\` already exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- protocol
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'protocol'
    ),
    'ALTER TABLE \`tunnel\` ADD COLUMN \`protocol\` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT "tls";',
    'SELECT "Column \`protocol\` already exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- traffic_ratio (流量倍率)
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'traffic_ratio'
    ),
    'ALTER TABLE \`tunnel\` ADD COLUMN \`traffic_ratio\` DECIMAL(5,1) DEFAULT 1.0 COMMENT "流量倍率";',
    'SELECT "Column \`traffic_ratio\` already exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有数据设置默认流量倍率
UPDATE \`tunnel\`
SET \`traffic_ratio\` = 1.0
WHERE \`traffic_ratio\` IS NULL;

-- forward 表：删除 proxy_protocol 字段（如果存在）
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'forward'
        AND column_name = 'proxy_protocol'
    ),
    'ALTER TABLE \`forward\` DROP COLUMN \`proxy_protocol\`;',
    'SELECT "Column \`proxy_protocol\` not exists in \`forward\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- forward 表：修改 remote_addr 字段类型为 longtext
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'forward'
        AND column_name = 'remote_addr'
        AND data_type = 'varchar'
    ),
    'ALTER TABLE \`forward\` MODIFY COLUMN \`remote_addr\` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL;',
    'SELECT "Column \`remote_addr\` not exists or already modified in \`forward\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- forward 表：添加 strategy 字段（负载均衡策略）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'forward'
        AND column_name = 'strategy'
    ),
    'ALTER TABLE \`forward\` ADD COLUMN \`strategy\` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT "fifo" COMMENT "负载均衡策略";',
    'SELECT "Column \`strategy\` already exists in \`forward\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有数据设置默认负载均衡策略
UPDATE \`forward\`
SET \`strategy\` = 'fifo'
WHERE \`strategy\` IS NULL;

-- forward 表：添加 inx 字段（排序索引）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'forward'
        AND column_name = 'inx'
    ),
    'ALTER TABLE \`forward\` ADD COLUMN \`inx\` INT(10) DEFAULT 0 COMMENT "排序索引";',
    'SELECT "Column \`inx\` already exists in \`forward\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有数据设置默认排序索引
UPDATE \`forward\`
SET \`inx\` = 0
WHERE \`inx\` IS NULL;

-- tunnel 表：添加 interface_name 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'tunnel'
        AND column_name = 'interface_name'
    ),
    'ALTER TABLE \`tunnel\` ADD COLUMN \`interface_name\` VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;',
    'SELECT "Column \`interface_name\` already exists in \`tunnel\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- forward 表：添加 interface_name 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'forward'
        AND column_name = 'interface_name'
    ),
    'ALTER TABLE \`forward\` ADD COLUMN \`interface_name\` VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;',
    'SELECT "Column \`interface_name\` already exists in \`forward\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 创建 vite_config 表（如果不存在）
CREATE TABLE IF NOT EXISTS \`vite_config\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`name\` varchar(200) NOT NULL,
  \`value\` varchar(200) NOT NULL,
  \`time\` bigint(20) NOT NULL,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`unique_name\` (\`name\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 创建 statistics_flow 表（如果不存在）
CREATE TABLE IF NOT EXISTS \`statistics_flow\` (
  \`id\` bigint(20) NOT NULL AUTO_INCREMENT,
  \`user_id\` int(10) NOT NULL,
  \`flow\` bigint(20) NOT NULL,
  \`total_flow\` bigint(20) NOT NULL,
  \`time\` varchar(100) NOT NULL,
  \`created_time\` bigint(20) NOT NULL,
  PRIMARY KEY (\`id\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- statistics_flow 表：添加 created_time 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'statistics_flow'
        AND column_name = 'created_time'
    ),
    'ALTER TABLE \`statistics_flow\` ADD COLUMN \`created_time\` BIGINT(20) NOT NULL DEFAULT 0 COMMENT "创建时间毫秒时间戳";',
    'SELECT "Column \`created_time\` already exists in \`statistics_flow\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有记录设置当前毫秒时间戳（仅当 created_time 为 0 或 NULL 时）
UPDATE \`statistics_flow\`
SET \`created_time\` = UNIX_TIMESTAMP() * 1000
WHERE \`created_time\` = 0 OR \`created_time\` IS NULL;

-- 商业化功能：用户扩展字段与邀请唯一索引
-- user 表：添加 package_plan_id 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'package_plan_id'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`package_plan_id\` INT(10) DEFAULT NULL COMMENT "当前套餐ID";',
    'SELECT "Column \`package_plan_id\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 user_group_id 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'user_group_id'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`user_group_id\` INT(10) DEFAULT NULL COMMENT "当前用户组ID";',
    'SELECT "Column \`user_group_id\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 speed_mbps 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'speed_mbps'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`speed_mbps\` INT(10) NOT NULL DEFAULT 0 COMMENT "用户限速Mbps";',
    'SELECT "Column \`speed_mbps\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 ip_limit 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'ip_limit'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`ip_limit\` INT(10) NOT NULL DEFAULT 0 COMMENT "用户IP限制";',
    'SELECT "Column \`ip_limit\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 connection_limit 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'connection_limit'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`connection_limit\` INT(10) NOT NULL DEFAULT 0 COMMENT "用户连接数限制";',
    'SELECT "Column \`connection_limit\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 invite_code 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'invite_code'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`invite_code\` VARCHAR(32) DEFAULT NULL COMMENT "用户邀请码";',
    'SELECT "Column \`invite_code\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 inviter_user_id 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'inviter_user_id'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`inviter_user_id\` INT(10) DEFAULT NULL COMMENT "邀请人用户ID";',
    'SELECT "Column \`inviter_user_id\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user 表：添加 invite_balance 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND column_name = 'invite_balance'
    ),
    'ALTER TABLE \`user\` ADD COLUMN \`invite_balance\` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT "邀请返现余额";',
    'SELECT "Column \`invite_balance\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE table_schema = DATABASE()
        AND table_name = 'user'
        AND index_name = 'invite_code'
    ),
    'ALTER TABLE \`user\` ADD UNIQUE KEY \`invite_code\` (\`invite_code\`);',
    'SELECT "Index \`invite_code\` already exists in \`user\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 聚合转发功能：节点组和转发表
CREATE TABLE IF NOT EXISTS \`aggregate_node_group\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`name\` varchar(100) NOT NULL,
  \`node_ids\` text NOT NULL,
  \`remark\` varchar(500) DEFAULT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) NOT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`)
);

CREATE TABLE IF NOT EXISTS \`aggregate_forward\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`name\` varchar(100) NOT NULL,
  \`entry_group_id\` int(10) NOT NULL,
  \`exit_group_id\` int(10) NOT NULL,
  \`entry_addresses\` text NOT NULL,
  \`entry_port_start\` int(10) NOT NULL,
  \`entry_port_end\` int(10) NOT NULL,
  \`target_port_start\` int(10) NOT NULL,
  \`target_port_end\` int(10) NOT NULL,
  \`mode\` varchar(30) NOT NULL DEFAULT 'load_balance',
  \`traffic_ratio\` decimal(10,1) NOT NULL DEFAULT 1.0,
  \`in_flow\` bigint(20) NOT NULL DEFAULT 0,
  \`out_flow\` bigint(20) NOT NULL DEFAULT 0,
  \`interface_name\` varchar(200) DEFAULT NULL,
  \`remark\` varchar(500) DEFAULT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) NOT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`)
);

-- 商业化功能：套餐、设备组、用户组、订单、兑换码和邀请返现表
CREATE TABLE IF NOT EXISTS \`package_plan\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`name\` varchar(100) NOT NULL,
  \`hidden\` int(10) NOT NULL DEFAULT 0,
  \`price\` decimal(10,2) NOT NULL DEFAULT 0.00,
  \`type\` int(10) NOT NULL DEFAULT 1,
  \`duration_multiplier\` int(10) NOT NULL DEFAULT 1,
  \`user_group_id\` int(10) DEFAULT NULL,
  \`flow\` bigint(20) NOT NULL DEFAULT 0,
  \`max_rules\` int(10) NOT NULL DEFAULT 0,
  \`speed_mbps\` int(10) NOT NULL DEFAULT 0,
  \`ip_limit\` int(10) NOT NULL DEFAULT 0,
  \`connection_limit\` int(10) NOT NULL DEFAULT 0,
  \`description\` longtext,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`)
);

CREATE TABLE IF NOT EXISTS \`device_group\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`name\` varchar(100) NOT NULL,
  \`tunnel_ids\` longtext,
  \`description\` longtext,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`)
);

CREATE TABLE IF NOT EXISTS \`user_group\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`name\` varchar(100) NOT NULL,
  \`description\` longtext,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`)
);

CREATE TABLE IF NOT EXISTS \`user_group_device_group\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`user_group_id\` int(10) NOT NULL,
  \`device_group_id\` int(10) NOT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`user_device_group\` (\`user_group_id\`,\`device_group_id\`)
);

CREATE TABLE IF NOT EXISTS \`order_record\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`order_no\` varchar(64) NOT NULL,
  \`user_id\` int(10) NOT NULL,
  \`package_plan_id\` int(10) NOT NULL,
  \`package_name\` varchar(100) NOT NULL,
  \`original_amount\` decimal(10,2) NOT NULL DEFAULT 0.00,
  \`discount_ratio\` int(10) NOT NULL DEFAULT 100,
  \`payable_amount\` decimal(10,2) NOT NULL DEFAULT 0.00,
  \`status\` int(10) NOT NULL DEFAULT 0,
  \`payment_channel\` varchar(32) DEFAULT NULL,
  \`provider_trade_no\` varchar(128) DEFAULT NULL,
  \`payment_url\` longtext,
  \`paid_amount\` decimal(10,2) NOT NULL DEFAULT 0.00,
  \`redeem_code_id\` int(10) DEFAULT NULL,
  \`inviter_user_id\` int(10) DEFAULT NULL,
  \`reward_ratio\` int(10) NOT NULL DEFAULT 0,
  \`reward_amount\` decimal(10,2) NOT NULL DEFAULT 0.00,
  \`completed_time\` bigint(20) DEFAULT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`order_no\` (\`order_no\`),
  KEY \`provider_trade_no\` (\`provider_trade_no\`)
);

CREATE TABLE IF NOT EXISTS \`redeem_code\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`package_plan_id\` int(10) NOT NULL,
  \`package_name\` varchar(100) NOT NULL,
  \`discount_ratio\` int(10) NOT NULL DEFAULT 100,
  \`total_times\` int(10) NOT NULL DEFAULT 1,
  \`used_times\` int(10) NOT NULL DEFAULT 0,
  \`code\` varchar(64) NOT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`code\` (\`code\`)
);

CREATE TABLE IF NOT EXISTS \`invite_record\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`inviter_user_id\` int(10) NOT NULL,
  \`invitee_user_id\` int(10) NOT NULL,
  \`invite_code\` varchar(32) NOT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`invitee_user_id\` (\`invitee_user_id\`)
);

CREATE TABLE IF NOT EXISTS \`invite_reward_record\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`order_id\` int(10) NOT NULL,
  \`inviter_user_id\` int(10) NOT NULL,
  \`invitee_user_id\` int(10) NOT NULL,
  \`reward_amount\` decimal(10,2) NOT NULL DEFAULT 0.00,
  \`ratio\` int(10) NOT NULL DEFAULT 0,
  \`type\` int(10) NOT NULL DEFAULT 1,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`)
);


-- order_record 表：添加 payment_channel 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'order_record'
        AND column_name = 'payment_channel'
    ),
    'ALTER TABLE \`order_record\` ADD COLUMN \`payment_channel\` VARCHAR(32) DEFAULT NULL COMMENT "支付渠道";',
    'SELECT "Column \`payment_channel\` already exists in \`order_record\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- order_record 表：添加 provider_trade_no 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'order_record'
        AND column_name = 'provider_trade_no'
    ),
    'ALTER TABLE \`order_record\` ADD COLUMN \`provider_trade_no\` VARCHAR(128) DEFAULT NULL COMMENT "支付平台流水号";',
    'SELECT "Column \`provider_trade_no\` already exists in \`order_record\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- order_record 表：添加 payment_url 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'order_record'
        AND column_name = 'payment_url'
    ),
    'ALTER TABLE \`order_record\` ADD COLUMN \`payment_url\` LONGTEXT COMMENT "支付链接或二维码内容";',
    'SELECT "Column \`payment_url\` already exists in \`order_record\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- order_record 表：添加 paid_amount 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'order_record'
        AND column_name = 'paid_amount'
    ),
    'ALTER TABLE \`order_record\` ADD COLUMN \`paid_amount\` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT "实付金额";',
    'SELECT "Column \`paid_amount\` already exists in \`order_record\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- order_record 表：添加 invite_deduction 字段（如果不存在）
SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE table_schema = DATABASE()
        AND table_name = 'order_record'
        AND column_name = 'invite_deduction'
    ),
    'ALTER TABLE \`order_record\` ADD COLUMN \`invite_deduction\` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT "邀请余额抵扣金额";',
    'SELECT "Column \`invite_deduction\` already exists in \`order_record\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE table_schema = DATABASE()
        AND table_name = 'order_record'
        AND index_name = 'provider_trade_no'
    ),
    'ALTER TABLE \`order_record\` ADD KEY \`provider_trade_no\` (\`provider_trade_no\`);',
    'SELECT "Index \`provider_trade_no\` already exists in \`order_record\`";'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS \`payment_config\` (
  \`id\` int(10) NOT NULL AUTO_INCREMENT,
  \`channel\` varchar(32) NOT NULL,
  \`display_name\` varchar(100) NOT NULL,
  \`enabled\` tinyint(1) NOT NULL DEFAULT 0,
  \`pay_type\` varchar(32) DEFAULT NULL,
  \`gateway_url\` varchar(500) DEFAULT NULL,
  \`app_id\` varchar(200) DEFAULT NULL,
  \`merchant_id\` varchar(200) DEFAULT NULL,
  \`secret_key\` longtext,
  \`private_key\` longtext,
  \`public_key\` longtext,
  \`api_key\` longtext,
  \`endpoint_secret\` longtext,
  \`serial_no\` varchar(200) DEFAULT NULL,
  \`notify_url\` varchar(500) DEFAULT NULL,
  \`return_url\` varchar(500) DEFAULT NULL,
  \`cancel_url\` varchar(500) DEFAULT NULL,
  \`currency\` varchar(16) DEFAULT NULL,
  \`created_time\` bigint(20) NOT NULL,
  \`updated_time\` bigint(20) DEFAULT NULL,
  \`status\` int(10) NOT NULL DEFAULT 1,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`channel\` (\`channel\`)
);
INSERT INTO \`vite_config\` (\`name\`, \`value\`, \`time\`) VALUES
  ('invite_ratio', '0', UNIX_TIMESTAMP() * 1000),
  ('invite_renewal_ratio', '0', UNIX_TIMESTAMP() * 1000)
ON DUPLICATE KEY UPDATE \`time\` = \`time\`;

EOF

  # 检查数据库容器
  if ! docker ps --format "{{.Names}}" | grep -q "^gost-mysql$"; then
    echo "❌ 数据库容器 gost-mysql 未运行"
    echo "🔍 当前运行的容器："
    docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
    echo "❌ 数据库结构更新失败，请手动执行 temp_migration.sql"
    echo "📁 迁移文件已保存为 temp_migration.sql"
    return 1
  fi

  # 执行数据库迁移
  if docker exec -i gost-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" < temp_migration.sql 2>/dev/null; then
    echo "✅ 数据库结构更新完成"
  else
    echo "⚠️ 使用用户密码失败，尝试root密码..."
    if docker exec -i gost-mysql mysql -u root -p"$DB_PASSWORD" < temp_migration.sql 2>/dev/null; then
      echo "✅ 数据库结构更新完成"
    else
      echo "❌ 数据库结构更新失败，请手动执行 temp_migration.sql"
      echo "📁 迁移文件已保存为 temp_migration.sql"
      echo "🔍 数据库容器状态: $(docker inspect -f '{{.State.Status}}' gost-mysql 2>/dev/null || echo '容器不存在')"
      echo "🛑 更新终止"
      return 1
    fi
  fi

  # 清理临时文件
  rm -f temp_migration.sql

  echo "✅ 更新完成"
}

# 导出数据库备份
export_migration_sql() {
  echo "📄 开始导出数据库备份..."

  # 获取数据库配置信息
  echo "🔍 获取数据库配置信息..."

  # 先检查后端容器是否在运行
  if ! docker ps --format "{{.Names}}" | grep -q "^springboot-backend$"; then
    echo "❌ 后端容器未运行，尝试从 .env 文件读取配置..."

    # 从 .env 文件读取配置
    if [[ -f ".env" ]]; then
      DB_NAME=$(grep "^DB_NAME=" .env | cut -d'=' -f2 2>/dev/null)
      DB_PASSWORD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2 2>/dev/null)
      DB_USER=$(grep "^DB_USER=" .env | cut -d'=' -f2 2>/dev/null)

      if [[ -n "$DB_NAME" && -n "$DB_PASSWORD" && -n "$DB_USER" ]]; then
        echo "✅ 从 .env 文件读取数据库配置成功"
      else
        echo "❌ .env 文件中的数据库配置不完整"
        return 1
      fi
    else
      echo "❌ 未找到 .env 文件"
      return 1
    fi
  else
    # 从容器环境变量获取数据库信息
    DB_INFO=$(docker exec springboot-backend env | grep "^DB_" 2>/dev/null || echo "")

    if [[ -n "$DB_INFO" ]]; then
      DB_NAME=$(echo "$DB_INFO" | grep "^DB_NAME=" | cut -d'=' -f2)
      DB_PASSWORD=$(echo "$DB_INFO" | grep "^DB_PASSWORD=" | cut -d'=' -f2)
      DB_USER=$(echo "$DB_INFO" | grep "^DB_USER=" | cut -d'=' -f2)

      echo "✅ 从容器环境变量读取数据库配置成功"
    else
      echo "❌ 无法从容器获取数据库配置，尝试从 .env 文件读取..."

      if [[ -f ".env" ]]; then
        DB_NAME=$(grep "^DB_NAME=" .env | cut -d'=' -f2 2>/dev/null)
        DB_PASSWORD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2 2>/dev/null)
        DB_USER=$(grep "^DB_USER=" .env | cut -d'=' -f2 2>/dev/null)

        if [[ -n "$DB_NAME" && -n "$DB_PASSWORD" && -n "$DB_USER" ]]; then
          echo "✅ 从 .env 文件读取数据库配置成功"
        else
          echo "❌ .env 文件中的数据库配置不完整"
          return 1
        fi
      else
        echo "❌ 未找到 .env 文件"
        return 1
      fi
    fi
  fi

  # 检查必要的数据库配置
  if [[ -z "$DB_PASSWORD" || -z "$DB_USER" || -z "$DB_NAME" ]]; then
    echo "❌ 数据库配置不完整（缺少必要参数）"
    return 1
  fi

  echo "📋 数据库配置："
  echo "   数据库名: $DB_NAME"
  echo "   用户名: $DB_USER"

  # 检查数据库容器是否运行
  if ! docker ps --format "{{.Names}}" | grep -q "^gost-mysql$"; then
    echo "❌ 数据库容器未运行，无法导出数据"
    echo "🔍 当前运行的容器："
    docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
    return 1
  fi

  # 生成数据库备份文件
  SQL_FILE="database_backup_$(date +%Y%m%d_%H%M%S).sql"
  echo "📝 导出数据库备份: $SQL_FILE"

  # 使用 mysqldump 导出数据库
  echo "⏳ 正在导出数据库..."
  if docker exec gost-mysql mysqldump -u "$DB_USER" -p"$DB_PASSWORD" --single-transaction --routines --triggers "$DB_NAME" > "$SQL_FILE" 2>/dev/null; then
    echo "✅ 数据库导出成功"
  else
    echo "⚠️ 使用用户密码失败，尝试root密码..."
    if docker exec gost-mysql mysqldump -u root -p"$DB_PASSWORD" --single-transaction --routines --triggers "$DB_NAME" > "$SQL_FILE" 2>/dev/null; then
      echo "✅ 数据库导出成功"
    else
      echo "❌ 数据库导出失败"
      rm -f "$SQL_FILE"
      return 1
    fi
  fi

  # 检查文件大小
  if [[ -f "$SQL_FILE" ]] && [[ -s "$SQL_FILE" ]]; then
    FILE_SIZE=$(du -h "$SQL_FILE" | cut -f1)
    echo "📁 文件位置: $(pwd)/$SQL_FILE"
    echo "📊 文件大小: $FILE_SIZE"
  else
    echo "❌ 导出的文件为空或不存在"
    rm -f "$SQL_FILE"
    return 1
  fi
}


# 卸载功能
uninstall_panel() {
  echo "🗑️ 开始卸载面板..."
  check_docker

  if [[ ! -f "docker-compose.yml" ]]; then
    echo "⚠️ 未找到 docker-compose.yml 文件，正在下载以完成卸载..."
    DOCKER_COMPOSE_URL=$(get_docker_compose_url)
    echo "📡 选择配置文件：$(basename "$DOCKER_COMPOSE_URL")"
    download_file "$DOCKER_COMPOSE_URL" "docker-compose.yml"
    echo "✅ docker-compose.yml 下载完成"
  fi

  read -p "确认卸载面板吗？此操作将停止并删除所有容器和数据 (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "❌ 取消卸载"
    return 0
  fi

  echo "🛑 停止并删除容器、镜像、卷..."
  $DOCKER_CMD down --rmi all --volumes --remove-orphans
  echo "🧹 删除配置文件..."
  rm -f docker-compose.yml gost.sql .env
  echo "✅ 卸载完成"
}

# 主逻辑
main() {

  # 显示交互式菜单
  while true; do
    show_menu
    read -p "请输入选项 (1-5): " choice

    case $choice in
      1)
        install_panel
        delete_self
        exit 0
        ;;
      2)
        update_panel
        delete_self
        exit 0
        ;;
      3)
        uninstall_panel
        delete_self
        exit 0
        ;;
      4)
        export_migration_sql
        delete_self
        exit 0
        ;;
      5)
        echo "👋 退出脚本"
        delete_self
        exit 0
        ;;
      *)
        echo "❌ 无效选项，请输入 1-5"
        echo ""
        ;;
    esac
  done
}

# 仅在直接执行时进入交互菜单，便于脚本函数自检。
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main
fi
