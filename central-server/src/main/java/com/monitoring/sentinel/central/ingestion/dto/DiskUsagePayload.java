package com.monitoring.sentinel.central.ingestion.dto;

public record DiskUsagePayload(String mount, double usedGb, double totalGb) {
}
