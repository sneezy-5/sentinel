package com.monitoring.sentinel.central.ingestion;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes the standard nginx "combined" access log format in an already-ingested raw log
 * line, to derive per-endpoint API_CALL counters (architecture doc, section 7.2: "standard
 * access-log formats like nginx" is one of the generic default rules). Runs centrally rather
 * than on the agent (see IngestionService) - consistent with how ERROR event derivation
 * already works here, off the agent-assigned level, rather than the agent itself deriving
 * events (which the architecture doc's own text would suggest but the existing code
 * doesn't do).
 *
 * Not nginx-specific in practice: any log line matching this shape (any service's raw logs,
 * not just NginxAdapter's) is counted, the same "standard access-log formats" generic rule
 * the doc describes.
 */
public final class NginxAccessLogParser {

	// $remote_addr - $remote_user [$time_local] "$request" $status ... - the default
	// "combined" log_format. Group 2 (path) stops at the first space, '?', or closing quote,
	// which is what strips the query string and any HTTP version suffix in one pass.
	private static final Pattern ACCESS_LINE =
			Pattern.compile("^\\S+ \\S+ \\S+ \\[[^]]+] \"(\\S+) ([^ ?\"]+)[^\"]*\" (\\d{3})");

	private NginxAccessLogParser() {
	}

	public static Optional<Endpoint> parse(String line) {
		Matcher matcher = ACCESS_LINE.matcher(line);
		if (!matcher.find()) {
			return Optional.empty();
		}
		return Optional.of(new Endpoint(matcher.group(1), matcher.group(2)));
	}

	public record Endpoint(String method, String path) {

		/** What gets stored as LogEvent.detail - e.g. "GET /api/stock". */
		public String label() {
			return method + " " + path;
		}
	}
}
