#!/usr/bin/env bash
#
# Installs the central-server stack (central-server + TimescaleDB, and Caddy unless you
# already have a reverse proxy) without cloning the repo - central-server itself doesn't
# exist yet on a fresh box, so this can't be served BY central-server the way install.sh
# is; it's fetched from GitHub's raw content instead.
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/sneezy-5/sentinel/main/deploy/install-central.sh \
#     | bash -s -- --domain=monitor.example.com --db-password=<password> [--admin-password=<password>] [--no-caddy]
#
# Requires Docker already installed. Bundling a single self-contained binary (as asked
# for) isn't practical for central-server specifically - unlike the agent, it needs a real
# running Postgres/TimescaleDB next to it, which can't be baked into one executable. This
# script is the practical equivalent: no git clone, no manual file editing, one command.
#
# NOT TESTED end-to-end on a fresh box (no server available to run this against while
# writing it) - the individual pieces (docker-compose.yml, Caddyfile, init-hypertables.sql)
# have been exercised manually in this conversation, but not through this exact script.

set -euo pipefail

RAW_BASE="https://raw.githubusercontent.com/sneezy-5/sentinel/main/deploy"
INSTALL_DIR="/opt/sentinel"

DOMAIN=""
DB_PASSWORD=""
ADMIN_PASSWORD=""
WITH_CADDY=true

for arg in "$@"; do
  case "$arg" in
    --domain=*) DOMAIN="${arg#*=}" ;;
    --db-password=*) DB_PASSWORD="${arg#*=}" ;;
    --admin-password=*) ADMIN_PASSWORD="${arg#*=}" ;;
    --no-caddy) WITH_CADDY=false ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$DOMAIN" || -z "$DB_PASSWORD" ]]; then
  echo "Usage: install-central.sh --domain=<domain> --db-password=<password> [--admin-password=<password>] [--no-caddy]" >&2
  exit 1
fi

if [[ "$(id -u)" -ne 0 ]]; then
  echo "This script must be run as root (writes to /opt and manages Docker)." >&2
  exit 1
fi

if ! command -v docker &> /dev/null; then
  echo "Docker is required but not found - install it first: https://docs.docker.com/engine/install/" >&2
  exit 1
fi

if ! docker compose version &> /dev/null; then
  echo "The Docker Compose plugin is required but not found (docker compose, not docker-compose)." >&2
  exit 1
fi

# A generated password beats a hardcoded default landing in shell history across many
# installs - openssl is virtually always present alongside Docker, no extra dependency.
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(openssl rand -base64 18)}"

echo "==> Setting up Sentinel central in ${INSTALL_DIR}"
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

curl -fsSL "${RAW_BASE}/docker-compose.yml" -o docker-compose.yml
curl -fsSL "${RAW_BASE}/Caddyfile" -o Caddyfile
curl -fsSL "${RAW_BASE}/init-hypertables.sql" -o init-hypertables.sql
curl -fsSL "${RAW_BASE}/nginx-central-server.conf.example" -o nginx-central-server.conf.example

cat > .env <<EOF
SENTINEL_DOMAIN=${DOMAIN}
DB_PASSWORD=${DB_PASSWORD}
ADMIN_USERNAME=admin
ADMIN_PASSWORD=${ADMIN_PASSWORD}
EOF
chmod 600 .env

echo "==> Pulling latest images"
# Without an explicit pull, "up -d" only fetches an image if that tag isn't already cached
# locally - re-running this script (e.g. to pick up a newer release) would otherwise keep
# reusing whatever :latest happened to be cached from an earlier run, silently never
# updating central-server even though a newer image exists on GHCR.
docker compose --env-file .env pull timescaledb central-server

echo "==> Starting timescaledb + central-server"
docker compose --env-file .env up -d timescaledb central-server

if [[ "$WITH_CADDY" == true ]]; then
  echo "==> Starting caddy (skip with --no-caddy if you already run a reverse proxy on 80/443)"
  docker compose --env-file .env up -d caddy
else
  echo "==> Skipping caddy - see nginx-central-server.conf.example to wire up an existing reverse proxy."
fi

echo "==> Waiting for central-server to create its schema..."
# A fixed sleep here used to cause init-hypertables.sql to run against a database that
# didn't have the app's tables yet ("relation does not exist" on every statement) -
# Hibernate's ddl-auto=update can easily take longer than a few seconds on a slower VPS.
# Poll for the actual signal (does system_metrics exist yet) instead of guessing a delay.
SCHEMA_READY=false
for _ in $(seq 1 30); do
  # `< /dev/null`: this whole script's own stdin is the curl pipe still streaming the rest
  # of the script's source - `docker compose exec` (even with -T, which only skips
  # allocating a tty, not stdin forwarding) inherits and relays stdin to the container by
  # default, so without this it can consume bytes meant for bash itself and truncate the
  # script's execution partway through with no error, unpredictably depending on timing.
  if docker compose --env-file .env exec -T timescaledb \
      psql -U sentinel -d sentinel -tAc "SELECT to_regclass('public.system_metrics') IS NOT NULL" \
      < /dev/null 2>/dev/null \
      | grep -q "^t$"; then
    SCHEMA_READY=true
    break
  fi
  # Otherwise this loop is completely silent for up to 60s, which reads as "hung" rather
  # than "still working" - printf without a newline keeps it on the same "Waiting..." line.
  printf '.'
  sleep 2
done
echo

if [[ "$SCHEMA_READY" != true ]]; then
  echo "central-server hasn't created its schema after 60s - check its logs for a startup" >&2
  echo "error (docker compose --env-file .env logs central-server), fix that, then re-run" >&2
  echo "just the conversion once the tables exist:" >&2
  echo "  cd ${INSTALL_DIR} && docker compose --env-file .env exec -T timescaledb psql -U sentinel -d sentinel -f - < init-hypertables.sql" >&2
  exit 1
fi

docker compose --env-file .env exec -T timescaledb psql -U sentinel -d sentinel -f - < init-hypertables.sql \
  || echo "Hypertable conversion failed or partially applied - see the root README's Deploying section to retry/debug."

echo
echo "==> Done."
echo "    Admin username: admin"
echo "    Admin password: ${ADMIN_PASSWORD}"
echo "    (save this now - it's only shown here, change it from the Settings page after logging in)"
if [[ "$WITH_CADDY" == true ]]; then
  echo "    Dashboard: https://${DOMAIN}"
else
  echo "    Dashboard: point your existing reverse proxy at 127.0.0.1:8090, then https://${DOMAIN}"
fi
