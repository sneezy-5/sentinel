package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

	List<ServiceEntity> findByServerId(String serverId);

	void deleteByServerId(String serverId);
}
