package com.monitoring.sentinel.core.enums;

/**
 * Deployment mode of a service discovered by the agent.
 * The {@link #prefix()} is used in the stable "type:name" id (see ServiceIdValidator).
 */
public enum ServiceType {
	DOCKER("docker"),
	PM2("pm2"),
	K8S("k8s"),
	PROCESS("process");

	private final String prefix;

	ServiceType(String prefix) {
		this.prefix = prefix;
	}

	public String prefix() {
		return prefix;
	}

	public static ServiceType fromPrefix(String prefix) {
		for (ServiceType type : values()) {
			if (type.prefix.equals(prefix)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown service prefix: " + prefix);
	}
}
