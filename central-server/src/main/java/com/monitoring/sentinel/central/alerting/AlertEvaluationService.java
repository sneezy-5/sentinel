package com.monitoring.sentinel.central.alerting;

import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import com.monitoring.sentinel.central.persistence.entity.SystemMetricEntity;
import com.monitoring.sentinel.central.persistence.repository.AlertRuleRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceMetricRepository;
import com.monitoring.sentinel.central.persistence.repository.SystemMetricRepository;
import com.monitoring.sentinel.core.model.AlertRule;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Threshold evaluation (architecture doc, section 4.1), read from the latest ingested metrics. */
@Component
public class AlertEvaluationService {

	private final AlertRuleRepository alertRuleRepository;
	private final SystemMetricRepository systemMetricRepository;
	private final ServiceMetricRepository serviceMetricRepository;
	private final List<AlertNotifier> alertNotifiers;

	public AlertEvaluationService(
			AlertRuleRepository alertRuleRepository,
			SystemMetricRepository systemMetricRepository,
			ServiceMetricRepository serviceMetricRepository,
			List<AlertNotifier> alertNotifiers) {
		this.alertRuleRepository = alertRuleRepository;
		this.systemMetricRepository = systemMetricRepository;
		this.serviceMetricRepository = serviceMetricRepository;
		this.alertNotifiers = alertNotifiers;
	}

	@Scheduled(fixedDelayString = "PT30S")
	public void evaluateRules() {
		for (var ruleEntity : alertRuleRepository.findByEnabledTrue()) {
			AlertRule rule = ruleEntity.toModel();
			OptionalDouble actualValue = readMetric(rule);
			if (actualValue.isPresent() && actualValue.getAsDouble() > rule.getThreshold()) {
				// Every configured channel gets a shot - a broken one (e.g. email
				// misconfigured) must not stop the others (e.g. the log line) from firing.
				for (AlertNotifier notifier : alertNotifiers) {
					notifier.notify(rule, actualValue.getAsDouble());
				}
			}
		}
	}

	private OptionalDouble readMetric(AlertRule rule) {
		if (rule.getServiceId() != null) {
			Optional<ServiceMetricEntity> metric =
					serviceMetricRepository.findFirstByServiceIdOrderByTimestampDesc(rule.getServiceId());
			return metric.map(m -> extractServiceMetric(m, rule.getTargetMetric()))
					.map(OptionalDouble::of)
					.orElse(OptionalDouble.empty());
		}
		Optional<SystemMetricEntity> metric =
				systemMetricRepository.findFirstByServerIdOrderByTimestampDesc(rule.getServerId());
		return metric.map(m -> extractSystemMetric(m, rule.getTargetMetric()))
				.map(OptionalDouble::of)
				.orElse(OptionalDouble.empty());
	}

	private double extractSystemMetric(SystemMetricEntity metric, String targetMetric) {
		return switch (targetMetric) {
			case "cpuPercent" -> metric.getCpuPercent();
			case "ramUsedMb" -> metric.getRamUsedMb();
			case "topProcessRssMb" -> metric.getTopProcessRssMb();
			case "heaviestFileSizeMb" -> metric.getHeaviestFileSizeMb();
			default -> throw new IllegalArgumentException("Unknown system metric: " + targetMetric);
		};
	}

	private double extractServiceMetric(ServiceMetricEntity metric, String targetMetric) {
		return switch (targetMetric) {
			case "cpuPercent" -> metric.getCpuPercent();
			case "memMb" -> metric.getMemMb();
			default -> throw new IllegalArgumentException("Unknown service metric: " + targetMetric);
		};
	}
}
