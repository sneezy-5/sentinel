package com.monitoring.sentinel.agent.dto;

public record NetworkUsagePayload(long rxBytes, long txBytes) {
}
