#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/install.sh"
[[ "$INSTALL_DIR" == /etc/mi-flux-node ]] || { echo 'wrong default install directory' >&2; exit 1; }
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
fail() { echo "$*" >&2; exit 1; }

reset_fixture() {
  NODE_INSTALL_DIR="$TEST_DIR/$1/mi-flux-node"
  LEGACY_INSTALL_DIR="$TEST_DIR/$1/gost"
  INSTALL_DIR="$NODE_INSTALL_DIR"
  SERVICE_DIR="$TEST_DIR/$1/systemd"
  DIRECTORY_MIGRATED=false
  mkdir -p "$SERVICE_DIR"
  SYSTEMCTL_LOG="$TEST_DIR/$1/systemctl.log"
  CURL_LOG="$TEST_DIR/$1/curl.log"
  : > "$SYSTEMCTL_LOG"
  : > "$CURL_LOG"
  CURL_MODE=ok
  FAIL_NEW_RESTART=false
}

systemctl() {
  printf '%s\n' "$*" >> "$SYSTEMCTL_LOG"
  if [[ "$1 ${2:-}" == "restart mi-flux-node" && "$FAIL_NEW_RESTART" == true ]]; then
    FAIL_NEW_RESTART=false
    return 1
  fi
  return 0
}
curl() {
  local output="" url=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -o) output="$2"; shift 2 ;;
      *) url="$1"; shift ;;
    esac
  done
  printf '%s\n' "$url" >> "$CURL_LOG"
  [[ "$CURL_MODE" != failed ]] || return 22
  if [[ "$CURL_MODE" == invalid ]]; then
    printf '#!/bin/bash\nexit 1\n' > "$output"
  else
    printf '#!/bin/bash\nprintf "mi-flux-node fixture\\n"\n' > "$output"
  fi
}
check_and_install_tcpkill() { :; }

create_legacy_node() {
  local name="$1"
  mkdir -p "$LEGACY_INSTALL_DIR/subdir"
  printf '#!/bin/bash\necho legacy-fixture\n' > "$LEGACY_INSTALL_DIR/$name"
  chmod +x "$LEGACY_INSTALL_DIR/$name"
  printf '[Service]\nWorkingDirectory=%s\nExecStart=%s/%s\n' "$LEGACY_INSTALL_DIR" "$LEGACY_INSTALL_DIR" "$name" > "$SERVICE_DIR/$name.service"
  printf '{"servers":[{"addr":"panel.example:3000","secret":"fixture","ns":""},{"addr":"second.example:3000","secret":"fixture2","ns":"p2"}]}\n' > "$LEGACY_INSTALL_DIR/config.json"
  printf '{"services":[{"name":"existing-forward"}]}\n' > "$LEGACY_INSTALL_DIR/gost.json"
  printf 'preserved extra file\n' > "$LEGACY_INSTALL_DIR/subdir/state"
  cp "$LEGACY_INSTALL_DIR/config.json" "$TEST_DIR/config.before"
  cp "$LEGACY_INSTALL_DIR/gost.json" "$TEST_DIR/gost.before"
}

assert_new_node() {
  [[ "$INSTALL_DIR" == "$NODE_INSTALL_DIR" && -x "$NODE_INSTALL_DIR/$SERVICE_NAME" ]] || fail 'new node path missing'
  [[ ! -e "$LEGACY_INSTALL_DIR" ]] || fail 'old directory remains'
  grep -Fxq "WorkingDirectory=$NODE_INSTALL_DIR" "$SERVICE_DIR/$SERVICE_NAME.service" || fail 'wrong working directory'
  grep -Fxq "ExecStart=$NODE_INSTALL_DIR/$SERVICE_NAME" "$SERVICE_DIR/$SERVICE_NAME.service" || fail 'wrong executable path'
}
assert_configs_preserved() {
  cmp "$INSTALL_DIR/config.json" "$TEST_DIR/config.before"
  cmp "$INSTALL_DIR/gost.json" "$TEST_DIR/gost.before"
  grep -Fxq 'preserved extra file' "$INSTALL_DIR/subdir/state"
}

# Both the raw script and Release downloads follow the selected mode.
DIRECT_URL="$DOWNLOAD_URL"
[[ $(resolve_download_url "$DIRECT_URL") == "$DIRECT_URL" ]] || fail 'default mode is not direct'
parse_args -a '[2001:db8::1]:3000' -s fixture-secret -c
[[ "$SERVER_ADDR" == '[2001:db8::1]:3000' && "$SECRET" == fixture-secret && "$DOMESTIC_MODE" == true ]] || fail 'install arguments changed'
for url in "$DIRECT_URL" 'https://raw.githubusercontent.com/owner/repo/main/install.sh' 'https://api.github.com/repos/owner/repo/releases/latest'; do
  [[ $(resolve_download_url "$url") == "$GITHUB_PROXY$url" ]] || fail 'GitHub URL was not proxied'
done
[[ $(resolve_download_url "$GITHUB_PROXY$DIRECT_URL") == "$GITHUB_PROXY$DIRECT_URL" ]] || fail 'proxy applied twice'
[[ $(resolve_download_url 'https://example.com/file') == 'https://example.com/file' ]] || fail 'non-GitHub URL changed'

# A fresh installation never creates the old directory.
reset_fixture fresh
select_install_dir
ensure_multi_master_config
fetch_node_binary
setup_service
assert_new_node
uninstall_node <<< y
[[ ! -d "$NODE_INSTALL_DIR" ]] || fail 'fresh node uninstall left files behind'

# An original gost node keeps working when downloads fail, then migrates all files.
reset_fixture legacy-gost
create_legacy_node gost
select_install_dir
for CURL_MODE in failed invalid; do
  if fetch_node_binary; then fail "bad download succeeded: $CURL_MODE"; fi
  [[ -x "$LEGACY_INSTALL_DIR/gost" && ! -e "$NODE_INSTALL_DIR" ]] || fail 'download failure moved the old node'
  [[ -f "$SERVICE_DIR/gost.service" && ! -s "$SYSTEMCTL_LOG" ]] || fail 'download failure stopped the old node'
done
CURL_MODE=ok
fetch_node_binary
setup_service
assert_new_node
assert_configs_preserved
[[ ! -e "$SERVICE_DIR/gost.service" && ! -e "$INSTALL_DIR/gost" ]] || fail 'legacy application remains'
grep -Fxq "$GITHUB_PROXY$DIRECT_URL" "$CURL_LOG" || fail 'binary download bypassed proxy'
update_node
assert_new_node
assert_configs_preserved

# Both old service names recover their original paths and binaries on startup failure.
for previous_service in gost mi-flux-node; do
  reset_fixture "rollback-$previous_service"
  create_legacy_node "$previous_service"
  cp "$LEGACY_INSTALL_DIR/$previous_service" "$TEST_DIR/binary.before"
  select_install_dir
  FAIL_NEW_RESTART=true
  fetch_node_binary
  if setup_service; then fail 'failed startup was reported as successful'; fi
  [[ "$INSTALL_DIR" == "$LEGACY_INSTALL_DIR" && ! -e "$NODE_INSTALL_DIR" ]] || fail 'old directory was not restored'
  cmp "$LEGACY_INSTALL_DIR/$previous_service" "$TEST_DIR/binary.before"
  grep -Fxq "WorkingDirectory=$LEGACY_INSTALL_DIR" "$SERVICE_DIR/$previous_service.service" || fail 'old unit was not restored'
  assert_configs_preserved
  fetch_node_binary
  setup_service
  assert_new_node
  assert_configs_preserved
done

# Configuration-only service refreshes also migrate an old node without downloading.
reset_fixture service-refresh
create_legacy_node gost
select_install_dir
setup_service
assert_new_node
assert_configs_preserved
[[ ! -s "$CURL_LOG" ]] || fail 'service refresh downloaded unexpectedly'

# Conflicting existing installations are left intact.
reset_fixture conflict
create_legacy_node mi-flux-node
mkdir -p "$NODE_INSTALL_DIR"
printf 'existing-new-node\n' > "$NODE_INSTALL_DIR/config.json"
if select_install_dir; then fail 'conflicting directories were accepted'; fi
grep -Fxq 'existing-new-node' "$NODE_INSTALL_DIR/config.json" || fail 'new configuration was overwritten'
cmp "$LEGACY_INSTALL_DIR/config.json" "$TEST_DIR/config.before"
[[ ! -s "$SYSTEMCTL_LOG" ]] || fail 'conflict stopped an existing service'

# Unrelated gost installations stay untouched during setup and uninstall.
reset_fixture unrelated
mkdir -p "$LEGACY_INSTALL_DIR"
printf 'unrelated-file\n' > "$LEGACY_INSTALL_DIR/unrelated"
printf 'ExecStart=/opt/other/gost\n' > "$SERVICE_DIR/gost.service"
select_install_dir
ensure_multi_master_config
fetch_node_binary
setup_service
uninstall_node <<< y
[[ -f "$LEGACY_INSTALL_DIR/unrelated" && -f "$SERVICE_DIR/gost.service" ]] || fail 'unrelated installation was changed'
if grep -Fxq 'stop gost' "$SYSTEMCTL_LOG"; then fail 'unrelated service was stopped'; fi

echo 'node installation, directory migration, rollback, conflict and proxy tests passed'
