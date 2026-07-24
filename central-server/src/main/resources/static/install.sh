#!/usr/bin/env bash
#
# Sentinel agent install script (architecture doc, section 5.1).
# Usage:
#   curl -sSL https://<central>/install.sh | bash -s -- --token=xxxx --central=https://<central>
#
# Static files (this script, the systemd units, the config template) come from the
# central itself - they're bundled into central-server's own image, nothing to deploy
# separately for them. The two compiled binaries (sentinel-agent, sentinel-native) come
# from GitHub Releases by default (public, no auth needed, always the latest tag) -
# override with --download-base if you'd rather self-host them behind the central
# (see WebConfig's /downloads/** mapping and the root README).

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

DOWNLOAD_BASE="${DOWNLOAD_BASE:-https://github.com/sneezy-5/sentinel/releases/latest/download}"

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

# Re-running this script to update an already-installed agent overwrites the binaries in
# place - if the old process is still running that file, the write fails with ETXTBSY
# ("text file busy"), which curl reports as exit 23. Stopping first avoids that; `|| true`
# keeps a first-time install working when these units don't exist yet.
echo "==> Stopping any existing services before overwriting binaries"
systemctl stop monitoring-agent.service monitoring-agent-collector.service 2>/dev/null || true

echo "==> Downloading binaries from ${DOWNLOAD_BASE}"
curl -fsSL "${DOWNLOAD_BASE}/sentinel-agent-linux-${ARCH}" -o "${INSTALL_DIR}/bin/sentinel-agent"
curl -fsSL "${DOWNLOAD_BASE}/sentinel-native-linux-${ARCH}" -o "${INSTALL_DIR}/bin/sentinel-native"
chmod +x "${INSTALL_DIR}/bin/sentinel-agent" "${INSTALL_DIR}/bin/sentinel-native"

echo "==> Writing local config (${CONFIG_PATH})"
if [[ -f "$CONFIG_PATH" ]]; then
  echo "    Existing config found, not overwritten (reinstall?). Delete it to regenerate."
else
  TEMPLATE=$(curl -fsSL "${CENTRAL_URL}/monitoring-agent.yml.template")
  sed -e "s#__CENTRAL_URL__#${CENTRAL_URL}#" -e "s#__TOKEN__#${TOKEN}#" \
    <<< "$TEMPLATE" > "$CONFIG_PATH"
fi

echo "==> Installing systemd services"
curl -fsSL "${CENTRAL_URL}/monitoring-agent-collector.service" -o /etc/systemd/system/monitoring-agent-collector.service
curl -fsSL "${CENTRAL_URL}/monitoring-agent.service" -o /etc/systemd/system/monitoring-agent.service

systemctl daemon-reload
systemctl enable --now monitoring-agent-collector.service
systemctl enable --now monitoring-agent.service

echo "==> Install complete. Status:"
systemctl --no-pager status monitoring-agent-collector.service monitoring-agent.service || true
