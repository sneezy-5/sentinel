package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

	List<ServiceEntity> findByServerId(String serverId);

	// Bulk DELETE - see ServiceMetricRepository.deleteByServerId for why. Must run after the
	// service_metrics/log_entries/log_events deletes (ServerController), since those resolve
	// their target rows via a subquery over these very rows.
	@Modifying
	@Query("DELETE FROM ServiceEntity s WHERE s.serverId = :serverId")
	void deleteByServerId(@Param("serverId") String serverId);
}
