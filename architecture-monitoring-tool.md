# Multi-server monitoring tool — Architecture & specifications

## 1. Goal

Build an "install and forget" monitoring tool to watch multiple servers (VPS) and the
services running on them, regardless of their deployment mode: Docker/Swarm, PM2, plain
processes, or Kubernetes (pods).

Unlike Prometheus/Grafana, the user shouldn't have to **instrument anything in their
projects**: the tool automatically detects what's running on each server.

Target features:
- Server-level CPU / RAM / disk / network tracking
- CPU / RAM tracking per individual service (container, PM2 process, pod, plain process)
- Per-service logs (browsing + error and API call counting)
- Threshold-based alerting
- Multi-server from day one, simple to add a new server

---

## 2. Architecture overview

```
[VPS 1: Agent]  --push HTTPS + token-->  \
[VPS 2: Agent]  --push HTTPS + token-->   --> [Central server: Spring Boot + TimescaleDB] --> [Dashboard]
[VPS N: Agent]  --push HTTPS + token-->  /
```

- **Dashboard = central server.** It's the only thing to explicitly install/host as a
  "product".
- **Agent (C module + Java module) = a dependency to install on each monitored server.**
- **Push** model: each agent initiates the connection to the central, not the other way
  around (see section 5).

---

## 3. The agent (per monitored server)

### 3.1 Separation of responsibilities

| Component | Role | Why |
|---|---|---|
| **C module** | Low-level system reads: CPU, RAM, disk, network, via `/proc`, `/sys`, cgroups. Also serves as a fallback to read raw process stats (`/proc/{pid}/...`) when no structured adapter is available. | Minimal overhead, single binary, keeps running even if the Java layer is under pressure. |
| **Java module (no Spring, lightweight)** | Discovery + application-level adapters (Docker/Swarm, PM2, Kubernetes), reads local config, merges data into a unified format, sends it to the central server. | More comfortable for parsing JSON and calling HTTP APIs (Docker API, PM2, Kubernetes API). |

The Java module is the only one that talks to the network toward the central; the C
module feeds the Java module locally (file, socket, or a small localhost HTTP server).

### 3.2 Discovery adapters

Each adapter activates automatically if its environment is detected — no configuration
required to turn it on.

- **Docker / Swarm**: detected via the presence of `/var/run/docker.sock`; if `docker info`
  reports `Swarm: active`, also activates the services/replicas layer on top of containers.
  Per-container stats via `/containers/{id}/stats` (CPU, RAM, network, I/O — the most
  complete of the four).
- **PM2**: detected via the presence of the PM2 daemon (`~/.pm2/pm2.pid`). List/stats via
  `pm2 jlist`. Logs already centralized by PM2 under `~/.pm2/logs/`. No per-process
  network/disk stats (known limitation).
- **Kubernetes (pods)**: detected via an active kubelet / present kubeconfig. Uses the
  Kubernetes API. Needs **metrics-server** for per-pod CPU/RAM stats; falls back to cgroups
  directly if absent (to plan for, more fragile). Recommended deployment model: agent as a
  **DaemonSet** (one agent pod per node) for native access to other pods' cgroups.
- **Plain process**: generic fallback via `/proc/{pid}/status` and `/proc/{pid}/stat` (read
  by the C module). The most limited: no notion of a grouped "service", just raw PIDs — the
  weakest link of zero-config, to iterate on with manual configuration if more precision is
  needed.

### 3.3 Local configuration (additive, never required at startup)

Optional `monitoring-agent.yml` file, generated empty at install time:

- **Level 1 (default, no user action)**: filtering of known system processes/services
  (kernel threads, `docker-proxy`, `ssh`, `cron`, etc.)
- **Level 2 (optional local config)**: exclude/include services by name/pattern, display
  renaming, server-specific alert thresholds, custom pattern-matching rules for logs (see
  section 7)
- **Level 3 (global config, dashboard side)**: default thresholds applied to all servers,
  log retention — avoids editing a file per VPS for generic settings

### 3.4 Network resilience

- **Local buffer** (file or embedded SQLite) if the central is unreachable, with a
  size/duration cap (e.g. max 1h buffered, oldest data purged beyond that)
- Every push sends a **full snapshot** of active services (not a diff): self-healing if a
  previous send was lost, negligible network cost given how few services run per server

---

## 4. Central server (Spring Boot)

### 4.1 Modules

- **Ingestion**: receives agent payloads (token authentication), writes to TimescaleDB
- **REST API**: aggregated queries and time series for the dashboard
- **Server management**: token generation and revocation, "server down" detection (no push
  for N seconds)
- **Alerting**: threshold rules + notifications (email/Telegram/webhook)

### 4.2 Data model (TimescaleDB — overview)

- `servers` (id, name, hostname, token, status, last_push)
- `system_metrics` *(hypertable)*: server_id, timestamp, cpu%, ram_used, ram_total,
  disk_used, disk_total, network
- `services`: stable id (`type:name`), server_id, name, type (docker/pm2/k8s/process),
  metadata
- `service_metrics` *(hypertable)*: service_id, timestamp, cpu%, mem_mb
- `logs_raw` *(hypertable, short retention — a few days)*: service_id, timestamp, level,
  message
- `log_events` *(hypertable, long retention)*: service_id, timestamp, event_type
  (error/api_call/warning), count
- `alerts`: rules + trigger history

TimescaleDB allows a differentiated retention policy (short-term detail, long-term
aggregates) — important for multi-server so disk usage doesn't blow up.

### 4.3 Security

- A **unique token per server**, generated from the dashboard, unlimited lifetime in V1 but
  manually revocable (no automatic rotation, to keep install simple)
- Agent → central communication over HTTPS

---

## 5. Communication model: push, not pull

**Clear-cut choice: push.** Each agent itself initiates the periodic send to the central.

Reasons:
- The central doesn't need to know each agent's IP address ahead of time (unlike a
  pull/scrape model à la Prometheus)
- Works even behind NAT or a restrictive firewall (outbound traffic is almost always
  allowed)
- The token serves as both the server's identification and authentication
- An agent that hasn't pushed for N seconds = automatic "server down" detection, no extra
  mechanism needed

### 5.1 Install flow

1. On the dashboard: "add a server" → generates a unique token + shows an install command
   (script or binary with the token as a parameter)
2. On the VPS to monitor: running the command → installs the agent (C + Java), writes a
   minimal local config: `central_url` + `token`
3. The agent starts, does its local discovery, starts pushing its data to the central over
   HTTPS (`Authorization: Bearer <token>`)
4. The central receives it, validates the token, matches it to the right server, stores it,
   the dashboard displays it automatically

No configuration needed on the central side to recognize a new server beyond generating
the token.

---

## 6. Payload format

Two distinct streams, very different volume profiles.

### 6.1 Metrics stream (frequency: 15-30s)

```json
{
  "timestamp": "2026-07-10T14:32:00Z",
  "system": {
    "cpu_percent": 42.5,
    "cpu_cores": 4,
    "ram_used_mb": 3120,
    "ram_total_mb": 8192,
    "disk": [
      { "mount": "/", "used_gb": 45.2, "total_gb": 100 }
    ],
    "network": { "rx_bytes": 102400, "tx_bytes": 51200 }
  },
  "services": [
    {
      "id": "docker:djeli-api-7f3a",
      "name": "djeli-api",
      "type": "docker",
      "status": "running",
      "cpu_percent": 8.1,
      "mem_mb": 210,
      "metadata": { "image": "djeli-api:latest", "swarm_service": "djeli_api" }
    },
    {
      "id": "pm2:worker-mailer",
      "name": "worker-mailer",
      "type": "pm2",
      "status": "running",
      "cpu_percent": 1.2,
      "mem_mb": 60,
      "metadata": { "pm2_id": 3, "restarts": 0 }
    }
  ]
}
```

Key point: each service's `id` field must be **stable over time** (computed, e.g.
`type:name`), not a PID or an ephemeral Docker id — otherwise the stored history fragments
every time a service restarts.

### 6.2 Logs stream (frequency: shorter, sent in batches)

```json
{
  "service_id": "docker:djeli-api-7f3a",
  "entries": [
    { "timestamp": "2026-07-10T14:32:01Z", "level": "info", "message": "Request handled in 42ms" },
    { "timestamp": "2026-07-10T14:32:02Z", "level": "error", "message": "DB connection timeout" }
  ]
}
```

The agent keeps a **local cursor/offset per log source** (last timestamp or file position)
to never duplicate or lose lines between two sends.

---

## 7. Logs: raw browsing vs derived metrics

Two different needs, two mechanisms.

### 7.1 Raw logs
Stored as-is (`logs_raw`), short retention (a few days), for direct browsing ("show me the
last 50 lines of this service").

### 7.2 Derived events (counters)
To answer "how many API calls", "list of errors" without re-scanning raw logs on every
request:

- The agent applies **pattern-matching rules** to each ingested line, and classifies the
  event (error / API call / warning / info)
- The resulting counters are stored as time series (`log_events`), tiny volume, long
  retention

Parsing happens **on the agent side**, not on the central: avoids loading the central with
parsing for every server at once, reduces bandwidth.

Two reliability cases:
- **Already-structured logs (JSON)** emitted by the server's own projects (e.g. Djeli):
  trivial, reliable parsing, under the developer's control
- **Free-text logs** (general case, PM2, third-party containers): generic default regex
  rules (`ERROR|Exception|timeout`, standard access-log formats like nginx), but fine-grained
  precision per service (e.g. counting calls to `/api/stock`) requires a custom rule defined
  in the local config — no longer 100% zero-config at this level of detail.

---

## 8. Implementation roadmap

1. Unified data model (generic "Service" with subtypes) — foundation for everything else
2. Agent: C module (low-level system metrics) + Docker/Swarm discovery (the most structured
   case)
3. Central server: ingestion, TimescaleDB storage, basic REST API, token management
4. Minimal dashboard: near real-time display of a server
5. PM2 adapter
6. Raw logs + derived events (default rules)
7. Kubernetes adapter (DaemonSet)
8. Plain process adapter (the fuzziest one, to iterate on)
9. Alerting (thresholds + notifications)
10. Multi-server at scale (several dozen VPS, load management on the central side)

---

## 9. Still-open points (to settle before implementation)

- Detailed unified data model for the generic "Service" (exact metadata schema per type)
- Exact send frequency and load-management strategy if many servers push simultaneously
- Exact format of custom pattern-matching rules (syntax, where they live in the config)
- Precise strategy for the Kubernetes cgroups fallback when metrics-server is absent
