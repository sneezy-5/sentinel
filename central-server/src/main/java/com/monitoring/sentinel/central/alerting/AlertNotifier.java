package com.monitoring.sentinel.central.alerting;

import com.monitoring.sentinel.core.model.AlertRule;

/**
 * A notification channel (email/Telegram/webhook - architecture doc, section 4.1). Only
 * one implementation (log) for now; wiring real channels is a roadmap item (section 8, #9).
 */
public interface AlertNotifier {

	void notify(AlertRule rule, double actualValue);
}
