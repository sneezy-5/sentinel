package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, String> {

	List<AlertRuleEntity> findByEnabledTrue();

	// serverId is always set (even for service-level rules - see AlertRuleController), so
	// this alone covers both without needing a service-scoped subquery.
	@Modifying
	@Query("DELETE FROM AlertRuleEntity r WHERE r.serverId = :serverId")
	void deleteByServerId(@Param("serverId") String serverId);
}
