package com.monitoring.sentinel.agent;

import com.monitoring.sentinel.agent.collector.NativeStatsClient;
import com.monitoring.sentinel.agent.collector.SystemStats;
import com.monitoring.sentinel.agent.config.AgentConfig;
import com.monitoring.sentinel.agent.config.ConfigLoader;
import com.monitoring.sentinel.agent.discovery.DiscoveredService;
import com.monitoring.sentinel.agent.discovery.DockerAdapter;
import com.monitoring.sentinel.agent.discovery.KubernetesAdapter;
import com.monitoring.sentinel.agent.discovery.Pm2Adapter;
import com.monitoring.sentinel.agent.discovery.ProcessAdapter;
import com.monitoring.sentinel.agent.discovery.ServiceAdapter;
import com.monitoring.sentinel.agent.dto.DiskUsagePayload;
import com.monitoring.sentinel.agent.dto.MetricsPayload;
import com.monitoring.sentinel.agent.dto.NetworkUsagePayload;
import com.monitoring.sentinel.agent.dto.ServicePayload;
import com.monitoring.sentinel.agent.dto.SystemPayload;
import com.monitoring.sentinel.agent.network.LocalBuffer;
import com.monitoring.sentinel.agent.network.PushClient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

		System.out.println("Sentinel agent starting - central=" + config.getCentralUrl()
				+ ", pushIntervalSeconds=" + config.getPushIntervalSeconds());

		while (true) {
			runOnce(nativeStatsClient, adapters, pushClient, buffer);
			sleep(Duration.ofSeconds(config.getPushIntervalSeconds()));
		}
	}

	private static void runOnce(
			NativeStatsClient nativeStatsClient, List<ServiceAdapter> adapters,
			PushClient pushClient, LocalBuffer buffer) {
		try {
			SystemStats systemStats = nativeStatsClient.readLatest();
			List<DiscoveredService> services = adapters.stream()
					.filter(ServiceAdapter::isAvailable)
					.flatMap(adapter -> adapter.discover().stream())
					.collect(Collectors.toList());

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
