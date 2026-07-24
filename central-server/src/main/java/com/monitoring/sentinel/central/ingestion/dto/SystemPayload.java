package com.monitoring.sentinel.central.ingestion.dto;

import java.util.List;

public record SystemPayload(
		double cpuPercent,
		int cpuCores,
		long ramUsedMb,
		long ramTotalMb,
		List<DiskUsagePayload> disk,
		NetworkUsagePayload network) {
}
