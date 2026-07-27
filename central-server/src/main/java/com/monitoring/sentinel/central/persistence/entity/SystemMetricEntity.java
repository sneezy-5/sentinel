package com.monitoring.sentinel.central.persistence.entity;

import com.monitoring.sentinel.core.model.DiskUsage;
import com.monitoring.sentinel.core.model.HeaviestFile;
import com.monitoring.sentinel.core.model.NetworkUsage;
import com.monitoring.sentinel.core.model.SystemMetric;
import com.monitoring.sentinel.core.model.TopProcess;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
	private long topProcessRssMb;
	private long heaviestFileSizeMb;

	// Eager: this is a tiny per-row list (one entry per mounted disk), and serialized
	// straight back out over the API - lazy would need an open Hibernate session at
	// response-write time, which open-in-view=false intentionally doesn't provide.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "system_metric_disks", joinColumns = @JoinColumn(name = "system_metric_id"))
	private List<DiskUsageEmbeddable> disks;

	// Same reasoning as disks - tiny (PROCESS_STATS_TOP_N, currently 5), eager, serialized
	// straight back out. NO_CONSTRAINT (unlike disks): on any deployment that already ran
	// init-hypertables.sql, system_metrics.id has no unique/PK constraint anymore (dropped
	// for create_hypertable()) - a real FK from a table Hibernate creates *after* that point
	// (i.e. this one, added later than the initial schema) would fail outright, since
	// Postgres won't let you reference a column with no unique constraint. disks predates
	// the hypertable conversion so its FK was created while system_metrics.id still had one;
	// this table wasn't so lucky.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "system_metric_top_processes",
			joinColumns = @JoinColumn(name = "system_metric_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)))
	private List<TopProcessEmbeddable> topProcesses;

	// Same reasoning as topProcesses (NO_CONSTRAINT included).
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "system_metric_heaviest_files",
			joinColumns = @JoinColumn(name = "system_metric_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)))
	private List<HeaviestFileEmbeddable> heaviestFiles;

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
		entity.topProcessRssMb = metric.getTopProcessRssMb();
		if (metric.getTopProcesses() != null) {
			entity.topProcesses = metric.getTopProcesses().stream()
					.map(p -> new TopProcessEmbeddable(p.getPid(), p.getName(), p.getRssMb()))
					.collect(Collectors.toList());
		}
		entity.heaviestFileSizeMb = metric.getHeaviestFileSizeMb();
		if (metric.getHeaviestFiles() != null) {
			entity.heaviestFiles = metric.getHeaviestFiles().stream()
					.map(f -> new HeaviestFileEmbeddable(f.getPath(), f.getSizeMb()))
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
		metric.setTopProcessRssMb(topProcessRssMb);
		if (topProcesses != null) {
			metric.setTopProcesses(topProcesses.stream()
					.map(p -> new TopProcess(p.getPid(), p.getName(), p.getRssMb()))
					.collect(Collectors.toList()));
		}
		metric.setHeaviestFileSizeMb(heaviestFileSizeMb);
		if (heaviestFiles != null) {
			metric.setHeaviestFiles(heaviestFiles.stream()
					.map(f -> new HeaviestFile(f.getPath(), f.getSizeMb()))
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

	public long getTopProcessRssMb() {
		return topProcessRssMb;
	}

	public List<TopProcessEmbeddable> getTopProcesses() {
		return topProcesses;
	}

	public long getHeaviestFileSizeMb() {
		return heaviestFileSizeMb;
	}

	public List<HeaviestFileEmbeddable> getHeaviestFiles() {
		return heaviestFiles;
	}
}
