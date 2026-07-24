package com.monitoring.sentinel.core.model;

import com.monitoring.sentinel.core.enums.AlertLevel;

/**
 * A threshold rule evaluated on the central side (see alerting/AlertEvaluationService).
 * targetMetric refers to a flat field of SystemMetric or ServiceMetric, e.g. "cpuPercent", "ramUsedMb".
 */
public class AlertRule {

	private String id;
	private String serverId;
	private String serviceId;
	private String targetMetric;
	private double threshold;
	private AlertLevel level;
	private boolean enabled;

	public AlertRule() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public String getTargetMetric() {
		return targetMetric;
	}

	public void setTargetMetric(String targetMetric) {
		this.targetMetric = targetMetric;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public AlertLevel getLevel() {
		return level;
	}

	public void setLevel(AlertLevel level) {
		this.level = level;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
