package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.SystemMetricEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemMetricRepository extends JpaRepository<SystemMetricEntity, Long> {

	List<SystemMetricEntity> findByServerIdOrderByTimestampDesc(String serverId);

	List<SystemMetricEntity> findByServerIdOrderByTimestampDesc(String serverId, Pageable pageable);

	Optional<SystemMetricEntity> findFirstByServerIdOrderByTimestampDesc(String serverId);

	// Bulk DELETE - see ServiceMetricRepository.deleteByServerId for why. Likely the single
	// biggest offender of the five: this hypertable grows one row per server per push cycle
	// (every ~30s), so a long-lived server can easily have hundreds of thousands of rows.
	@Modifying
	@Query("DELETE FROM SystemMetricEntity m WHERE m.serverId = :serverId")
	void deleteByServerId(@Param("serverId") String serverId);
}
