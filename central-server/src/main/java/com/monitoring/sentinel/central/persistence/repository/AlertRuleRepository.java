package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, String> {

	List<AlertRuleEntity> findByEnabledTrue();
}
