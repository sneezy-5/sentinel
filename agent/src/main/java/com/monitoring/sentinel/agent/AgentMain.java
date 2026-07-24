package com.monitoring.sentinel.agent;

import com.monitoring.sentinel.agent.collector.NativeStatsClient;
import com.monitoring.sentinel.agent.collector.SystemStats;
import com.monitoring.sentinel.agent.config.AgentConfig;
import com.monitoring.sentinel.agent.config.ConfigLoader;
import com.monitoring.sentinel.agent.discovery.DiscoveredService;
import com.monitoring.sentinel.agent.discovery.DockerAdapter;
import com.monitoring.sentinel.agent.discovery.KubernetesAdapter;
import com.monitoring.sentinel.agent.discovery.LogSource;
import com.monitoring.sentinel.agent.discovery.Pm2Adapter;
import com.monitoring.sentinel.agent.discovery.ProcessAdapter;
import com.monitoring.sentinel.agent.discovery.ServiceAdapter;
import com.monitoring.sentinel.agent.dto.DiskUsagePayload;
import com.monitoring.sentinel.agent.dto.LogBatchPayload;
import com.monitoring.sentinel.agent.dto.LogEntryPayload;
import com.monitoring.sentinel.agent.dto.MetricsPayload;
import com.monitoring.sentinel.agent.dto.NetworkUsagePayload;
import com.monitoring.sentinel.agent.dto.ServicePayload;
import com.monitoring.sentinel.agent.dto.SystemPayload;
import com.monitoring.sentinel.agent.network.LocalBuffer;
import com.monitoring.sentinel.agent.network.PushClient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent entry point (architecture doc, section 3). Loop: discovery -> full snapshot -> push.
 * No Spring: startup is intentionally simple and free of heavy dependencies (section 3.1).
 */
public final class AgentMain {

	private static final Duration BUFFER_MAX_AGE = Duration.ofHours(1);

	public static void main(String[] args) {
		Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("/etc/sentinel/monitoring-agent.yml");
		AgentConfig config = ConfigLoader.load(configPath);

		if (config.getCentralUrl() == null || config.getToken() == null) {
			System.err.println("centralUrl/token missing in " + configPath + " - the agent cannot push data.");
		}

		NativeStatsClient nativeStatsClient = new NativeStatsClient(Path.of("/run/sentinel/system_stats.json"));
		PushClient pushClient = new PushClient(config.getCentralUrl(), config.getToken());
		LocalBuffer buffer = new LocalBuffer(Path.of("/var/lib/sentinel/buffer.jsonl"), BUFFER_MAX_AGE);

		List<ServiceAdapter> adapters = List.of(
				new DockerAdapter(), new Pm2Adapter(), new KubernetesAdapter(), new ProcessAdapter());

		// Per-service "last seen log timestamp", so each cycle only fetches new lines
		// (architecture doc, section 6.2: "curseur/offset local par source de log"). In
		// memory only - lost on restart, at which point a service's LogSource re-sends its
		// last ~100 lines once rather than picking up exactly where it left off.
		Map<String, Instant> logCursors = new HashMap<>();

		System.out.println("Sentinel agent starting - central=" + config.getCentralUrl()
				+ ", pushIntervalSeconds=" + config.getPushIntervalSeconds());

		while (true) {
			runOnce(nativeStatsClient, adapters, pushClient, buffer, logCursors);
			sleep(Duration.ofSeconds(config.getPushIntervalSeconds()));
		}
	}

	private static void runOnce(
			NativeStatsClient nativeStatsClient, List<ServiceAdapter> adapters,
			PushClient pushClient, LocalBuffer buffer, Map<String, Instant> logCursors) {
		try {
			SystemStats systemStats = nativeStatsClient.readLatest();

			// Not a flattened stream this time: log fetching needs to know which adapter
			// discovered which service, so the per-adapter grouping has to survive this pass.
			List<DiscoveredService> services = new ArrayList<>();
			for (ServiceAdapter adapter : adapters) {
				if (!adapter.isAvailable()) {
					continue;
				}
				List<DiscoveredService> discovered = adapter.discover();
				services.addAll(discovered);
				if (adapter instanceof LogSource logSource) {
					for (DiscoveredService service : discovered) {
						pushLogsForService(logSource, service, pushClient, logCursors);
					}
				}
			}

			MetricsPayload payload = toPayload(systemStats, services);
			if (pushClient.pushMetrics(payload)) {
				System.out.println("Pushed metrics (" + services.size() + " services) at " + payload.timestamp());
			} else {
				System.err.println("Push failed, buffering locally: " + payload.timestamp());
				buffer.append(payload.toString());
			}
		} catch (RuntimeException e) {
			System.err.println("Collection cycle failed: " + e.getMessage());
		}
	}

	private static void pushLogsForService(
			LogSource logSource, DiscoveredService service, PushClient pushClient, Map<String, Instant> logCursors) {
		String nativeId = service.metadata() != null ? service.metadata().get("container_id") : null;
		if (nativeId == null) {
			return;
		}

		Instant since = logCursors.get(service.id());
		List<LogSource.LogLine> lines = logSource.fetchLogs(nativeId, since);
		if (lines.isEmpty()) {
			return;
		}

		List<LogEntryPayload> entries = lines.stream()
				.map(line -> new LogEntryPayload(line.timestamp(), line.level(), line.message()))
				.collect(Collectors.toList());
		if (pushClient.pushLogs(new LogBatchPayload(service.id(), entries))) {
			logCursors.put(service.id(), lines.get(lines.size() - 1).timestamp());
		} else {
			// Unlike metrics, log batches aren't buffered locally on failure - a lost batch
			// just means those lines are skipped, not retried (LocalBuffer.drain() isn't
			// wired to anything that resends it yet, so pretending to buffer here wouldn't
			// actually help - see the root README's Status section).
			System.err.println("Failed to push logs for " + service.id());
		}
	}

	private static MetricsPayload toPayload(SystemStats stats, List<DiscoveredService> services) {
		SystemPayload systemPayload = new SystemPayload(
				stats.cpuPercent(),
				stats.cpuCores(),
				stats.ramUsedMb(),
				stats.ramTotalMb(),
				stats.disks().stream()
						.map(d -> new DiskUsagePayload(d.mount(), d.usedGb(), d.totalGb()))
						.collect(Collectors.toList()),
				new NetworkUsagePayload(stats.rxBytes(), stats.txBytes()));

		List<ServicePayload> servicePayloads = services.stream()
				.map(s -> new ServicePayload(s.id(), s.name(), s.type(), s.status(), s.cpuPercent(), s.memMb(), s.diskMb(), s.metadata()))
				.collect(Collectors.toList());

		return new MetricsPayload(Instant.now(), systemPayload, servicePayloads);
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private AgentMain() {
	}
}
