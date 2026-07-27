package com.monitoring.sentinel.central.ingestion.dto;

public record HeaviestFilePayload(String path, long sizeMb) {
}
