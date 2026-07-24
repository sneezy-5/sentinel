package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.enums.LogEventType;
import com.monitoring.sentinel.core.model.LogEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** log_events: derived counters, long retention, tiny volume (architecture doc, section 7.2). */
@Entity
@Table(name = "log_events")
public class LogEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String serviceId;
	private Instant timestamp;

	@Enumerated(EnumType.STRING)
	private LogEventType eventType;

	private long count;

	protected LogEventEntity() {
	}

	public static LogEventEntity fromModel(LogEvent event) {
		LogEventEntity entity = new LogEventEntity();
		entity.serviceId = event.getServiceId();
		entity.timestamp = event.getTimestamp();
		entity.eventType = event.getEventType();
		entity.count = event.getCount();
		return entity;
	}

	public LogEvent toModel() {
		LogEvent event = new LogEvent();
		event.setServiceId(serviceId);
		event.setTimestamp(timestamp);
		event.setEventType(eventType);
		event.setCount(count);
		return event;
	}

	public String getServiceId() {
		return serviceId;
	}

	public LogEventType getEventType() {
		return eventType;
	}

	public long getCount() {
		return count;
	}
}
