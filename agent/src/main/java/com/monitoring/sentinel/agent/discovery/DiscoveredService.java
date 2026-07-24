package com.monitoring.sentinel.agent.discovery;

import java.util.Map;

/**
 * A service as seen by an adapter, before being serialized into a ServicePayload. The id
 * must follow the stable "type:name" format (architecture doc, section 6.1) - each adapter
 * is responsible for building it correctly (e.g. DockerAdapter -> "docker:" + container name).
 */
public record DiscoveredService(
		String id,
		String name,
		String type,
		String status,
		double cpuPercent,
		long memMb,
		long diskMb,
		Map<String, String> metadata) {
}
