package com.monitoring.sentinel.central.api;

import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import com.monitoring.sentinel.central.persistence.entity.SystemMetricEntity;
import com.monitoring.sentinel.central.persistence.repository.ServiceMetricRepository;
import com.monitoring.sentinel.central.persistence.repository.SystemMetricRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Time series consumed by the dashboard (architecture doc, section 4.1). */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

	private final SystemMetricRepository systemMetricRepository;
	private final ServiceMetricRepository serviceMetricRepository;

	public MetricsController(
			SystemMetricRepository systemMetricRepository, ServiceMetricRepository serviceMetricRepository) {
		this.systemMetricRepository = systemMetricRepository;
		this.serviceMetricRepository = serviceMetricRepository;
	}

	@GetMapping("/servers/{serverId}")
	public List<SystemMetricEntity> systemMetrics(
			@PathVariable String serverId, @RequestParam(defaultValue = "120") int limit) {
		List<SystemMetricEntity> latestFirst =
				systemMetricRepository.findByServerIdOrderByTimestampDesc(serverId, PageRequest.of(0, limit));
		// The chart wants oldest-to-newest (left-to-right), the query is newest-first.
		List<SystemMetricEntity> chronological = new ArrayList<>(latestFirst);
		Collections.reverse(chronological);
		return chronological;
	}

	@GetMapping("/services/{serviceId}")
	public List<ServiceMetricEntity> serviceMetrics(@PathVariable String serviceId) {
		return serviceMetricRepository.findByServiceIdOrderByTimestampDesc(serviceId);
	}
}
