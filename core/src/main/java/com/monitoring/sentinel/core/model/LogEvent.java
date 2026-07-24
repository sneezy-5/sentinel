package com.monitoring.sentinel.core.model;

import com.monitoring.sentinel.core.enums.LogEventType;

import java.time.Instant;

/**
 * A derived event counter (log_events), long retention, produced by pattern matching on
 * the agent side (architecture doc, section 7.2). Tiny volume compared to logs_raw.
 */
public class LogEvent {

	private String serviceId;
	private Instant timestamp;
	private LogEventType eventType;
	private long count;

	public LogEvent() {
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

	public LogEventType getEventType() {
		return eventType;
	}

	public void setEventType(LogEventType eventType) {
		this.eventType = eventType;
	}

	public long getCount() {
		return count;
	}

	public void setCount(long count) {
		this.count = count;
	}
}
