package com.monitoring.sentinel.central.ingestion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Envelope received every 15-30s (architecture doc, section 6.1). Always a full snapshot
 * of active services, never a diff.
 */
public record MetricsPayload(Instant timestamp, SystemPayload system, List<ServicePayload> services) {
}
