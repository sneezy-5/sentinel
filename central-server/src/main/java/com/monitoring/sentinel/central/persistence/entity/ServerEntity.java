package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.enums.ServerStatus;
import com.monitoring.sentinel.core.model.Server;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "servers")
public class ServerEntity {

	@Id
	private String id;

	private String name;
	private String hostname;

	/** Never the plain-text token: see TokenService. Unique to allow findByTokenHash. */
	@Column(unique = true, nullable = false)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	private ServerStatus status;

	private Instant lastPushAt;

	protected ServerEntity() {
	}

	public ServerEntity(String id, String name, String hostname, String tokenHash) {
		this.id = id;
		this.name = name;
		this.hostname = hostname;
		this.tokenHash = tokenHash;
		this.status = ServerStatus.UNKNOWN;
	}

	public Server toModel() {
		return new Server(id, name, hostname, status, lastPushAt);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getHostname() {
		return hostname;
	}

	public String getTokenHash() {
		return tokenHash;
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
