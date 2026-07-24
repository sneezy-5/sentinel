package com.monitoring.sentinel.core.model;

import com.monitoring.sentinel.core.enums.ServerStatus;

import java.time.Instant;

/**
 * A monitored server (VPS). The token is never stored in plain text (see TokenService on the central side).
 */
public class Server {

	private String id;
	private String name;
	private String hostname;
	private ServerStatus status;
	private Instant lastPushAt;

	public Server() {
	}

	public Server(String id, String name, String hostname, ServerStatus status, Instant lastPushAt) {
		this.id = id;
		this.name = name;
		this.hostname = hostname;
		this.status = status;
		this.lastPushAt = lastPushAt;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public ServerStatus getStatus() {
		return status;
	}

	public void setStatus(ServerStatus status) {
		this.status = status;
	}

	public Instant getLastPushAt() {
		return lastPushAt;
	}

	public void setLastPushAt(Instant lastPushAt) {
		this.lastPushAt = lastPushAt;
	}
}
