package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.enums.AlertLevel;
import com.monitoring.sentinel.core.model.AlertRule;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rules")
public class AlertRuleEntity {

	@Id
	private String id;

	private String serverId;
	private String serviceId;
	private String targetMetric;
	private double threshold;

	@Enumerated(EnumType.STRING)
	private AlertLevel level;

	private boolean enabled;

	public AlertRule toModel() {
		AlertRule rule = new AlertRule();
		rule.setId(id);
		rule.setServerId(serverId);
		rule.setServiceId(serviceId);
		rule.setTargetMetric(targetMetric);
		rule.setThreshold(threshold);
		rule.setLevel(level);
		rule.setEnabled(enabled);
		return rule;
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
