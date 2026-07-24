package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.LogEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntryEntity, Long> {

	List<LogEntryEntity> findByServiceIdOrderByTimestampDesc(String serviceId, Pageable pageable);

	void deleteByServiceId(String serviceId);
}
