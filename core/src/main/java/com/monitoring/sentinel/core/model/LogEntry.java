package com.monitoring.sentinel.core.model;

import com.monitoring.sentinel.core.enums.LogLevel;

import java.time.Instant;

/**
 * A raw log line (logs_raw), short retention, for direct browsing (architecture doc, section 7.1).
 */
public class LogEntry {

	private String serviceId;
	private Instant timestamp;
	private LogLevel level;
	private String message;

	public LogEntry() {
	}

	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public LogLevel getLevel() {
		return level;
	}

	public void setLevel(LogLevel level) {
		this.level = level;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
