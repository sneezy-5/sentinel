package com.monitoring.sentinel.core.model;

import com.monitoring.sentinel.core.enums.ServiceStatus;
import com.monitoring.sentinel.core.enums.ServiceType;

import java.util.Map;

/**
 * A service discovered on a server. The id must follow the "type:name" format
 * (see ServiceIdValidator) to stay stable across restarts.
 */
public class Service {

	private String id;
	private String serverId;
	private String name;
	private ServiceType type;
	private ServiceStatus status;
	private Map<String, String> metadata;

	public Service() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ServiceType getType() {
		return type;
	}

	public void setType(ServiceType type) {
		this.type = type;
	}

	public ServiceStatus getStatus() {
		return status;
	}

	public void setStatus(ServiceStatus status) {
		this.status = status;
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}
}
