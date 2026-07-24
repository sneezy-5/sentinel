package com.monitoring.sentinel.agent.dto;

import java.time.Instant;
import java.util.List;

public record MetricsPayload(Instant timestamp, SystemPayload system, List<ServicePayload> services) {
}
