#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/install.sh"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
INSTALL_DIR="$TEST_DIR/node"
SERVICE_DIR="$TEST_DIR/systemd"
mkdir -p "$INSTALL_DIR" "$SERVICE_DIR"
SYSTEMCTL_LOG="$TEST_DIR/systemctl.log"
CURL_LOG="$TEST_DIR/curl.log"
: > "$SYSTEMCTL_LOG"
CURL_MODE=ok

fail() { echo "$*" >&2; exit 1; }
systemctl() { printf '%s\n' "$*" >> "$SYSTEMCTL_LOG"; }
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

# The selected mode applies to both raw scripts and Release binaries.
DIRECT_URL="$DOWNLOAD_URL"
[[ $(resolve_download_url "$DIRECT_URL") == "$DIRECT_URL" ]] || fail "default mode is not direct"
parse_args -a '[2001:db8::1]:3000' -s fixture-secret -c
[[ "$SERVER_ADDR" == '[2001:db8::1]:3000' && "$SECRET" == fixture-secret ]] || fail "install arguments changed"
[[ "$DOMESTIC_MODE" == true ]] || fail "domestic flag ignored"
for url in "$DIRECT_URL" 'https://raw.githubusercontent.com/owner/repo/main/install.sh' 'https://api.github.com/repos/owner/repo/releases/latest'; do
  [[ $(resolve_download_url "$url") == "$GITHUB_PROXY$url" ]] || fail "GitHub URL was not proxied"
done
[[ $(resolve_download_url "$GITHUB_PROXY$DIRECT_URL") == "$GITHUB_PROXY$DIRECT_URL" ]] || fail "proxy applied twice"
[[ $(resolve_download_url 'https://example.com/file') == 'https://example.com/file' ]] || fail "non-GitHub source changed"

printf '#!/bin/bash\necho legacy-fixture\n' > "$INSTALL_DIR/gost"
chmod +x "$INSTALL_DIR/gost"
printf 'ExecStart=%s/gost\n' "$INSTALL_DIR" > "$SERVICE_DIR/gost.service"
printf '{"servers":[{"addr":"panel.example:3000","secret":"fixture","ns":""}]}\n' > "$INSTALL_DIR/config.json"
printf '{"services":[{"name":"existing-forward"}]}\n' > "$INSTALL_DIR/gost.json"
cp "$INSTALL_DIR/config.json" "$TEST_DIR/config.before"
cp "$INSTALL_DIR/gost.json" "$TEST_DIR/gost.before"

# Failed or invalid downloads must leave the working legacy service untouched.
for CURL_MODE in failed invalid; do
  if fetch_node_binary; then fail "bad download succeeded: $CURL_MODE"; fi
  [[ -x "$INSTALL_DIR/gost" && -f "$SERVICE_DIR/gost.service" ]] || fail "legacy node was removed on failure"
  [[ ! -e "$INSTALL_DIR/$SERVICE_NAME.new" && ! -e "$INSTALL_DIR/$SERVICE_NAME" ]] || fail "invalid binary was installed"
  [[ ! -s "$SYSTEMCTL_LOG" ]] || fail "working service stopped before download validation"
done
CURL_MODE=ok
fetch_node_binary
setup_service
[[ -x "$INSTALL_DIR/$SERVICE_NAME" ]] || fail "renamed executable missing"
[[ ! -e "$INSTALL_DIR/gost" && ! -e "$SERVICE_DIR/gost.service" ]] || fail "legacy node was not retired"
grep -Fxq "ExecStart=$INSTALL_DIR/$SERVICE_NAME" "$SERVICE_DIR/$SERVICE_NAME.service" || fail "wrong process path"
grep -Fxq 'Description=mi-flux-node' "$SERVICE_DIR/$SERVICE_NAME.service" || fail "wrong application name"
for action in 'stop gost' 'disable gost' 'enable mi-flux-node' 'restart mi-flux-node'; do
  grep -Fxq "$action" "$SYSTEMCTL_LOG" || fail "missing service action: $action"
done
grep -Fxq "$GITHUB_PROXY$DIRECT_URL" "$CURL_LOG" || fail "binary download bypassed proxy"
cmp "$INSTALL_DIR/config.json" "$TEST_DIR/config.before"
cmp "$INSTALL_DIR/gost.json" "$TEST_DIR/gost.before"

# Repeated updates operate on the new service and retain both configuration files.
check_and_install_tcpkill() { :; }
update_node
cmp "$INSTALL_DIR/config.json" "$TEST_DIR/config.before"
cmp "$INSTALL_DIR/gost.json" "$TEST_DIR/gost.before"

# An independent gost unit is not part of this node installation.
printf 'ExecStart=/opt/other/gost\n' > "$SERVICE_DIR/gost.service"
: > "$SYSTEMCTL_LOG"
setup_service
[[ -f "$SERVICE_DIR/gost.service" ]] || fail "unrelated service was deleted"
if grep -Fxq 'stop gost' "$SYSTEMCTL_LOG"; then fail "unrelated service was stopped"; fi
uninstall_node <<< y
[[ ! -d "$INSTALL_DIR" && ! -e "$SERVICE_DIR/$SERVICE_NAME.service" ]] || fail "node uninstall left files behind"
[[ -f "$SERVICE_DIR/gost.service" ]] || fail "uninstall removed unrelated service"

echo 'node install mode, migration, failure preservation, update and uninstall tests passed'
