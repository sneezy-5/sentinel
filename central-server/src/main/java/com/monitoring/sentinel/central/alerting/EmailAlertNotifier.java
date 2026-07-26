package com.monitoring.sentinel.central.alerting;

import com.monitoring.sentinel.core.model.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

/**
 * Sends an email per exceeded threshold, using SMTP settings from the Settings page
 * (AlertSettingsEntity) - read fresh on every call rather than cached, since alerts are
 * infrequent and settings can change at any time without a restart.
 *
 * Builds its own JavaMailSenderImpl per send instead of a single shared Spring-managed
 * bean, because the settings (and therefore the sender config) can change at runtime -
 * a bean wired once at startup wouldn't pick up changes made from the Settings page.
 */
@Component
public class EmailAlertNotifier implements AlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(EmailAlertNotifier.class);

	private final AlertSettingsRepository alertSettingsRepository;
	private final AlertMessageFormatter formatter;

	public EmailAlertNotifier(AlertSettingsRepository alertSettingsRepository, AlertMessageFormatter formatter) {
		this.alertSettingsRepository = alertSettingsRepository;
		this.formatter = formatter;
	}

	@Override
	public void notify(AlertRule rule, double actualValue) {
		Optional<AlertSettingsEntity> settings = alertSettingsRepository.findById(AlertSettingsEntity.SINGLETON_ID);
		if (settings.isEmpty() || !settings.get().isEmailEnabled()) {
			return;
		}

		AlertSettingsEntity s = settings.get();
		if (isBlank(s.getSmtpHost()) || isBlank(s.getToAddress()) || isBlank(s.getFromAddress())) {
			log.warn("Email alerting is enabled but not fully configured (host/from/to) - skipping send.");
			return;
		}

		try {
			JavaMailSenderImpl sender = buildSender(s);
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(s.getFromAddress());
			message.setTo(s.getToAddress());
			message.setSubject(formatter.subject(rule));
			message.setText(formatter.body(rule, actualValue));
			sender.send(message);
		} catch (RuntimeException e) {
			// A failed email must never break threshold evaluation for the next rule/cycle.
			log.error("Failed to send alert email: {}", e.getMessage());
		}
	}

	private JavaMailSenderImpl buildSender(AlertSettingsEntity s) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(s.getSmtpHost());
		sender.setPort(s.getSmtpPort() != null ? s.getSmtpPort() : 587);
		if (!isBlank(s.getSmtpUsername())) {
			sender.setUsername(s.getSmtpUsername());
			sender.setPassword(s.getSmtpPassword());
		}
		Properties props = sender.getJavaMailProperties();
		props.put("mail.smtp.auth", !isBlank(s.getSmtpUsername()));
		props.put("mail.smtp.starttls.enable", "true");
		return sender;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
