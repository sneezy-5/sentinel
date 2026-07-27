package com.monitoring.sentinel.central.ingestion.dto;

public record TopProcessPayload(int pid, String name, long rssMb) {
}
