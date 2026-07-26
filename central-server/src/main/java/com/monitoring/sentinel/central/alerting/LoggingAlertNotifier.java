package com.monitoring.sentinel.central.alerting;

import com.monitoring.sentinel.core.model.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAlertNotifier implements AlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

	private final AlertMessageFormatter formatter;

	public LoggingAlertNotifier(AlertMessageFormatter formatter) {
		this.formatter = formatter;
	}

	@Override
	public void notify(AlertRule rule, double actualValue) {
		log.warn("[{}] {}", rule.getLevel(), formatter.logLine(rule, actualValue));
	}
}
