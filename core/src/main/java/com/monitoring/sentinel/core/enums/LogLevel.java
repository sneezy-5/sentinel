package com.monitoring.sentinel.core.enums;

/**
 * Level of a raw log line as emitted by the source (logs_raw).
 * Not to be confused with LogEventType, which categorizes derived events.
 */
public enum LogLevel {
	DEBUG,
	INFO,
	WARN,
	ERROR
}
