package com.monitoring.sentinel.agent.discovery;

import java.util.List;

/**
 * Generic fallback via /proc/{pid}/status and /proc/{pid}/stat, read by the C module
 * (architecture doc, section 3.2). The most limited: no notion of a grouped "service",
 * just raw PIDs - the weakest link of zero-config. Always available (no specific
 * detection). Roadmap #8 - discover() still needs a real implementation, the fuzziest
 * one, to iterate on.
 */
public class ProcessAdapter implements ServiceAdapter {

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public List<DiscoveredService> discover() {
		// TODO(roadmap #8): read the raw process stats exposed by the C module (via
		// NativeStatsClient) for processes not covered by the other adapters, filtering
		// out known system processes (level 1 filtering, architecture doc section 3.3).
		return List.of();
	}
}
