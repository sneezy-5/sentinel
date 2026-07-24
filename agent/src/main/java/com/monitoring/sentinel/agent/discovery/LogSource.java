package com.monitoring.sentinel.agent.discovery;

import java.time.Instant;
import java.util.List;

/**
 * Optional capability for adapters that can also fetch raw log lines for a service they
 * discovered (architecture doc, section 6.2/7 - parsing happens on the agent side). Not
 * every ServiceAdapter implements this yet (PM2/Kubernetes/process are still discovery-only
 * stubs) - AgentMain checks `instanceof LogSource` before calling it.
 */
public interface LogSource {

	List<LogLine> fetchLogs(String nativeId, Instant since);

	record LogLine(Instant timestamp, String level, String message) {
	}
}
