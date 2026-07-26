package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.LogEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntryEntity, Long> {

	List<LogEntryEntity> findByServiceIdOrderByTimestampDesc(String serviceId, Pageable pageable);

	// Bulk DELETE - see ServiceMetricRepository.deleteByServerId for why (this table can hold
	// a lot more rows than service_metrics, so the row-by-row derived delete was even worse
	// here).
	@Modifying
	@Query("DELETE FROM LogEntryEntity e WHERE e.serviceId IN (SELECT s.id FROM ServiceEntity s WHERE s.serverId = :serverId)")
	void deleteByServerId(@Param("serverId") String serverId);
}
