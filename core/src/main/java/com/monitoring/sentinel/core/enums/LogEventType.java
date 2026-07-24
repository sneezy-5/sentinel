package com.monitoring.sentinel.core.enums;

/**
 * Category of an event derived by pattern matching on the agent side (see architecture doc, section 7).
 */
public enum LogEventType {
	ERROR,
	API_CALL,
	WARNING,
	INFO
}
