package com.monitoring.sentinel.central.ingestion.dto;

import java.time.Instant;

public record LogEntryPayload(Instant timestamp, String level, String message) {
}
