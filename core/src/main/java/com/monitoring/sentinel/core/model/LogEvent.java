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
	// Null for ERROR/WARNING/INFO (a plain per-batch counter, same as before) - populated for
	// API_CALL, where it's the "method path" the count is for (e.g. "GET /api/stock").
	private String detail;
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

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}
}
