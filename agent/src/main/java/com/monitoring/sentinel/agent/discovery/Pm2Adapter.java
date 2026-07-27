package com.monitoring.sentinel.agent.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Detected via a live PM2 daemon (~/.pm2/pm2.pid) - list/stats via "pm2 jlist" (architecture
 * doc, section 3.2). No per-process network/disk stats (known limitation of PM2 itself, not
 * just this adapter).
 *
 * The agent normally runs as root (see the systemd unit), while PM2 is normally started
 * under an unprivileged user's account - its state (and what "pm2 jlist" needs to talk to)
 * lives under that user's $HOME/.pm2, not /root/.pm2. Rather than requiring the operator to
 * configure which user, this scans /home/* for live daemons and points the "pm2" CLI at each
 * one in turn via the PM2_HOME env var - PM2's own supported way to target a specific
 * instance, no "sudo -u" or capability changes needed.
 *
 * Merges the process lists from every live daemon found (own home + each /home/* account),
 * not just the first one: any account - root's own $HOME included - can have a pm2.pid from
 * a daemon PM2 auto-started for some unrelated one-off "pm2 ..." invocation (that's normal
 * PM2 behavior, not a sign anything is wrong), and that daemon legitimately manages zero
 * processes. Stopping at the first pm2.pid found - which used to be root's own, checked
 * first - meant a single stray `pm2 ls` run as root silently hid every real process running
 * under an actual user's account from then on, with nothing about it looking like a failure.
 *
 * Also a LogSource: PM2 already centralizes each process' stdout/stderr under
 * <PM2_HOME>/logs (architecture doc, section 3.2) - "pm2 jlist" conveniently already reports
 * the exact paths per process (pm_out_log_path/pm_err_log_path), cluster-mode instance
 * numbering and all, so there's no need to reconstruct them from the process name. Tailed by
 * byte offset per file (own instance state), not the timestamp `since` cursor AgentMain
 * otherwise uses - same reasoning as NginxAdapter: a raw file has no "give me everything
 * since time X" query, and PM2's own log files aren't reliably timestamped in the first
 * place (only if the app itself logs one, or PM2 was started with --time).
 */
public class Pm2Adapter implements ServiceAdapter, LogSource {

	private static final Path HOME_DIR = Path.of("/home");
	private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-z0-9_-]");
	private static final long JLIST_TIMEOUT_SECONDS = 5;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private boolean loggedUnavailable = false;
	private final Map<Path, Long> logFileOffsets = new HashMap<>();

	// isAvailable() only checks for *some* .pm2 directory (cheap, and logs at most once per
	// agent run when it finds nothing - most servers genuinely have no PM2 at all and
	// shouldn't get a log line every cycle for that, but total silence made this adapter
	// impossible to debug remotely: a real PM2 install this can't reach for any reason - home
	// directory in a non-standard place, $HOME resolving unexpectedly for whichever user the
	// agent runs as, anything - looked identical in the logs to "PM2 isn't installed".
	// discover() does the stricter check (a *live* daemon's pm2.pid) and logs every cycle
	// when that fails despite a .pm2 directory existing, matching DockerAdapter's tiering:
	// silent if not applicable to this server, loud if it looks like it should work but
	// doesn't.
	@Override
	public boolean isAvailable() {
		boolean available = anyPm2DirectoryExists();
		if (!available && !loggedUnavailable) {
			loggedUnavailable = true;
			System.err.println("Pm2Adapter: no .pm2 directory found under " + System.getProperty("user.home")
					+ " or any /home/* account - not detecting PM2 on this server. (Logged once per agent run.)");
		}
		return available;
	}

	@Override
	public List<DiscoveredService> discover() {
		List<Path> pm2Homes = findLivePm2Homes();
		if (pm2Homes.isEmpty()) {
			System.err.println("Pm2Adapter: found a .pm2 directory but no live daemon (<home>/.pm2/pm2.pid) under "
					+ System.getProperty("user.home") + " or any /home/* account - is PM2 actually running, and if "
					+ "so, can the user this agent runs as (see systemd unit's User=) traverse into the PM2 "
					+ "process owner's home directory?");
			return List.of();
		}
		List<DiscoveredService> services = new ArrayList<>();
		for (Path pm2Home : pm2Homes) {
			try {
				services.addAll(parseJlist(runJlist(pm2Home)));
			} catch (IOException | RuntimeException e) {
				// One daemon failing to answer shouldn't hide processes a different, healthy
				// daemon (a different account) already reported this same cycle.
				System.err.println("Pm2Adapter: discovery failed for PM2_HOME=" + pm2Home + ": " + e.getMessage());
			}
		}
		return services;
	}

	private boolean anyPm2DirectoryExists() {
		if (Files.isDirectory(Path.of(System.getProperty("user.home"), ".pm2"))) {
			return true;
		}
		if (!Files.isDirectory(HOME_DIR)) {
			return false;
		}
		try (var entries = Files.list(HOME_DIR)) {
			return entries.anyMatch(dir -> Files.isDirectory(dir.resolve(".pm2")));
		} catch (IOException e) {
			return false;
		}
	}

	/** Every account with a live daemon (own home + each /home/* account) - not just the
	 * first one found, see the class doc for why. */
	private List<Path> findLivePm2Homes() {
		List<Path> homes = new ArrayList<>();
		Path ownHome = Path.of(System.getProperty("user.home"), ".pm2");
		if (Files.exists(ownHome.resolve("pm2.pid"))) {
			homes.add(ownHome);
		}
		if (Files.isDirectory(HOME_DIR)) {
			try (var entries = Files.list(HOME_DIR)) {
				entries.map(dir -> dir.resolve(".pm2"))
						.filter(pm2Dir -> Files.exists(pm2Dir.resolve("pm2.pid")))
						.forEach(homes::add);
			} catch (IOException e) {
				// Best effort - whatever was already found (e.g. own home) is still returned.
			}
		}
		return homes;
	}

	/** Package-private for direct unit testing against a canned "pm2 jlist" payload. */
	List<DiscoveredService> parseJlist(String json) throws IOException {
		JsonNode root = objectMapper.readTree(json);
		List<DiscoveredService> services = new ArrayList<>();
		for (JsonNode process : root) {
			try {
				services.add(toDiscoveredService(process));
			} catch (RuntimeException e) {
				System.err.println("Pm2Adapter: skipping a process: " + e.getMessage());
			}
		}
		return services;
	}

	private DiscoveredService toDiscoveredService(JsonNode process) {
		String rawName = process.path("name").asText();
		String stableName = sanitizeName(rawName);
		JsonNode env = process.path("pm2_env");

		// "online" is PM2's own term for a healthy running process (others: stopping,
		// stopped, launching, errored, one-launch-status) - collapsed to the same
		// running/stopped binary IngestionService already expects (matches DockerAdapter).
		boolean online = "online".equalsIgnoreCase(env.path("status").asText(""));
		double cpuPercent = process.path("monit").path("cpu").asDouble(0);
		long memMb = process.path("monit").path("memory").asLong(0) / (1024 * 1024);

		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("pm2_id", process.path("pm_id").asText(""));
		metadata.put("restarts", String.valueOf(env.path("restart_time").asInt(0)));

		// "pm2 jlist" already reports the exact log paths per process (cluster-mode instance
		// numbering and all) - encode both into the one string fetchLogs()/AgentMain expect,
		// same "|"-joined trick KubernetesAdapter uses for its own multi-part nativeId. Empty
		// on either side just means that stream doesn't have a log file (fetchLogs skips it).
		String outLog = env.path("pm_out_log_path").asText("");
		String errLog = env.path("pm_err_log_path").asText("");
		metadata.put("log_native_id", outLog + "|" + errLog);

		// No disk figure from PM2 itself - 0 rather than a fabricated number.
		return new DiscoveredService(
				"pm2:" + stableName, rawName, "pm2", online ? "running" : "stopped", cpuPercent, memMb, 0, metadata);
	}

	@Override
	public List<LogLine> fetchLogs(String nativeId, Instant since) {
		String[] parts = nativeId.split("\\|", 2);
		String outLog = parts.length > 0 ? parts[0] : "";
		String errLog = parts.length > 1 ? parts[1] : "";

		List<LogLine> lines = new ArrayList<>();
		if (!outLog.isBlank()) {
			for (String raw : tailNewLines(Path.of(outLog))) {
				lines.add(parseLine(raw));
			}
		}
		if (!errLog.isBlank()) {
			for (String raw : tailNewLines(Path.of(errLog))) {
				lines.add(parseLine(raw));
			}
		}
		return lines;
	}

	/** Reads whatever's been appended to path since the last call (byte offset tracked in
	 * logFileOffsets, keyed by path since a single adapter instance tails many services'
	 * files). First call for a given file skips straight to its current end rather than
	 * dumping the existing backlog - these logs can already be sizeable by the time the
	 * agent first sees them. Package-private for direct unit testing against temp files. */
	List<String> tailNewLines(Path path) {
		try {
			long fileSize = Files.size(path);
			Long previousOffset = logFileOffsets.get(path);
			if (previousOffset == null) {
				logFileOffsets.put(path, fileSize);
				return List.of();
			}
			// Rotated/truncated since last time - start over rather than seek past EOF.
			long offset = fileSize < previousOffset ? 0 : previousOffset;
			if (offset == fileSize) {
				return List.of();
			}
			try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
				file.seek(offset);
				List<String> lines = new ArrayList<>();
				String line;
				while ((line = file.readLine()) != null) {
					// RandomAccessFile.readLine() decodes as ISO-8859-1 (its own documented
					// behavior, not real charset detection) - re-decode as UTF-8.
					lines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
				}
				logFileOffsets.put(path, file.getFilePointer());
				return lines;
			}
		} catch (IOException | UncheckedIOException e) {
			// Missing/unreadable file (deleted, permissions) - skip this cycle, not fatal to
			// the other log stream or the rest of discovery.
			System.err.println("Pm2Adapter: reading " + path + " failed: " + e.getMessage());
			return List.of();
		}
	}

	/** PM2's raw stdout/stderr passthrough has no guaranteed timestamp prefix (only present
	 * if the app itself logs one, or PM2 was started with --time) - same defensive "try to
	 * parse the first token as a timestamp, keep the whole line otherwise" pattern as
	 * DockerAdapter. Package-private for direct unit testing. */
	LogLine parseLine(String line) {
		int spaceIndex = line.indexOf(' ');
		if (spaceIndex < 0) {
			return new LogLine(Instant.now(), classifyLevel(line), line);
		}
		String timestampPart = line.substring(0, spaceIndex);
		String message = line.substring(spaceIndex + 1);
		try {
			return new LogLine(Instant.parse(timestampPart), classifyLevel(message), message);
		} catch (DateTimeParseException e) {
			return new LogLine(Instant.now(), classifyLevel(line), line);
		}
	}

	/** No structured level in PM2's own log format - same keyword heuristic as
	 * DockerAdapter/KubernetesAdapter (architecture doc, section 7.2). */
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

	private String sanitizeName(String rawName) {
		return INVALID_NAME_CHARS.matcher(rawName.toLowerCase(Locale.ROOT)).replaceAll("-");
	}

	private String runJlist(Path pm2Home) throws IOException {
		ProcessBuilder builder = new ProcessBuilder("pm2", "jlist").redirectErrorStream(true);
		builder.environment().put("PM2_HOME", pm2Home.toString());
		Process process = builder.start();
		process.getOutputStream().close();

		// Drain the output on a separate thread while waiting: pm2 jlist's output (env vars,
		// axm_monitor blobs, etc. - see the real payload this was built against) can exceed
		// the pipe buffer, and reading it only *after* waitFor() risks a classic deadlock
		// (the child blocks writing, we block waiting for it to exit).
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Thread reader = new Thread(() -> {
			try {
				process.getInputStream().transferTo(buffer);
			} catch (IOException e) {
				// Process was killed/stream closed - buffer keeps whatever was read so far.
			}
		});
		reader.setDaemon(true);
		reader.start();

		try {
			boolean finished = process.waitFor(JLIST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IOException("'pm2 jlist' did not exit within " + JLIST_TIMEOUT_SECONDS + "s");
			}
			reader.join(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted waiting for 'pm2 jlist'", e);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}
}
