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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Reported as a single service ("nginx:main") rather than one per virtual host: nginx's own
 * logs don't identify which vhost/upstream served a request without custom log_format
 * config this adapter can't assume (the architecture doc's "custom rule... no longer 100%
 * zero-config" caveat, section 7.2).
 *
 * Log tailing is byte-offset based (own instance state), not the timestamp `since` cursor
 * AgentMain otherwise uses - the API-based adapters (Docker/Kubernetes) can ask their daemon
 * for "everything since time X", but a raw file has no equivalent query, and re-scanning
 * from the start every cycle to find where a timestamp cursor left off doesn't scale once
 * these logs get large. On first sight, tailing starts at the current end of file rather
 * than looking back (unlike Docker/Kubernetes' "last few minutes on first sight") - nginx
 * logs can already be huge, reading the existing backlog isn't practical.
 */
public class NginxAdapter implements ServiceAdapter, LogSource {

	private static final Path DEFAULT_ACCESS_LOG = Path.of("/var/log/nginx/access.log");
	private static final Path DEFAULT_ERROR_LOG = Path.of("/var/log/nginx/error.log");

	// Default "combined" log format: $remote_addr - $remote_user [$time_local] "$request" $status ...
	private static final Pattern ACCESS_LINE =
			Pattern.compile("^\\S+ \\S+ \\S+ \\[([^]]+)] \"(\\S+) (\\S+)[^\"]*\" (\\d{3})");
	private static final DateTimeFormatter ACCESS_TIME_FORMAT =
			DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

	private static final Pattern ERROR_LINE_TIMESTAMP = Pattern.compile("^(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})");
	private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private final Path accessLogPath;
	private final Path errorLogPath;

	private long accessLogOffset = -1;
	private long errorLogOffset = -1;

	public NginxAdapter() {
		this(DEFAULT_ACCESS_LOG, DEFAULT_ERROR_LOG);
	}

	/** Package-private for direct unit testing against temp files. */
	NginxAdapter(Path accessLogPath, Path errorLogPath) {
		this.accessLogPath = accessLogPath;
		this.errorLogPath = errorLogPath;
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
		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("access_log", String.valueOf(accessLogPath));
		metadata.put("error_log", String.valueOf(errorLogPath));
		// Unused by fetchLogs() (see class doc - this adapter tails by its own byte offset,
		// not a nativeId lookup) but every LogSource needs some value here for AgentMain to
		// even attempt calling fetchLogs() in the first place.
		metadata.put("log_native_id", "nginx");
		return List.of(new DiscoveredService("nginx:main", "nginx", "nginx", "running", 0, 0, 0, metadata));
	}

	@Override
	public List<LogLine> fetchLogs(String nativeId, Instant since) {
		List<LogLine> lines = new ArrayList<>();
		if (accessLogPath != null) {
			TailResult result = tail(accessLogPath, accessLogOffset);
			accessLogOffset = result.newOffset();
			for (String raw : result.lines()) {
				lines.add(parseAccessLine(raw));
			}
		}
		if (errorLogPath != null) {
			TailResult result = tail(errorLogPath, errorLogOffset);
			errorLogOffset = result.newOffset();
			for (String raw : result.lines()) {
				lines.add(parseErrorLine(raw));
			}
		}
		return lines;
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
