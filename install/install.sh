#!/usr/bin/env bash
#
# Sentinel agent install script (architecture doc, section 5.1).
# Usage:
#   curl -sSL https://<central>/install.sh | bash -s -- --token=xxxx --central=https://<central>
#
# NOT TESTED under real conditions: assumes a download endpoint exists (--download-base,
# see comment below) that still needs to be built - an open point from the earlier
# discussion ("the central hosts the artifacts, or GitHub releases instead").

set -euo pipefail

INSTALL_DIR="/opt/monitoring-agent"
CONFIG_PATH="${INSTALL_DIR}/monitoring-agent.yml"
DOWNLOAD_BASE=""
CENTRAL_URL=""
TOKEN=""

for arg in "$@"; do
  case "$arg" in
    --token=*) TOKEN="${arg#*=}" ;;
    --central=*) CENTRAL_URL="${arg#*=}" ;;
    --download-base=*) DOWNLOAD_BASE="${arg#*=}" ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$TOKEN" || -z "$CENTRAL_URL" ]]; then
  echo "Usage: install.sh --token=<token> --central=<url> [--download-base=<url>]" >&2
  exit 1
fi

# By default, binaries are served by the central itself (no extra infra to manage);
# --download-base lets you switch to GitHub releases later if that path is preferred,
# without changing the install script.
DOWNLOAD_BASE="${DOWNLOAD_BASE:-${CENTRAL_URL}/downloads}"

case "$(uname -m)" in
  x86_64) ARCH="amd64" ;;
  aarch64|arm64) ARCH="arm64" ;;
  *)
    echo "Unsupported architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

if [[ "$(id -u)" -ne 0 ]]; then
  echo "This script must be run as root (installs systemd units)." >&2
  exit 1
fi

echo "==> Installing into ${INSTALL_DIR} (arch: ${ARCH})"
mkdir -p "${INSTALL_DIR}/bin"
mkdir -p /var/lib/sentinel /run/sentinel

echo "==> Downloading binaries"
curl -fsSL "${DOWNLOAD_BASE}/agent/linux/${ARCH}/sentinel-agent" -o "${INSTALL_DIR}/bin/sentinel-agent"
curl -fsSL "${DOWNLOAD_BASE}/agent-native/linux/${ARCH}/sentinel-native" -o "${INSTALL_DIR}/bin/sentinel-native"
chmod +x "${INSTALL_DIR}/bin/sentinel-agent" "${INSTALL_DIR}/bin/sentinel-native"

echo "==> Writing local config (${CONFIG_PATH})"
if [[ -f "$CONFIG_PATH" ]]; then
  echo "    Existing config found, not overwritten (reinstall?). Delete it to regenerate."
else
  # The script runs via "curl | bash", $0 doesn't point to anything usable on the VPS -
  # the template comes from the same download endpoint as the binaries/units.
  TEMPLATE=$(curl -fsSL "${DOWNLOAD_BASE}/install/monitoring-agent.yml.template")
  sed -e "s#__CENTRAL_URL__#${CENTRAL_URL}#" -e "s#__TOKEN__#${TOKEN}#" \
    <<< "$TEMPLATE" > "$CONFIG_PATH"
fi

echo "==> Installing systemd services"
curl -fsSL "${DOWNLOAD_BASE}/install/monitoring-agent-collector.service" -o /etc/systemd/system/monitoring-agent-collector.service
curl -fsSL "${DOWNLOAD_BASE}/install/monitoring-agent.service" -o /etc/systemd/system/monitoring-agent.service

systemctl daemon-reload
systemctl enable --now monitoring-agent-collector.service
systemctl enable --now monitoring-agent.service

echo "==> Install complete. Status:"
systemctl --no-pager status monitoring-agent-collector.service monitoring-agent.service || true
