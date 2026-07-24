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
}
