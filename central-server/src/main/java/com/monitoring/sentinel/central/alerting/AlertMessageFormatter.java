package com.monitoring.sentinel.central.alerting;

import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import com.monitoring.sentinel.central.persistence.entity.ServiceEntity;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import com.monitoring.sentinel.core.model.AlertRule;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Builds a human-readable identification of "what fired" for an AlertRule - shared by every
 * AlertNotifier so email/log/future channels agree on the same wording. Originally each
 * notifier formatted its own message from just the rule id and raw metric value, which meant
 * an operator getting an alert had no server name to go on without looking the rule up.
 */
@Component
public class AlertMessageFormatter {

	private final ServerRepository serverRepository;
	private final ServiceRepository serviceRepository;

	public AlertMessageFormatter(ServerRepository serverRepository, ServiceRepository serviceRepository) {
		this.serverRepository = serverRepository;
		this.serviceRepository = serviceRepository;
	}

	public String subject(AlertRule rule) {
		return "[Sentinel] " + rule.getLevel() + " - " + target(rule) + " - " + rule.getTargetMetric() + " threshold exceeded";
	}

	public String body(AlertRule rule, double actualValue) {
		return target(rule) + ": " + rule.getTargetMetric() + " = " + formatValue(rule.getTargetMetric(), actualValue)
				+ " (threshold " + formatValue(rule.getTargetMetric(), rule.getThreshold()) + ")"
				+ "\nRule: " + rule.getId();
	}

	/** Single-line form for the log notifier - same information as {@link #body}, no newline. */
	public String logLine(AlertRule rule, double actualValue) {
		return target(rule) + ": " + rule.getTargetMetric() + " = " + formatValue(rule.getTargetMetric(), actualValue)
				+ " (threshold " + formatValue(rule.getTargetMetric(), rule.getThreshold()) + ", rule " + rule.getId() + ")";
	}

	/** "server-name (hostname)" for a server-level rule, or "server-name (hostname) / service-name"
	 * once a serviceId narrows it further - this is what actually lets an operator identify
	 * which machine fired without going back to the rule list. */
	private String target(AlertRule rule) {
		String server = serverLabel(rule.getServerId());
		return rule.getServiceId() != null ? server + " / " + serviceName(rule.getServiceId()) : server;
	}

	private String serverLabel(String serverId) {
		if (serverId == null) {
			return "unknown server";
		}
		return serverRepository.findById(serverId)
				.map(this::formatServer)
				// The server may have been deleted since the rule was created - fall back to
				// the raw id rather than hiding that this rule no longer resolves to anything.
				.orElse(serverId);
	}

	private String formatServer(ServerEntity server) {
		boolean hasHostname = server.getHostname() != null && !server.getHostname().isBlank();
		return hasHostname ? server.getName() + " (" + server.getHostname() + ")" : server.getName();
	}

	private String serviceName(String serviceId) {
		return serviceRepository.findById(serviceId).map(ServiceEntity::getName).orElse(serviceId);
	}

	/** Package-private for direct unit testing. cpuPercent as a percentage, the mem/disk
	 * metrics as whole megabytes - the raw double alone doesn't say which. */
	static String formatValue(String targetMetric, double value) {
		return switch (targetMetric) {
			case "cpuPercent" -> String.format(Locale.ROOT, "%.1f%%", value);
			case "ramUsedMb", "memMb", "diskMb" -> String.format(Locale.ROOT, "%.0f MB", value);
			default -> String.valueOf(value);
		};
	}
}
