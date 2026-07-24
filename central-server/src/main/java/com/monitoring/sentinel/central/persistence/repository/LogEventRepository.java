package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.LogEventEntity;
import com.monitoring.sentinel.core.enums.LogEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LogEventRepository extends JpaRepository<LogEventEntity, Long> {

	List<LogEventEntity> findByServiceIdAndEventTypeAndTimestampAfter(
			String serviceId, LogEventType eventType, Instant since);
}
