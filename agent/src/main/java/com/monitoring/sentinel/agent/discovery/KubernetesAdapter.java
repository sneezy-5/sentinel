package com.monitoring.sentinel.agent.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.sentinel.agent.network.K8sApiClient;

import java.io.IOException;
import java.net.URLEncoder;
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
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Detected via an active kubelet / present kubeconfig (architecture doc, section 3.2).
 * discover() only implements the in-cluster case (a DaemonSet pod with the standard mounted
 * service account token/CA - see K8sApiClient): isAvailable() also returns true from a bare
 * KUBECONFIG on a non-pod host, but discover() there will fail to find the token file and
 * just log + return no services, same fail-soft behavior as every other adapter here.
 *
 * The DaemonSet pod spec needs a NODE_NAME env var sourced from
 * `valueFrom.fieldRef.fieldPath: spec.nodeName` (the Downward API doesn't expose this as a
 * mounted file the way pod name/namespace are) so discover() only reports pods on this node
 * - without it every replica of the DaemonSet would report every pod in the cluster. RBAC:
 * the service account needs "list" on pods (and on metrics.k8s.io PodMetrics, if
 * metrics-server is present).
 *
 * CPU/RAM come from metrics-server (`/apis/metrics.k8s.io/v1beta1/pods`) when it's
 * installed; if that 404s, pods are reported with 0/0 rather than fabricated numbers - the
 * doc-mentioned cgroups fallback ("fragile, open point") isn't implemented here. Pods also
 * have no writable-layer disk figure the way Docker containers have SizeRw, so diskMb is
 * always 0.
 *
 * Only a pod's first container is treated as "the" service (metadata "container") and as
 * the log source - multi-container pods (sidecars) are a known simplification, same spirit
 * as DockerAdapter treating one container as one service.
 *
 * NOT TESTED against a live cluster (none available while building this) - see K8sApiClient.
 */
public class KubernetesAdapter implements ServiceAdapter, LogSource {

	private static final Path SERVICE_ACCOUNT_TOKEN =
			Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
	private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-z0-9_-]");

	// First-ever fetch for a service (no cursor yet): same reasoning as DockerAdapter - recent
	// logs on first sight, not an arbitrarily large backlog.
	private static final Duration FIRST_FETCH_LOOKBACK = Duration.ofMinutes(5);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private K8sApiClient client;

	@Override
	public boolean isAvailable() {
		return Files.exists(SERVICE_ACCOUNT_TOKEN) || System.getenv("KUBECONFIG") != null;
	}

	@Override
	public List<DiscoveredService> discover() {
		String nodeName = System.getenv("NODE_NAME");
		if (nodeName == null || nodeName.isBlank()) {
			System.err.println("KubernetesAdapter: NODE_NAME is not set - can't scope pods to this node, "
					+ "skipping (the DaemonSet pod spec needs env NODE_NAME from fieldRef spec.nodeName)");
			return List.of();
		}
		try {
			K8sApiClient apiClient = client();
			JsonNode pods = fetchPods(apiClient, nodeName);
			Map<String, double[]> usage = fetchPodMetrics(apiClient);

			List<DiscoveredService> services = new ArrayList<>();
			for (JsonNode pod : pods.path("items")) {
				try {
					services.add(toDiscoveredService(pod, usage));
				} catch (RuntimeException e) {
					System.err.println("KubernetesAdapter: skipping a pod: " + e.getMessage());
				}
			}
			return services;
		} catch (IOException | RuntimeException e) {
			System.err.println("KubernetesAdapter: discovery failed: " + e.getMessage());
			return List.of();
		}
	}

	private K8sApiClient client() throws IOException {
		if (client == null) {
			client = new K8sApiClient();
		}
		return client;
	}

	private JsonNode fetchPods(K8sApiClient apiClient, String nodeName) throws IOException {
		String fieldSelector =
				URLEncoder.encode("spec.nodeName=" + nodeName + ",status.phase=Running", StandardCharsets.UTF_8);
		return objectMapper.readTree(apiClient.get("/api/v1/pods?fieldSelector=" + fieldSelector));
	}

	/** Best-effort: "namespace/name" -> {cpuCores, memBytes} summed across a pod's
	 * containers. Empty if metrics-server isn't installed (see class doc). */
	private Map<String, double[]> fetchPodMetrics(K8sApiClient apiClient) {
		try {
			Optional<String> body = apiClient.getOptional("/apis/metrics.k8s.io/v1beta1/pods");
			if (body.isEmpty()) {
				return Map.of();
			}
			JsonNode root = objectMapper.readTree(body.get());
			Map<String, double[]> usage = new LinkedHashMap<>();
			for (JsonNode item : root.path("items")) {
				String key = podKey(item.path("metadata"));
				double cpuCores = 0;
				double memBytes = 0;
				for (JsonNode container : item.path("containers")) {
					cpuCores += parseQuantity(container.path("usage").path("cpu").asText("0"));
					memBytes += parseQuantity(container.path("usage").path("memory").asText("0"));
				}
				usage.put(key, new double[] {cpuCores, memBytes});
			}
			return usage;
		} catch (IOException | RuntimeException e) {
			System.err.println(
					"KubernetesAdapter: metrics-server call failed, reporting 0 cpu/mem this cycle: " + e.getMessage());
			return Map.of();
		}
	}

	private String podKey(JsonNode metadata) {
		return metadata.path("namespace").asText("") + "/" + metadata.path("name").asText("");
	}

	private DiscoveredService toDiscoveredService(JsonNode pod, Map<String, double[]> usage) {
		String namespace = pod.path("metadata").path("namespace").asText("");
		String podName = pod.path("metadata").path("name").asText("");
		if (namespace.isEmpty() || podName.isEmpty()) {
			throw new IllegalArgumentException("pod is missing metadata.namespace/name");
		}
		JsonNode containers = pod.path("spec").path("containers");
		if (!containers.isArray() || containers.isEmpty()) {
			throw new IllegalArgumentException(namespace + "/" + podName + " has no containers");
		}
		String containerName = containers.get(0).path("name").asText("");

		boolean running = "Running".equalsIgnoreCase(pod.path("status").path("phase").asText(""));
		double[] stats = usage.getOrDefault(namespace + "/" + podName, new double[] {0, 0});
		// Same convention as DockerAdapter's CPU%: fraction of a single core, so a pod fully
		// using 2 cores reports 200%, not clamped to 100%.
		double cpuPercent = stats[0] * 100.0;
		long memMb = (long) (stats[1] / (1024 * 1024));

		String stableName = sanitizeName(namespace + "-" + podName);
		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("namespace", namespace);
		metadata.put("container", containerName);
		metadata.put("node", pod.path("spec").path("nodeName").asText(""));
		metadata.put("log_native_id", namespace + "/" + podName + "/" + containerName);

		return new DiscoveredService(
				"k8s:" + stableName, namespace + "/" + podName, "k8s", running ? "running" : "stopped",
				cpuPercent, memMb, 0, metadata);
	}

	private String sanitizeName(String rawName) {
		return INVALID_NAME_CHARS.matcher(rawName.toLowerCase(Locale.ROOT)).replaceAll("-");
	}

	/**
	 * nativeId is "namespace/pod/container" (from metadata "log_native_id" - see
	 * AgentMain). since=null means "no cursor yet" - same first-fetch lookback as
	 * DockerAdapter, the caller's cursor takes over from here.
	 */
	@Override
	public List<LogLine> fetchLogs(String nativeId, Instant since) {
		String[] parts = nativeId.split("/", 3);
		if (parts.length != 3) {
			System.err.println("KubernetesAdapter: malformed log nativeId: " + nativeId);
			return List.of();
		}
		String namespace = parts[0];
		String pod = parts[1];
		String container = parts[2];
		Instant effectiveSince = since != null ? since : Instant.now().minus(FIRST_FETCH_LOOKBACK);
		long sinceSeconds = Math.max(1, Duration.between(effectiveSince, Instant.now()).getSeconds());

		try {
			String path = "/api/v1/namespaces/" + namespace + "/pods/" + pod + "/log"
					+ "?container=" + URLEncoder.encode(container, StandardCharsets.UTF_8)
					+ "&timestamps=true&sinceSeconds=" + sinceSeconds;
			return parseLines(client().get(path));
		} catch (IOException | RuntimeException e) {
			System.err.println("KubernetesAdapter: fetching logs for " + nativeId + " failed: " + e.getMessage());
			return List.of();
		}
	}

	/** Kubernetes' pod log endpoint returns plain "<RFC3339 timestamp> <message>\n" lines (no
	 * framing to undo, unlike Docker's raw socket endpoint) when timestamps=true is passed.
	 * Package-private for direct unit testing. */
	List<LogLine> parseLines(String text) {
		List<LogLine> lines = new ArrayList<>();
		for (String line : text.split("\n")) {
			if (!line.isBlank()) {
				lines.add(parseLine(line));
			}
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

	/** No structured level in Kubernetes' own log format either - same keyword heuristic as
	 * DockerAdapter (architecture doc, section 7.2). */
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

	/** Parses a Kubernetes resource quantity (cpu e.g. "250m"/"2", memory e.g.
	 * "128974848"/"512Mi") into its base unit (cores for cpu, bytes for memory) - binary
	 * (Ki/Mi/Gi/Ti/Pi/Ei, 1024-based) and decimal (n/u/m/k/M/G/T/P/E, 1000-based) suffixes
	 * per the Kubernetes quantity spec. Package-private for direct unit testing. */
	static double parseQuantity(String raw) {
		if (raw == null || raw.isEmpty()) {
			return 0;
		}
		String[] binarySuffixes = {"Ki", "Mi", "Gi", "Ti", "Pi", "Ei"};
		double[] binaryFactors = {
				Math.pow(2, 10), Math.pow(2, 20), Math.pow(2, 30), Math.pow(2, 40), Math.pow(2, 50), Math.pow(2, 60)};
		for (int i = 0; i < binarySuffixes.length; i++) {
			if (raw.endsWith(binarySuffixes[i])) {
				return Double.parseDouble(raw.substring(0, raw.length() - 2)) * binaryFactors[i];
			}
		}
		String[] decimalSuffixes = {"n", "u", "m", "k", "M", "G", "T", "P", "E"};
		double[] decimalFactors = {1e-9, 1e-6, 1e-3, 1e3, 1e6, 1e9, 1e12, 1e15, 1e18};
		for (int i = 0; i < decimalSuffixes.length; i++) {
			if (raw.endsWith(decimalSuffixes[i])) {
				return Double.parseDouble(raw.substring(0, raw.length() - 1)) * decimalFactors[i];
			}
		}
		return Double.parseDouble(raw);
	}
}
