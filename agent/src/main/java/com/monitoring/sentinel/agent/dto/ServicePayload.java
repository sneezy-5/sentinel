package com.monitoring.sentinel.agent.dto;

import java.util.Map;

public record ServicePayload(
		String id,
		String name,
		String type,
		String status,
		double cpuPercent,
		long memMb,
		long diskMb,
		Map<String, String> metadata) {
}
