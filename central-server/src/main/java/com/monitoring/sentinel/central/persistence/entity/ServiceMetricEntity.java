package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.model.ServiceMetric;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "service_metrics")
public class ServiceMetricEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String serviceId;
	private Instant timestamp;
	private double cpuPercent;
	private long memMb;

	protected ServiceMetricEntity() {
	}

	public static ServiceMetricEntity fromModel(ServiceMetric metric) {
		ServiceMetricEntity entity = new ServiceMetricEntity();
		entity.serviceId = metric.getServiceId();
		entity.timestamp = metric.getTimestamp();
		entity.cpuPercent = metric.getCpuPercent();
		entity.memMb = metric.getMemMb();
		return entity;
	}

	public ServiceMetric toModel() {
		ServiceMetric metric = new ServiceMetric();
		metric.setServiceId(serviceId);
		metric.setTimestamp(timestamp);
		metric.setCpuPercent(cpuPercent);
		metric.setMemMb(memMb);
		return metric;
	}

	public String getServiceId() {
		return serviceId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public double getCpuPercent() {
		return cpuPercent;
	}

	public long getMemMb() {
		return memMb;
	}
}
