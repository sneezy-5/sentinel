package com.monitoring.sentinel.central.ingestion.dto;

import java.util.List;

public record LogBatchPayload(String serviceId, List<LogEntryPayload> entries) {
}
