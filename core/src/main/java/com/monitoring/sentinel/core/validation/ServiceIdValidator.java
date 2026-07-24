package com.monitoring.sentinel.core.validation;

import com.monitoring.sentinel.core.enums.ServiceType;

import java.util.regex.Pattern;

/**
 * Validates and builds a service's stable id: "type:name" (e.g. "docker:djeli-api").
 * Must stay stable over time (never a PID or an ephemeral Docker id) to avoid fragmenting
 * the stored history every time a service restarts (see architecture doc, section 6.1).
 */
public final class ServiceIdValidator {

	private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,127}$");

	private ServiceIdValidator() {
	}

	public static boolean isValid(String serviceId) {
		if (serviceId == null) {
			return false;
		}
		int separatorIndex = serviceId.indexOf(':');
		if (separatorIndex <= 0 || separatorIndex == serviceId.length() - 1) {
			return false;
		}
		String prefix = serviceId.substring(0, separatorIndex);
		String name = serviceId.substring(separatorIndex + 1);
		return isKnownPrefix(prefix) && NAME_PATTERN.matcher(name).matches();
	}

	public static String build(ServiceType type, String name) {
		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("Invalid service name: " + name);
		}
		return type.prefix() + ":" + name;
	}

	private static boolean isKnownPrefix(String prefix) {
		for (ServiceType type : ServiceType.values()) {
			if (type.prefix().equals(prefix)) {
				return true;
			}
		}
		return false;
	}
}
