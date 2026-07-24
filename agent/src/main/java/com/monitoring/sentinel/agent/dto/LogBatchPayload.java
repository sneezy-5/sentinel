package com.monitoring.sentinel.agent.dto;

import java.util.List;

public record LogBatchPayload(String serviceId, List<LogEntryPayload> entries) {
}
