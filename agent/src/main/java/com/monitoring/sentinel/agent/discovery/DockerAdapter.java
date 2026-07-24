package com.monitoring.sentinel.agent.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.sentinel.agent.network.UnixSocketHttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detected via /var/run/docker.sock (architecture doc, section 3.2). The most structured
 * case: per-container stats via /containers/{id}/stats (CPU, RAM, network, I/O).
 *
 * Talks to the Docker Engine API directly over the unix socket (see UnixSocketHttpClient) -
 * no Docker SDK dependency, matching the "agent stays lightweight" goal. Uses unversioned
 * API paths ("/containers/json" rather than "/v1.51/containers/json") so it isn't pinned
 * to one daemon version.
 *
 * NOT TESTED against a live Docker daemon (none available while building this) - the CPU%
 * formula matches Docker's own documented calculation and the JSON field names match the
 * documented Engine API schema, but this needs real verification before being trusted.
 * Swarm service detection (com.docker.swarm.service.name label) is untested for the same
 * reason.
 */
public class DockerAdapter implements ServiceAdapter {

	private static final Path DOCKER_SOCKET = Path.of("/var/run/docker.sock");
	private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-z0-9_-]");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public boolean isAvailable() {
		return Files.exists(DOCKER_SOCKET);
	}

	@Override
	public List<DiscoveredService> discover() {
		try {
			JsonNode containers = requestJson("/containers/json");
			List<DiscoveredService> services = new ArrayList<>();
			for (JsonNode container : containers) {
				try {
					services.add(toDiscoveredService(container));
				} catch (RuntimeException | IOException e) {
					System.err.println("DockerAdapter: skipping a container: " + e.getMessage());
				}
			}
			return services;
		} catch (RuntimeException | IOException e) {
			// One adapter's failure shouldn't take the whole collection cycle down with it -
			// system-level metrics and other adapters still deserve to be pushed this round.
			System.err.println("DockerAdapter: discovery failed: " + e.getMessage());
			return List.of();
		}
	}

	private DiscoveredService toDiscoveredService(JsonNode container) throws IOException {
		String containerId = container.path("Id").asText();
		String rawName = container.path("Names").get(0).asText().replaceFirst("^/", "");
		String stableName = sanitizeName(rawName);
		String image = container.path("Image").asText("");

		JsonNode stats = requestJson("/containers/" + containerId + "/stats?stream=false");
		double cpuPercent = computeCpuPercent(stats);
		long memMb = computeMemMb(stats);

		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("image", image);
		metadata.put("container_id", containerId.substring(0, Math.min(12, containerId.length())));
		String swarmService = container.path("Labels").path("com.docker.swarm.service.name").asText(null);
		if (swarmService != null) {
			metadata.put("swarm_service", swarmService);
		}

		return new DiscoveredService(
				"docker:" + stableName, rawName, "docker", "running", cpuPercent, memMb, metadata);
	}

	/** Docker's own CPU% formula (as used by `docker stats`): delta of container CPU usage
	 * over delta of total system CPU usage, scaled by online CPU count. */
	private double computeCpuPercent(JsonNode stats) {
		long cpuTotal = stats.path("cpu_stats").path("cpu_usage").path("total_usage").asLong(0);
		long preCpuTotal = stats.path("precpu_stats").path("cpu_usage").path("total_usage").asLong(0);
		long systemCpu = stats.path("cpu_stats").path("system_cpu_usage").asLong(0);
		long preSystemCpu = stats.path("precpu_stats").path("system_cpu_usage").asLong(0);
		long onlineCpus = stats.path("cpu_stats").path("online_cpus").asLong(1);

		long cpuDelta = cpuTotal - preCpuTotal;
		long systemDelta = systemCpu - preSystemCpu;
		if (systemDelta <= 0 || cpuDelta < 0) {
			return 0.0;
		}
		return ((double) cpuDelta / (double) systemDelta) * onlineCpus * 100.0;
	}

	/** Usage minus page cache, matching what `docker stats` shows (raw usage over-counts
	 * reclaimable page cache as "used" memory). */
	private long computeMemMb(JsonNode stats) {
		long usage = stats.path("memory_stats").path("usage").asLong(0);
		long cache = stats.path("memory_stats").path("stats").path("cache").asLong(0);
		return Math.max(0, usage - cache) / (1024 * 1024);
	}

	private String sanitizeName(String rawName) {
		return INVALID_NAME_CHARS.matcher(rawName.toLowerCase(Locale.ROOT)).replaceAll("-");
	}

	private JsonNode requestJson(String path) throws IOException {
		String body = UnixSocketHttpClient.get(DOCKER_SOCKET, path);
		return objectMapper.readTree(body);
	}
}
