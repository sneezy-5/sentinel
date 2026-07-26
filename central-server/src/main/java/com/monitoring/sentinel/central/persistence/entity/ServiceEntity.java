package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.enums.ServiceStatus;
import com.monitoring.sentinel.core.enums.ServiceType;
import com.monitoring.sentinel.core.model.Service;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.util.Map;

@Entity
@Table(name = "services")
public class ServiceEntity {

	/** Stable "type:name" id (see ServiceIdValidator in the core module). */
	@Id
	private String id;

	private String serverId;
	private String name;

	@Enumerated(EnumType.STRING)
	private ServiceType type;

	@Enumerated(EnumType.STRING)
	private ServiceStatus status;

	@ElementCollection
	@CollectionTable(name = "service_metadata", joinColumns = @JoinColumn(name = "service_id"))
	@MapKeyColumn(name = "meta_key")
	private Map<String, String> metadata;

	protected ServiceEntity() {
	}

	public static ServiceEntity fromModel(Service service) {
		ServiceEntity entity = new ServiceEntity();
		entity.id = service.getId();
		entity.serverId = service.getServerId();
		entity.name = service.getName();
		entity.type = service.getType();
		entity.status = service.getStatus();
		entity.metadata = service.getMetadata();
		return entity;
	}

	public Service toModel() {
		Service service = new Service();
		service.setId(id);
		service.setServerId(serverId);
		service.setName(name);
		service.setType(type);
		service.setStatus(status);
		service.setMetadata(metadata);
		return service;
	}

	public String getId() {
		return id;
	}

	public String getServerId() {
		return serverId;
	}

	public String getName() {
		return name;
	}

	public ServiceType getType() {
		return type;
	}

	public ServiceStatus getStatus() {
		return status;
	}

	public void setStatus(ServiceStatus status) {
		this.status = status;
	}
}
