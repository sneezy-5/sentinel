package com.monitoring.sentinel.agent.dto;

/**
 * Intentionally duplicates the equivalent DTO in central-server/ingestion/dto (architecture
 * doc, section 3.1): the agent doesn't depend on `core`, so there's no shared type. The
 * contract is the JSON described in section 6 of the architecture doc - if you change a
 * field here, check the central-side ingestion too.
 */
public record HeaviestFilePayload(String path, long sizeMb) {
}
