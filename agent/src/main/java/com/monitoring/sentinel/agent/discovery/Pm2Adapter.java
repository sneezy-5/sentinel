package com.monitoring.sentinel.agent.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * configure which user, this scans /home/* for a live daemon and points the "pm2" CLI at it
 * via the PM2_HOME env var - PM2's own supported way to target a specific instance, no
 * "sudo -u" or capability changes needed.
 */
public class Pm2Adapter implements ServiceAdapter {

	private static final Path HOME_DIR = Path.of("/home");
	private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-z0-9_-]");
	private static final long JLIST_TIMEOUT_SECONDS = 5;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private boolean loggedUnavailable = false;

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
		Path pm2Home = findLivePm2Home();
		if (pm2Home == null) {
			System.err.println("Pm2Adapter: found a .pm2 directory but no live daemon (<home>/.pm2/pm2.pid) under "
					+ System.getProperty("user.home") + " or any /home/* account - is PM2 actually running, and if "
					+ "so, can the user this agent runs as (see systemd unit's User=) traverse into the PM2 "
					+ "process owner's home directory?");
			return List.of();
		}
		try {
			return parseJlist(runJlist(pm2Home));
		} catch (IOException | RuntimeException e) {
			System.err.println("Pm2Adapter: discovery failed: " + e.getMessage());
			return List.of();
		}
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

	private Path findLivePm2Home() {
		Path ownHome = Path.of(System.getProperty("user.home"), ".pm2");
		if (Files.exists(ownHome.resolve("pm2.pid"))) {
			return ownHome;
		}
		if (!Files.isDirectory(HOME_DIR)) {
			return null;
		}
		try (var entries = Files.list(HOME_DIR)) {
			return entries
					.map(dir -> dir.resolve(".pm2"))
					.filter(pm2Dir -> Files.exists(pm2Dir.resolve("pm2.pid")))
					.findFirst()
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
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

		// No disk figure from PM2 itself - 0 rather than a fabricated number.
		return new DiscoveredService(
				"pm2:" + stableName, rawName, "pm2", online ? "running" : "stopped", cpuPercent, memMb, 0, metadata);
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
