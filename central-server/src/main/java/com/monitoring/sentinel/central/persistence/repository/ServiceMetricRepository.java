package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceMetricRepository extends JpaRepository<ServiceMetricEntity, Long> {

	List<ServiceMetricEntity> findByServiceIdOrderByTimestampDesc(String serviceId);

	Optional<ServiceMetricEntity> findFirstByServiceIdOrderByTimestampDesc(String serviceId);
}
