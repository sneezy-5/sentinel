package com.monitoring.sentinel.agent.config;

import java.util.List;

/**
 * Content of monitoring-agent.yml (architecture doc, section 3.3). Generated empty at
 * install time: all level 2/3 fields are optional, only centralUrl/token are written by
 * the install script (section 5.1).
 */
public class AgentConfig {

	private String centralUrl;
	private String token;
	private int pushIntervalSeconds = 20;
	private List<String> excludeServices = List.of();
	private List<String> includeServices = List.of();
	// /var/log by default: the single most common source of a runaway file (verbose/unrotated
	// logs) - keeps the "install and forget" zero-config promise instead of requiring the
	// operator to opt in just to get a sane default. A full-disk scan on every push cycle
	// would be far too expensive, hence a narrow default root + a long default interval.
	private String heaviestFileScanPath = "/var/log";
	private long heaviestFileScanIntervalSeconds = 3600;
	// Standard Debian/Ubuntu locations - only relevant for a host-installed nginx (see
	// NginxAdapter); harmless default for servers without nginx at all, isAvailable() just
	// won't find these paths and the adapter stays inactive.
	private String nginxAccessLogPath = "/var/log/nginx/access.log";
	private String nginxErrorLogPath = "/var/log/nginx/error.log";

	public String getCentralUrl() {
		return centralUrl;
	}

	public void setCentralUrl(String centralUrl) {
		this.centralUrl = centralUrl;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public int getPushIntervalSeconds() {
		return pushIntervalSeconds;
	}

	public void setPushIntervalSeconds(int pushIntervalSeconds) {
		this.pushIntervalSeconds = pushIntervalSeconds;
	}

	public List<String> getExcludeServices() {
		return excludeServices;
	}

	public void setExcludeServices(List<String> excludeServices) {
		this.excludeServices = excludeServices;
	}

	public List<String> getIncludeServices() {
		return includeServices;
	}

	public void setIncludeServices(List<String> includeServices) {
		this.includeServices = includeServices;
	}

	public String getHeaviestFileScanPath() {
		return heaviestFileScanPath;
	}

	public void setHeaviestFileScanPath(String heaviestFileScanPath) {
		this.heaviestFileScanPath = heaviestFileScanPath;
	}

	public long getHeaviestFileScanIntervalSeconds() {
		return heaviestFileScanIntervalSeconds;
	}

	public void setHeaviestFileScanIntervalSeconds(long heaviestFileScanIntervalSeconds) {
		this.heaviestFileScanIntervalSeconds = heaviestFileScanIntervalSeconds;
	}

	public String getNginxAccessLogPath() {
		return nginxAccessLogPath;
	}

	public void setNginxAccessLogPath(String nginxAccessLogPath) {
		this.nginxAccessLogPath = nginxAccessLogPath;
	}

	public String getNginxErrorLogPath() {
		return nginxErrorLogPath;
	}

	public void setNginxErrorLogPath(String nginxErrorLogPath) {
		this.nginxErrorLogPath = nginxErrorLogPath;
	}
}
