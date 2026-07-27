package com.monitoring.sentinel.agent.discovery;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detected via nginx's own log files on the host (architecture doc, section 7.2: "standard
 * access-log formats like nginx" is one of the generic default rules). This only covers a
 * host-installed nginx reading straight off /var/log/nginx/{access,error}.log (paths
 * configurable, monitoring-agent.yml) - a dockerized nginx is already covered by
 * DockerAdapter, just without the structured access-log parsing this adapter does (its
 * lines come through as plain container output there instead).
 *
 * One host commonly fronts several vhosts through the same nginx. If they all share the
 * configured access.log, there's genuinely no field in it identifying which vhost served a
 * given request - nginx just doesn't record that by default - so those requests all land in
 * one catch-all service, "nginx:main" (also where error.log always lands, since nginx
 * doesn't tag those by vhost either).
 *
 * Per-vhost breakdown works *for free*, no nginx config change, for any vhost that already
 * has its own `access_log` directive pointing at its own file (a common setup, and simpler
 * to adopt than a custom log_format for anyone who doesn't already do this - see the README
 * for the one-line change): discover() scans /etc/nginx/{nginx.conf,sites-enabled/*,conf.d/*}
 * for `access_log <path>` directives, and tails every distinct path found (besides the
 * configured main one) as its own service, named after the file (e.g.
 * "onda-backend.access.log" -> "nginx:onda-backend").
 *
 * Unlike every other adapter here, discover() itself does the log tailing (not just
 * fetchLogs()): which services even exist is only knowable *from* the log content/config
 * (the set of vhost log files present), not from some independent "list of configured
 * vhosts" API nginx doesn't expose - so discover() tails+parses+buckets each cycle's new
 * lines by destination service, and fetchLogs() just hands back whatever landed in that
 * service's bucket a moment earlier in the same cycle (AgentMain always calls discover()
 * once, then fetchLogs() once per resulting service, right after - see AgentMain.runOnce()).
 *
 * Log tailing is byte-offset based (own instance state, one offset per file), not the
 * timestamp `since` cursor AgentMain otherwise uses - the API-based adapters
 * (Docker/Kubernetes) can ask their daemon for "everything since time X", but a raw file has
 * no equivalent query, and re-scanning from the start every cycle to find where a timestamp
 * cursor left off doesn't scale once these logs get large. On first sight, tailing starts at
 * the current end of file rather than looking back (unlike Docker/Kubernetes' "last few
 * minutes on first sight") - nginx logs can already be huge, reading the existing backlog
 * isn't practical.
 */
public class NginxAdapter implements ServiceAdapter, LogSource {

	private static final String MAIN_SERVICE_ID = "nginx:main";

	private static final Path DEFAULT_ACCESS_LOG = Path.of("/var/log/nginx/access.log");
	private static final Path DEFAULT_ERROR_LOG = Path.of("/var/log/nginx/error.log");

	private static final Path NGINX_MAIN_CONFIG = Path.of("/etc/nginx/nginx.conf");
	private static final Path[] NGINX_CONFIG_DIRS = {
			Path.of("/etc/nginx/sites-enabled"), Path.of("/etc/nginx/conf.d"),
	};
	// [^;\s]+ rather than \S+: nginx directives end with ";" directly abutting the last
	// token (no space before it), which \S+ would otherwise happily swallow into the path.
	private static final Pattern ACCESS_LOG_DIRECTIVE = Pattern.compile("access_log\\s+([^;\\s]+)");
	private static final Pattern LOG_FILE_EXTENSION = Pattern.compile("(?i)\\.log$");
	private static final Pattern LOG_FILENAME_ACCESS_SUFFIX = Pattern.compile("(?i)[._-]?access$");

	// Default "combined" log format: $remote_addr - $remote_user [$time_local] "$request" $status ...
	private static final Pattern ACCESS_LINE =
			Pattern.compile("^\\S+ \\S+ \\S+ \\[([^]]+)] \"(\\S+) (\\S+)[^\"]*\" (\\d{3})");
	private static final DateTimeFormatter ACCESS_TIME_FORMAT =
			DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

	private static final Pattern ERROR_LINE_TIMESTAMP = Pattern.compile("^(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})");
	private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-z0-9_-]");

	private final Path accessLogPath;
	private final Path errorLogPath;
	private final Path nginxMainConfig;
	private final List<Path> nginxConfigDirs;

	private final Map<Path, Long> fileOffsets = new HashMap<>();
	// Every vhost log path ever found in nginx's config (service id -> display name) - kept
	// across cycles so a vhost with a quiet cycle (no new requests, or a transient config
	// read failure) doesn't flicker out of the services list; only cleared by an agent
	// restart.
	private final Map<String, String> knownVhosts = new LinkedHashMap<>();
	// This cycle's tailed-and-parsed lines, bucketed by destination service id - populated by
	// discover(), drained by the fetchLogs() calls AgentMain makes right after.
	private final Map<String, List<LogLine>> pendingByService = new LinkedHashMap<>();

	public NginxAdapter() {
		this(DEFAULT_ACCESS_LOG, DEFAULT_ERROR_LOG);
	}

	NginxAdapter(Path accessLogPath, Path errorLogPath) {
		this(accessLogPath, errorLogPath, NGINX_MAIN_CONFIG, List.of(NGINX_CONFIG_DIRS));
	}

	/** Package-private for direct unit testing - lets tests point the vhost-config scan at a
	 * temp directory instead of the real /etc/nginx. */
	NginxAdapter(Path accessLogPath, Path errorLogPath, Path nginxMainConfig, List<Path> nginxConfigDirs) {
		this.accessLogPath = accessLogPath;
		this.errorLogPath = errorLogPath;
		this.nginxMainConfig = nginxMainConfig;
		this.nginxConfigDirs = nginxConfigDirs;
	}

	public static NginxAdapter fromConfig(String accessLogPath, String errorLogPath) {
		return new NginxAdapter(toPathOrNull(accessLogPath), toPathOrNull(errorLogPath));
	}

	private static Path toPathOrNull(String raw) {
		return raw == null || raw.isBlank() ? null : Path.of(raw);
	}

	@Override
	public boolean isAvailable() {
		return accessLogPath != null && Files.exists(accessLogPath);
	}

	@Override
	public List<DiscoveredService> discover() {
		pendingByService.clear();
		pendingByService.computeIfAbsent(MAIN_SERVICE_ID, id -> new ArrayList<>());

		if (accessLogPath != null) {
			tailInto(accessLogPath, MAIN_SERVICE_ID, this::parseAccessLine);
		}
		if (errorLogPath != null) {
			tailInto(errorLogPath, MAIN_SERVICE_ID, this::parseErrorLine);
		}

		for (Path vhostLogPath : discoverVhostLogPaths()) {
			String name = vhostServiceName(vhostLogPath);
			String serviceId = "nginx:" + sanitizeName(name);
			knownVhosts.putIfAbsent(serviceId, name);
			tailInto(vhostLogPath, serviceId, this::parseAccessLine);
		}

		List<DiscoveredService> services = new ArrayList<>();
		services.add(toDiscoveredService(MAIN_SERVICE_ID, "nginx"));
		for (Map.Entry<String, String> vhost : knownVhosts.entrySet()) {
			services.add(toDiscoveredService(vhost.getKey(), vhost.getValue()));
		}
		return services;
	}

	/** Every distinct file an `access_log` directive points at, across nginx's main config
	 * and every file under sites-enabled/conf.d, excluding the already-configured main path
	 * (that one's tailed separately, into "nginx:main") and non-file targets (`off`,
	 * `syslog:...`). Best effort: an unreadable config file is skipped, not fatal to
	 * discovering the rest. Package-private for direct unit testing. */
	Set<Path> discoverVhostLogPaths() {
		Set<Path> paths = new LinkedHashSet<>();
		List<Path> configFiles = new ArrayList<>();
		if (Files.isRegularFile(nginxMainConfig)) {
			configFiles.add(nginxMainConfig);
		}
		for (Path dir : nginxConfigDirs) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (var entries = Files.list(dir)) {
				entries.filter(Files::isRegularFile).forEach(configFiles::add);
			} catch (IOException e) {
				// Best effort - whatever config files were already found are still scanned.
			}
		}

		for (Path configFile : configFiles) {
			try {
				String content = Files.readString(configFile, StandardCharsets.UTF_8);
				Matcher matcher = ACCESS_LOG_DIRECTIVE.matcher(content);
				while (matcher.find()) {
					String token = matcher.group(1);
					if (token.equals("off") || token.startsWith("syslog:")) {
						continue;
					}
					Path path = Path.of(token);
					if (!path.equals(accessLogPath)) {
						paths.add(path);
					}
				}
			} catch (IOException e) {
				System.err.println("NginxAdapter: reading " + configFile + " failed: " + e.getMessage());
			}
		}
		return paths;
	}

	/** "onda-backend.access.log" -> "onda-backend", "waretrack.log" -> "waretrack" - falls
	 * back to the un-stripped filename if stripping would leave nothing. Package-private for
	 * direct unit testing. */
	String vhostServiceName(Path path) {
		String stem = LOG_FILE_EXTENSION.matcher(path.getFileName().toString()).replaceFirst("");
		String stripped = LOG_FILENAME_ACCESS_SUFFIX.matcher(stem).replaceFirst("");
		return stripped.isBlank() ? stem : stripped;
	}

	private void tailInto(Path path, String serviceId, Function<String, LogLine> parser) {
		long previousOffset = fileOffsets.getOrDefault(path, -1L);
		TailResult result = tail(path, previousOffset);
		fileOffsets.put(path, result.newOffset());
		List<LogLine> bucket = pendingByService.computeIfAbsent(serviceId, id -> new ArrayList<>());
		for (String raw : result.lines()) {
			bucket.add(parser.apply(raw));
		}
	}

	private DiscoveredService toDiscoveredService(String id, String name) {
		Map<String, String> metadata = new LinkedHashMap<>();
		if (id.equals(MAIN_SERVICE_ID)) {
			metadata.put("access_log", String.valueOf(accessLogPath));
			metadata.put("error_log", String.valueOf(errorLogPath));
		}
		// fetchLogs() ignores this value (see class doc - service id alone is enough to look
		// up this cycle's bucket) but AgentMain won't call fetchLogs() at all without some
		// non-null value here, matching every other LogSource's metadata contract.
		metadata.put("log_native_id", id);
		// No real per-vhost liveness signal available (nginx doesn't expose one) - "running"
		// is a best-effort default for as long as a vhost keeps appearing in discover().
		return new DiscoveredService(id, name, "nginx", "running", 0, 0, 0, metadata);
	}

	private String sanitizeName(String rawName) {
		return INVALID_NAME_CHARS.matcher(rawName.toLowerCase(Locale.ROOT)).replaceAll("-");
	}

	@Override
	public List<LogLine> fetchLogs(String nativeId, Instant since) {
		List<LogLine> lines = pendingByService.get(nativeId);
		return lines != null ? lines : List.of();
	}

	/** Package-private (not private) so NginxAdapterTest can reference tail()'s return type. */
	record TailResult(List<String> lines, long newOffset) {
	}

	/** Package-private for direct unit testing against temp files. */
	TailResult tail(Path path, long previousOffset) {
		try {
			long fileSize = Files.size(path);
			if (previousOffset < 0) {
				return new TailResult(List.of(), fileSize);
			}
			// Shrunk since last time - rotated/truncated, start over rather than seek past EOF.
			long offset = fileSize < previousOffset ? 0 : previousOffset;
			if (offset == fileSize) {
				return new TailResult(List.of(), offset);
			}
			try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
				file.seek(offset);
				List<String> lines = new ArrayList<>();
				String line;
				while ((line = file.readLine()) != null) {
					// RandomAccessFile.readLine() decodes bytes as ISO-8859-1 (its own
					// documented behavior, not real charset detection) - re-decode as UTF-8,
					// which is what nginx actually writes.
					lines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
				}
				return new TailResult(lines, file.getFilePointer());
			}
		} catch (IOException e) {
			System.err.println("NginxAdapter: reading " + path + " failed: " + e.getMessage());
			return new TailResult(List.of(), previousOffset);
		}
	}

	/** Package-private for direct unit testing. */
	LogLine parseAccessLine(String raw) {
		Matcher matcher = ACCESS_LINE.matcher(raw);
		if (!matcher.find()) {
			return new LogLine(Instant.now(), "info", raw);
		}
		Instant timestamp = parseAccessTimestamp(matcher.group(1));
		int status = Integer.parseInt(matcher.group(4));
		String level = status >= 500 ? "error" : status >= 400 ? "warn" : "info";
		return new LogLine(timestamp, level, raw);
	}

	private Instant parseAccessTimestamp(String rawTimestamp) {
		try {
			return ZonedDateTime.parse(rawTimestamp, ACCESS_TIME_FORMAT).toInstant();
		} catch (DateTimeParseException e) {
			return Instant.now();
		}
	}

	/** Package-private for direct unit testing. nginx's own error log already tags each line
	 * with a bracketed level ([error], [warn], [notice]...), no keyword guessing needed. */
	LogLine parseErrorLine(String raw) {
		String lower = raw.toLowerCase(Locale.ROOT);
		String level;
		if (lower.contains("[error]") || lower.contains("[crit]") || lower.contains("[alert]") || lower.contains("[emerg]")) {
			level = "error";
		} else if (lower.contains("[warn]")) {
			level = "warn";
		} else {
			level = "info";
		}
		return new LogLine(parseErrorTimestamp(raw), level, raw);
	}

	private Instant parseErrorTimestamp(String raw) {
		Matcher matcher = ERROR_LINE_TIMESTAMP.matcher(raw);
		if (!matcher.find()) {
			return Instant.now();
		}
		try {
			return LocalDateTime.parse(matcher.group(1), ERROR_TIME_FORMAT).atZone(ZoneId.systemDefault()).toInstant();
		} catch (DateTimeParseException e) {
			return Instant.now();
		}
	}
}
