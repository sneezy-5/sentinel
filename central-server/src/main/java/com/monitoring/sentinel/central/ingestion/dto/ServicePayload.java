package com.monitoring.sentinel.central.ingestion.dto;

import java.util.Map;

public record ServicePayload(
		String id,
		String name,
		String type,
		String status,
		double cpuPercent,
		long memMb,
		Map<String, String> metadata) {
}
