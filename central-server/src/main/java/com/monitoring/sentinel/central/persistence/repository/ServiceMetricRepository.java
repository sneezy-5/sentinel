package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceMetricRepository extends JpaRepository<ServiceMetricEntity, Long> {

	List<ServiceMetricEntity> findByServiceIdOrderByTimestampDesc(String serviceId);

	Optional<ServiceMetricEntity> findFirstByServiceIdOrderByTimestampDesc(String serviceId);

	// A single bulk DELETE instead of a derived delete method - those load every matching
	// row into the persistence context and issue one DELETE per row, which is what made
	// server deletion time out (HTTP 504) once a server had accumulated real metric history.
	@Modifying
	@Query("DELETE FROM ServiceMetricEntity m WHERE m.serviceId IN (SELECT s.id FROM ServiceEntity s WHERE s.serverId = :serverId)")
	void deleteByServerId(@Param("serverId") String serverId);
}
