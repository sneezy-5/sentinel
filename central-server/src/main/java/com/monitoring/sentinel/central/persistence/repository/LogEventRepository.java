package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.LogEventEntity;
import com.monitoring.sentinel.core.enums.LogEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LogEventRepository extends JpaRepository<LogEventEntity, Long> {

	List<LogEventEntity> findByServiceIdAndEventTypeAndTimestampAfter(
			String serviceId, LogEventType eventType, Instant since);

	// Sums API_CALL rows by "method path" (LogEventEntity.detail) rather than one row per
	// raw request - IngestionService already pre-aggregates per push batch (see its
	// javadoc/comment), this sums those batch-level counts over all of history. Pageable
	// caps it at "top N" without a separate LIMIT dialect quirk.
	@Query("SELECT e.detail AS detail, SUM(e.count) AS totalCount FROM LogEventEntity e "
			+ "WHERE e.serviceId = :serviceId AND e.eventType = :eventType "
			+ "GROUP BY e.detail ORDER BY SUM(e.count) DESC")
	List<EndpointCallCount> topEndpoints(
			@Param("serviceId") String serviceId, @Param("eventType") LogEventType eventType, Pageable pageable);

	interface EndpointCallCount {
		String getDetail();

		long getTotalCount();
	}

	// Bulk DELETE - see ServiceMetricRepository.deleteByServerId for why.
	@Modifying
	@Query("DELETE FROM LogEventEntity e WHERE e.serviceId IN (SELECT s.id FROM ServiceEntity s WHERE s.serverId = :serverId)")
	void deleteByServerId(@Param("serverId") String serverId);
}
