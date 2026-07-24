package com.monitoring.sentinel.agent.dto;

import java.time.Instant;

public record LogEntryPayload(Instant timestamp, String level, String message) {
}
