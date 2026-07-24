package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.model.DiskUsage;
import com.monitoring.sentinel.core.model.NetworkUsage;
import com.monitoring.sentinel.core.model.SystemMetric;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Backs the system_metrics hypertable (architecture doc, section 4.2). Hibernate's
 * ddl-auto=update (default profile) only creates a plain Postgres table; converting it
 * into a real TimescaleDB hypertable ("SELECT create_hypertable(...)") is a manual/migration
 * step for production deployments, not something Hibernate does.
 */
@Entity
@Table(name = "system_metrics")
public class SystemMetricEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String serverId;
	private Instant timestamp;
	private double cpuPercent;
	private int cpuCores;
	private long ramUsedMb;
	private long ramTotalMb;
	private long rxBytes;
	private long txBytes;

	// Eager: this is a tiny per-row list (one entry per mounted disk), and serialized
	// straight back out over the API - lazy would need an open Hibernate session at
	// response-write time, which open-in-view=false intentionally doesn't provide.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "system_metric_disks", joinColumns = @JoinColumn(name = "system_metric_id"))
	private List<DiskUsageEmbeddable> disks;

	protected SystemMetricEntity() {
	}

	public static SystemMetricEntity fromModel(SystemMetric metric) {
		SystemMetricEntity entity = new SystemMetricEntity();
		entity.serverId = metric.getServerId();
		entity.timestamp = metric.getTimestamp();
		entity.cpuPercent = metric.getCpuPercent();
		entity.cpuCores = metric.getCpuCores();
		entity.ramUsedMb = metric.getRamUsedMb();
		entity.ramTotalMb = metric.getRamTotalMb();
		if (metric.getNetwork() != null) {
			entity.rxBytes = metric.getNetwork().getRxBytes();
			entity.txBytes = metric.getNetwork().getTxBytes();
		}
		if (metric.getDisks() != null) {
			entity.disks = metric.getDisks().stream()
					.map(d -> new DiskUsageEmbeddable(d.getMount(), d.getUsedGb(), d.getTotalGb()))
					.collect(Collectors.toList());
		}
		return entity;
	}

	public SystemMetric toModel() {
		SystemMetric metric = new SystemMetric();
		metric.setServerId(serverId);
		metric.setTimestamp(timestamp);
		metric.setCpuPercent(cpuPercent);
		metric.setCpuCores(cpuCores);
		metric.setRamUsedMb(ramUsedMb);
		metric.setRamTotalMb(ramTotalMb);
		metric.setNetwork(new NetworkUsage(rxBytes, txBytes));
		if (disks != null) {
			metric.setDisks(disks.stream()
					.map(d -> new DiskUsage(d.getMount(), d.getUsedGb(), d.getTotalGb()))
					.collect(Collectors.toList()));
		}
		return metric;
	}

	public Long getId() {
		return id;
	}

	public String getServerId() {
		return serverId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public double getCpuPercent() {
		return cpuPercent;
	}

	public long getRamUsedMb() {
		return ramUsedMb;
	}

	public long getRamTotalMb() {
		return ramTotalMb;
	}

	public int getCpuCores() {
		return cpuCores;
	}

	public long getRxBytes() {
		return rxBytes;
	}

	public long getTxBytes() {
		return txBytes;
	}

	public List<DiskUsageEmbeddable> getDisks() {
		return disks;
	}
}
