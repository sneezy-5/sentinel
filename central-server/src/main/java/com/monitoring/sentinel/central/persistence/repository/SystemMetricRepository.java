package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.SystemMetricEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemMetricRepository extends JpaRepository<SystemMetricEntity, Long> {

	List<SystemMetricEntity> findByServerIdOrderByTimestampDesc(String serverId);

	List<SystemMetricEntity> findByServerIdOrderByTimestampDesc(String serverId, Pageable pageable);

	Optional<SystemMetricEntity> findFirstByServerIdOrderByTimestampDesc(String serverId);
}
