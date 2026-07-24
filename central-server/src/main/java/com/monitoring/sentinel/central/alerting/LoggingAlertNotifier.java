package com.monitoring.sentinel.central.alerting;

import com.monitoring.sentinel.core.model.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAlertNotifier implements AlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

	@Override
	public void notify(AlertRule rule, double actualValue) {
		log.warn("[{}] rule {} exceeded: {} = {} (threshold {})",
				rule.getLevel(), rule.getId(), rule.getTargetMetric(), actualValue, rule.getThreshold());
	}
}
