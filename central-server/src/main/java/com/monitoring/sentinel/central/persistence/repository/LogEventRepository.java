package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.LogEventEntity;
import com.monitoring.sentinel.core.enums.LogEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LogEventRepository extends JpaRepository<LogEventEntity, Long> {

	List<LogEventEntity> findByServiceIdAndEventTypeAndTimestampAfter(
			String serviceId, LogEventType eventType, Instant since);

	// Bulk DELETE - see ServiceMetricRepository.deleteByServerId for why.
	@Modifying
	@Query("DELETE FROM LogEventEntity e WHERE e.serviceId IN (SELECT s.id FROM ServiceEntity s WHERE s.serverId = :serverId)")
	void deleteByServerId(@Param("serverId") String serverId);
}
