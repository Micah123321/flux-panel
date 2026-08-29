#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Avoid the installer's optional country lookup while sourcing helper functions.
curl() { return 1; }
source "$ROOT_DIR/panel_install.sh"
unset -f curl

assert_overlap() {
  if ! ipv4_cidrs_overlap "$1" "$2"; then
    echo "expected overlapping CIDRs: $1 and $2" >&2
    exit 1
  fi
}

assert_no_overlap() {
  if ipv4_cidrs_overlap "$1" "$2"; then
    echo "expected non-overlapping CIDRs: $1 and $2" >&2
    exit 1
  fi
}

assert_overlap "172.20.0.0/16" "172.20.0.0/16"
assert_overlap "172.16.0.0/16" "172.16.0.0/12"
assert_no_overlap "172.20.0.0/16" "172.21.0.0/16"

# Mock Docker IPAM data without requiring a running Docker daemon.
docker() {
  if [[ "${1:-}" == "network" && "${2:-}" == "ls" ]]; then
    if [[ "${3:-}" == "-q" ]]; then
      printf '%s\n' net-narrow net-wide net-ten
    fi
    return 0
  fi

  if [[ "${1:-}" == "network" && "${2:-}" == "inspect" ]]; then
    printf '%s\n' "172.20.0.0/16" "172.16.0.0/12" "10.240.0.0/16"
    return 0
  fi

  echo "unexpected docker invocation: $*" >&2
  return 1
}

if docker_ipv4_subnet_available "172.20.0.0/16"; then
  echo "occupied subnet was incorrectly considered available" >&2
  exit 1
fi

if docker_ipv4_subnet_available "172.18.0.0/16"; then
  echo "subnet inside a broad occupied CIDR was incorrectly considered available" >&2
  exit 1
fi

select_available_docker_ipv4_subnet >/dev/null
[[ "$DOCKER_IPV4_SUBNET" == "10.241.0.0/16" ]] || {
  echo "unexpected selected subnet: $DOCKER_IPV4_SUBNET" >&2
  exit 1
}

echo "panel_install subnet selection tests passed"
