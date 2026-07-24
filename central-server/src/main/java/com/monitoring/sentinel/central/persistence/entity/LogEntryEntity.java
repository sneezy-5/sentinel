package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.enums.LogLevel;
import com.monitoring.sentinel.core.model.LogEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** logs_raw: short retention, direct browsing (architecture doc, section 7.1). */
@Entity
@Table(name = "logs_raw")
public class LogEntryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String serviceId;
	private Instant timestamp;

	@Enumerated(EnumType.STRING)
	private LogLevel level;

	// Docker log lines (stack traces especially) routinely exceed the default varchar(255) -
	// a real container send this hit "value too long for type character varying(255)" on
	// its first day in prod.
	@Column(columnDefinition = "text")
	private String message;

	protected LogEntryEntity() {
	}

	public static LogEntryEntity fromModel(LogEntry entry) {
		LogEntryEntity entity = new LogEntryEntity();
		entity.serviceId = entry.getServiceId();
		entity.timestamp = entry.getTimestamp();
		entity.level = entry.getLevel();
		entity.message = entry.getMessage();
		return entity;
	}

	public LogEntry toModel() {
		LogEntry entry = new LogEntry();
		entry.setServiceId(serviceId);
		entry.setTimestamp(timestamp);
		entry.setLevel(level);
		entry.setMessage(message);
		return entry;
	}

	public String getServiceId() {
		return serviceId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public LogLevel getLevel() {
		return level;
	}

	public String getMessage() {
		return message;
	}
}
