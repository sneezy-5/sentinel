package com.monitoring.sentinel.central.alerting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SMTP config for email alerts (architecture doc, section 4.1: "notifications
 * email/Telegram/webhook"). Single row (id is always "default") - DB-backed rather than
 * static config so it can be changed from the Settings page without a restart.
 */
@Entity
@Table(name = "alert_settings")
public class AlertSettingsEntity {

	public static final String SINGLETON_ID = "default";

	@Id
	private String id = SINGLETON_ID;

	private boolean emailEnabled;
	private String smtpHost;
	private Integer smtpPort;
	private String smtpUsername;

	// Stored in plain text, unlike agent tokens (hashed) - unavoidable here since the SMTP
	// server needs the actual credential, not a hash. Restrict DB access accordingly; this
	// is a real trade-off, not an oversight.
	private String smtpPassword;
	private String fromAddress;
	private String toAddress;

	public String getId() {
		return id;
	}

	public boolean isEmailEnabled() {
		return emailEnabled;
	}

	public void setEmailEnabled(boolean emailEnabled) {
		this.emailEnabled = emailEnabled;
	}

	public String getSmtpHost() {
		return smtpHost;
	}

	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}

	public Integer getSmtpPort() {
		return smtpPort;
	}

	public void setSmtpPort(Integer smtpPort) {
		this.smtpPort = smtpPort;
	}

	public String getSmtpUsername() {
		return smtpUsername;
	}

	public void setSmtpUsername(String smtpUsername) {
		this.smtpUsername = smtpUsername;
	}

	public String getSmtpPassword() {
		return smtpPassword;
	}

	public void setSmtpPassword(String smtpPassword) {
		this.smtpPassword = smtpPassword;
	}

	public String getFromAddress() {
		return fromAddress;
	}

	public void setFromAddress(String fromAddress) {
		this.fromAddress = fromAddress;
	}

	public String getToAddress() {
		return toAddress;
	}

	public void setToAddress(String toAddress) {
		this.toAddress = toAddress;
	}
}
