package com.monitoring.sentinel.agent.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.sentinel.agent.network.UnixSocketHttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detected via /var/run/docker.sock (architecture doc, section 3.2). The most structured
 * case: per-container stats via /containers/{id}/stats (CPU, RAM, network, I/O), plus
 * writable-layer disk size via /containers/json?size=true, plus raw logs (section 6.2/7)
 * via /containers/{id}/logs.
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
 * reason. Log fetching only handles the non-TTY case (containers started without `-t`,
 * i.e. the vast majority run via `docker run -d` or compose): Docker multiplexes
 * stdout/stderr into framed chunks in that case, but sends plain unframed text for
 * TTY-allocated containers - this doesn't detect or handle that second case, logs from a
 * TTY container would come through corrupted.
 */
public class DockerAdapter implements ServiceAdapter, LogSource {

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
			// size=true adds SizeRw/SizeRootFs to each entry - avoids a second per-container
			// call just to get disk usage.
			JsonNode containers = requestJson("/containers/json?size=true");
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
		// SizeRw: the container's own writable layer, not the shared read-only image layers
		// underneath it - matches what `docker ps --size` shows as SIZE (not "virtual size").
		long diskMb = container.path("SizeRw").asLong(0) / (1024 * 1024);

		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("image", image);
		metadata.put("container_id", containerId.substring(0, Math.min(12, containerId.length())));
		String swarmService = container.path("Labels").path("com.docker.swarm.service.name").asText(null);
		if (swarmService != null) {
			metadata.put("swarm_service", swarmService);
		}

		return new DiscoveredService(
				"docker:" + stableName, rawName, "docker", "running", cpuPercent, memMb, diskMb, metadata);
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

	// First-ever fetch for a service (no cursor yet) looks back this far instead of using
	// Docker's own "tail=N" - discovered live that --tail forces the json-file driver to
	// scan/seek through the *entire* log file to count lines from the end, which took over
	// 5s (our read timeout) on a container with hours of accumulated output. Filtering by
	// "since" instead doesn't need that scan, and a monitoring tool wants recent logs on
	// first sight anyway, not literally the last N lines however old they are.
	private static final Duration FIRST_FETCH_LOOKBACK = Duration.ofMinutes(5);

	/**
	 * nativeId is the container id (short form, from metadata "container_id" - Docker
	 * resolves short ids fine). since=null means "no cursor yet" - the caller's cursor
	 * then takes over from here.
	 */
	@Override
	public List<LogLine> fetchLogs(String nativeId, Instant since) {
		Instant effectiveSince = since != null ? since : Instant.now().minus(FIRST_FETCH_LOOKBACK);
		String query = "?stdout=true&stderr=true&timestamps=true&since=" + effectiveSince.getEpochSecond();
		try {
			byte[] raw = UnixSocketHttpClient.getBytes(DOCKER_SOCKET, "/containers/" + nativeId + "/logs" + query);
			return demux(raw);
		} catch (IOException | RuntimeException e) {
			System.err.println("DockerAdapter: fetching logs for " + nativeId + " failed: " + e.getMessage());
			return List.of();
		}
	}

	/** Docker's log stream framing (non-TTY only, see class doc): each frame is an 8-byte
	 * header (1 byte stream type, 3 zero bytes, 4-byte big-endian payload length) followed
	 * by that many bytes of payload - one or more "<timestamp> <message>\n" lines. */
	/** Package-private for direct unit testing (see the class doc for the framing spec). */
	List<LogLine> demux(byte[] raw) {
		List<LogLine> lines = new ArrayList<>();
		int pos = 0;
		while (pos + 8 <= raw.length) {
			int size = ((raw[pos + 4] & 0xFF) << 24) | ((raw[pos + 5] & 0xFF) << 16)
					| ((raw[pos + 6] & 0xFF) << 8) | (raw[pos + 7] & 0xFF);
			int payloadStart = pos + 8;
			int payloadEnd = Math.min(payloadStart + size, raw.length);
			if (size < 0 || payloadStart > raw.length) {
				break;
			}
			String payload = new String(raw, payloadStart, payloadEnd - payloadStart, StandardCharsets.UTF_8);
			for (String line : payload.split("\n")) {
				if (!line.isBlank()) {
					lines.add(parseLine(line));
				}
			}
			pos = payloadEnd;
		}
		return lines;
	}

	private LogLine parseLine(String line) {
		int spaceIndex = line.indexOf(' ');
		if (spaceIndex < 0) {
			return new LogLine(Instant.now(), classifyLevel(line), line);
		}
		String timestampPart = line.substring(0, spaceIndex);
		String message = line.substring(spaceIndex + 1);
		try {
			return new LogLine(Instant.parse(timestampPart), classifyLevel(message), message);
		} catch (DateTimeParseException e) {
			// Not actually a timestamp prefix - treat the whole line as the message.
			return new LogLine(Instant.now(), classifyLevel(line), line);
		}
	}

	/** No structured level in Docker's own log format - a generic keyword heuristic, same
	 * spirit as the default regex rules described for free-text logs (architecture doc,
	 * section 7.2). Exact rule format is still an open point there. */
	private String classifyLevel(String message) {
		String lower = message.toLowerCase(Locale.ROOT);
		if (lower.contains("error") || lower.contains("exception")) {
			return "error";
		}
		if (lower.contains("warn")) {
			return "warn";
		}
		return "info";
	}
}
