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
Encrypt) with one command. HTTPS isn't optional here: agents authenticate with a bearer
token over the wire, and `install.sh` is fetched via `curl | bash` — both need a real
certificate, not just `http://`.

On a fresh VPS with Docker installed:

```bash
git clone <this-repo>
cd sentinel
cp deploy/.env.example deploy/.env
# edit deploy/.env: SENTINEL_DOMAIN must have a DNS A record pointing at this VPS already,
# Caddy requests the cert on first request and will fail if the domain doesn't resolve.

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

### Releasing the agent binaries

Push a `v*` tag (e.g. `v0.1.0`) to trigger all three release workflows at once:
`agent-jvm-native-build.yml` and `agent-native-c-build.yml` build the two agent binaries
(amd64 + arm64 each) and upload them as assets on a GitHub Release matching that tag;
`central-server-release.yml` builds and pushes the Docker image to GHCR. Nothing needs to
be built or placed manually on the server — `install.sh` pulls
`sentinel-agent-linux-<arch>` and `sentinel-native-linux-<arch>` straight from
`github.com/<repo>/releases/latest/download/...`, which always resolves to the most recent
tag with no auth required.

If a monitored server can't reach github.com, `deploy/downloads/` is a self-hosted fallback
(`install.sh --download-base=https://<central>/downloads`) — see `deploy/downloads/README.md`.

Visit `https://<your-domain>`, add a server from the dashboard, and run the install
command it gives you on a second VPS to exercise the full push flow end-to-end.

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
