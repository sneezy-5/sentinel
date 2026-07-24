package com.monitoring.sentinel.central.ingestion.dto;

public record NetworkUsagePayload(long rxBytes, long txBytes) {
}
