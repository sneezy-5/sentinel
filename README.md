# Sentinel

A "install and forget" multi-server monitoring tool. Unlike Prometheus/Grafana, you don't
instrument your own projects: an agent installed on each server auto-detects what's running
(Docker/Swarm, PM2, Kubernetes pods, plain processes) and pushes metrics + logs to a central
dashboard over HTTPS.

Full design rationale, data model, and open questions live in
[`architecture-monitoring-tool.md`](architecture-monitoring-tool.md).

## How it fits together

```
[VPS 1: Agent]  --push HTTPS + token-->  \
[VPS 2: Agent]  --push HTTPS + token-->   --> [central-server: Spring Boot + TimescaleDB] --> [Dashboard]
[VPS N: Agent]  --push HTTPS + token-->  /
```

- **central-server** is the only thing you host explicitly. It ingests agent pushes, stores
  time series in TimescaleDB, exposes a REST API, and serves the dashboard (Thymeleaf,
  server-rendered, no separate frontend).
- **agent** runs on every monitored server. It has no Spring dependency and doesn't depend
  on `core`, on purpose — it's meant to stay small and independent from the central server's
  release cycle.
- **agent-native** (C) reads raw system stats (`/proc`, `/sys`) and hands them to the agent
  through a local file, not JNI — a crash in the native collector can't take the JVM down
  with it.
- **core** is the shared domain model (`Server`, `Service`, `SystemMetric`, ...) used only by
  `central-server`. It has no framework dependency of its own.

Each push is a full snapshot of currently active services, not a diff — simple, self-healing
if a previous push was lost, and cheap enough given how few services a typical server runs.

## Repository layout

```
core/            domain model shared by central-server (plain Java, no Spring)
central-server/  Spring Boot app: ingestion, REST API, dashboard, alerting
agent/           runs on each monitored server (plain Java, no Spring, no dependency on core)
agent-native/    C module for low-level system stats (built separately, see below)
deploy/          production docker-compose stack (central-server + TimescaleDB + Caddy)
```

## Getting started (central-server)

Requires JDK 17+ and Docker (for a local TimescaleDB).

```bash
git clone <this-repo>
cd sentinel
docker compose up -d          # starts a local TimescaleDB on localhost:5432
./mvnw -pl central-server -am spring-boot:run
```

The dashboard is then at `http://localhost:8080`. The default `application.yaml` uses
`ddl-auto: update`, so the schema is created automatically on first run — that gives you
plain Postgres tables, not real TimescaleDB hypertables. See the comment in
`SystemMetricEntity` for what a production deployment needs on top of that (a real
migration tool calling `create_hypertable(...)`).

Run the whole test suite (all modules):

```bash
./mvnw test
```

## Building the agent

```bash
./mvnw -pl agent -am package        # agent/target/agent-0.0.1-SNAPSHOT.jar (fat jar, runs on any JVM)
java -jar agent/target/agent-*.jar /path/to/monitoring-agent.yml
```

A GraalVM native-image build is also wired up (`-Pnative` profile) to produce a
dependency-free binary instead of requiring a JRE on every monitored server — see
`agent/Dockerfile.native` and `.github/workflows/agent-native-build.yml`. This path hasn't
been exercised end-to-end yet (documented as such in those files); the reflection
configuration under `agent/src/main/resources/META-INF/native-image/` is a starting point,
not a guarantee — validate it with the native-image tracing agent before relying on it.

## Building agent-native

Not part of the Maven reactor (Maven doesn't build C):

```bash
cd agent-native
make
```

See `agent-native/README.md` for details.

## Installing an agent on a server

Once `central-server` is running and reachable, creating a server from the dashboard
(`POST /api/servers`) returns a ready-to-copy install command:

```bash
curl -sSL https://<your-central>/install.sh | bash -s -- --token=<token> --central=https://<your-central>
```

See `central-server/src/main/resources/static/install.sh` — it fetches the two compiled
binaries from GitHub Releases by default (public, no auth needed), and the script/systemd
units/config template directly from the central itself (bundled static resources, always
in sync with whatever version of central-server is running).

## Deploying to production

The `deploy/` stack runs central-server + TimescaleDB + Caddy (automatic HTTPS via Let's
Encrypt). HTTPS isn't optional here: agents authenticate with a bearer token over the
wire, and `install.sh` is fetched via `curl | bash` — both need a real certificate, not
just `http://`.

### Quickest path: no git clone

On a fresh VPS with Docker already installed and a DNS A record already pointing at it:

```bash
curl -sSL https://raw.githubusercontent.com/sneezy-5/sentinel/main/deploy/install-central.sh \
  | sudo bash -s -- --domain=monitor.example.com --db-password=<a-real-password>
```

If this host already has a DNS-resolvable domain and its own reverse proxy on 80/443
(nginx, another Caddy, etc.), add `--no-caddy` and skip to
["Already have your own reverse proxy"](#already-have-your-own-reverse-proxy) below for the
rest of the setup - the command above assumes Caddy is free to take 80/443 itself.

The script prints a generated admin password once at the end — save it immediately, change
it from the Settings page after logging in. **If you never see that final message at all
(the script just seems to stop partway through, no error)**, you hit one of two bugs found
by actually running this against a real VPS while writing it, both since fixed:

- A fixed 15s wait before converting the tables into hypertables wasn't always enough time
  for Hibernate to finish creating them (`ddl-auto=update` can take longer on a slower box) -
  the conversion failed with "relation ... does not exist" on every statement. Replaced with
  a poll loop (up to 60s, printing a `.` each check so it doesn't look hung) that waits for
  `system_metrics` to actually exist first.
- That poll loop's `docker compose exec` call didn't redirect its own stdin away from the
  script's - since the whole script is `curl`'d straight into `bash`'s stdin, `exec` (even
  with `-T`, which only skips allocating a tty, not stdin forwarding) was relaying bytes
  meant for the rest of the *script itself* into the container instead, truncating execution
  silently and unpredictably depending on timing. Fixed with `< /dev/null` on that call.

If you're running an older copy of this script (cached, or fetched before this fix) and hit
this, or it times out for an unrelated reason, the schema has usually already been created
by the time the script stalls - just re-run the conversion directly instead of the whole
script:

```bash
cd /opt/sentinel
docker compose --env-file .env exec -T timescaledb psql -U sentinel -d sentinel -f - < init-hypertables.sql
```

### Already have your own reverse proxy

If port 80 is already taken (a shared box running other sites, most commonly a
host-installed nginx - `ss -tlnp | grep :80` shows what, `docker ps -a --filter
publish=80` if it's a container instead), starting `caddy` will fail outright with
"address already in use" and abort the rest of the script (`set -euo pipefail`) before it
ever gets to the schema-wait/hypertable-conversion steps. Use `--no-caddy` from the start
to skip that entirely:

```bash
curl -sSL https://raw.githubusercontent.com/sneezy-5/sentinel/main/deploy/install-central.sh \
  | sudo bash -s -- --domain=monitor.example.com --db-password=<a-real-password> --no-caddy
```

central-server ends up listening on `127.0.0.1:8090` only (not exposed publicly) - your own
reverse proxy is what actually terminates TLS and gets requests to it.
`nginx-central-server.conf.example` (downloaded into `/opt/sentinel` alongside everything
else) is a starting point for nginx specifically:

```bash
cd /opt/sentinel
# adapt SENTINEL_DOMAIN in the example to your real domain first
cp nginx-central-server.conf.example /etc/nginx/sites-available/monitor.example.com
ln -s /etc/nginx/sites-available/monitor.example.com /etc/nginx/sites-enabled/
# get a cert however you normally do on this box, e.g.:
certbot --nginx -d monitor.example.com
nginx -t && systemctl reload nginx
```

The DNS A record for the domain needs to already resolve to this host before requesting a
cert - same requirement as the Caddy path, just handled by whatever ACME client this
already-existing nginx setup uses instead of Caddy's automatic one. Confirm central-server
itself is reachable before troubleshooting the proxy layer - no `/actuator` endpoint (not a
dependency here), but `curl -I http://127.0.0.1:8090/` from the VPS itself should still get
a real HTTP response (redirect to the login page), independent of nginx/DNS/certs being
right yet.

### Alternative: clone first

Useful if you want to read/edit the compose file before running it:

```bash
git clone <this-repo>
cd sentinel
cp deploy/.env.example deploy/.env
# edit deploy/.env: SENTINEL_DOMAIN must have a DNS A record pointing at this VPS already,
# Caddy requests the cert on first request and will fail if the domain doesn't resolve.
# Also set ADMIN_PASSWORD - this seeds the dashboard login on first boot (see AdminSeeder).

docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

`central-server` is pulled pre-built from GHCR, not compiled on the VPS - no JDK/Maven
needed there. If GHCR rejects the pull ("denied"/"unauthorized"), the package is probably
still private (Actions-published packages default to private): switch its visibility to
public in the package's GitHub settings.

Then, once central-server has come up and created its schema (`docker compose -f
deploy/docker-compose.yml logs -f central-server`, wait for "Started
CentralServerApplication"), convert the plain tables into real TimescaleDB hypertables —
this is a one-time step, it can't happen automatically because the tables don't exist yet
when the database container first initializes:

```bash
docker compose -f deploy/docker-compose.yml exec -T timescaledb \
  psql -U sentinel -d sentinel -f - < deploy/init-hypertables.sql
```

The dashboard requires logging in (`ADMIN_USERNAME`/`ADMIN_PASSWORD` from `deploy/.env`,
only used to create the account on first boot - see `AdminSeeder`). Change the password
from the Settings page after the first login; `/api/agents/**` (agent pushes) and static
assets (`install.sh`, systemd units, CSS/JS) stay reachable without a session, since a
monitored server running `install.sh` has no way to log in.

The Settings page also configures SMTP for email alerts (any provider - host/port/user/
password/from/to). Threshold rules themselves (`AlertRule`) still have no UI to create
them yet — see the "Status" section below.

### Releasing the agent binaries

Push a `v*` tag (e.g. `v0.1.0`) to trigger both release workflows at once:
`agent-release.yml` builds all four agent binaries (the Java module's GraalVM native image
+ the C `agent-native` module, amd64 and arm64 each) and uploads them as assets on a GitHub
Release matching that tag; `central-server-release.yml` builds and pushes the Docker image
to GHCR. Nothing needs to be built or placed manually on the server — `install.sh` pulls
`sentinel-agent-linux-<arch>` and `sentinel-native-linux-<arch>` straight from
`github.com/<repo>/releases/latest/download/...`, which always resolves to the most recent
tag with no auth required.

`agent-release.yml`'s four jobs (`agent-amd64`, `agent-arm64`, `native-amd64`,
`native-arm64` - the C build is the last two, not a separate workflow) run strictly
sequentially via `needs:`, not in parallel (see the comment at the top of that file for
why) - the C build only starts once both Java native-image jobs have finished, which can
take a while, so it's normal for it to still show as queued/not-yet-run while the Java jobs
are in progress rather than appearing as its own workflow in the Actions list.

If a monitored server can't reach github.com, `deploy/downloads/` is a self-hosted fallback
(`install.sh --download-base=https://<central>/downloads`) — see `deploy/downloads/README.md`.

Visit `https://<your-domain>`, add a server from the dashboard, and run the install
command it gives you on a second VPS to exercise the full push flow end-to-end.

### Updating an existing deployment

Normal case (a new feature adds columns/tables but doesn't change existing ones - the
common case, and the only one `ddl-auto=update` supports without manual SQL, see "Resetting
a deployment" below for the other case):

- **central-server**: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env
  pull && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`. New
  columns/tables get created automatically on boot. If a new table is an `@ElementCollection`
  hanging off `system_metrics`/`service_metrics`/`logs_raw`/`log_events` (the four
  hypertables), it must use a `NO_CONSTRAINT` join (see `SystemMetricEntity`) instead of a
  real foreign key - those tables already had their primary key dropped by
  `init-hypertables.sql`, so Hibernate creating a *new* table with a real FK against them
  fails outright on an already-converted database. Easy to get wrong when adding the next
  one: if central-server fails to start after an update with a Postgres error mentioning a
  foreign key constraint, this is almost certainly why.
- **agent**: re-run the same `install.sh` command used for the original install (same
  token/central URL, or whatever placeholder values - see below). It stops the services,
  overwrites the two binaries with the latest release, and restarts - the existing
  `monitoring-agent.yml` is never touched (`install.sh` only writes one if none exists
  yet), and any config field the update introduced that isn't in that file falls back to
  `AgentConfig`'s own default (verified in `ConfigLoaderTest` - SnakeYAML only sets fields
  actually present in the YAML, it doesn't null out the rest). `--token`/`--central` still
  need to be *passed* to satisfy the script's argument check even though they won't be used
  for an update - the values in your already-installed config are fine to reuse.

### Resetting a deployment (wipe and start over)

Since `ddl-auto` is `update`, not a real migration tool (see the "Getting started" note
above), a schema change to an *existing* column (e.g. widening `logs_raw.message` from the
Hibernate-default `varchar(255)` to `text` in the commit that fixed real container logs
truncating) never gets applied automatically to a database that already has that column -
`update` only adds what's missing, it doesn't alter what's already there. The reliable fix
after a schema change like that is to drop the database and let it get recreated from
scratch, rather than trying to hand-write an `ALTER TABLE`:

```bash
cd ~/sentinel   # wherever deploy/docker-compose.yml lives on this VPS

# Wipes central-server + TimescaleDB and their volume (all servers/metrics/logs/alert
# rules are lost - re-adding a server generates a new token).
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v

# Agent binaries + config live outside Docker entirely (see install.sh) - remove them too
# so the reinstall below writes a fresh config with the new token instead of reusing the
# old one (install.sh never overwrites an existing monitoring-agent.yml).
systemctl stop monitoring-agent.service monitoring-agent-collector.service
rm -rf /opt/monitoring-agent

# Pull the latest images/binaries and come back up on a clean schema.
docker compose -f deploy/docker-compose.yml --env-file deploy/.env pull
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f central-server
# wait for "Started CentralServerApplication", then Ctrl+C
```

Then re-run `init-hypertables.sql` (see above - the plain tables need converting again on
the fresh schema), add a server from the dashboard to get a new install command, and run
it on the monitored server to reinstall the agent with the new token.

**The deploy stack (Docker Compose, Caddy, hypertable SQL) has not been run in this
environment** (no Docker available here) — the three release workflows above have run
successfully in CI, but the actual `docker compose up` on a real VPS is still unverified.
In particular, if `init-hypertables.sql`'s `DROP CONSTRAINT` fails with "constraint does
not exist", check the actual constraint name with `\d system_metrics` inside `psql` and
adjust the script.

## Status

This is an early-stage scaffold: the module boundaries, ingestion/API contracts, and
persistence layer are in place and tested, but several pieces are intentionally stubbed
(discovery adapters' actual Docker/PM2/Kubernetes calls, log pattern-matching rules,
notification channels for alerts). Search the codebase for `TODO(roadmap #N)` comments —
they map to the roadmap in `architecture-monitoring-tool.md`, section 8.

## License

Licensed under the [GNU AGPLv3](LICENSE). If you run a modified version of this project as
a network service, the AGPL requires you to make your modified source available to the
users of that service.
