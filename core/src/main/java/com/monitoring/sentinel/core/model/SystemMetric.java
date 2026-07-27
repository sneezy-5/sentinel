package com.monitoring.sentinel.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of a server's system metrics at a given instant (system_metrics hypertable).
 */
public class SystemMetric {

	private String serverId;
	private Instant timestamp;
	private double cpuPercent;
	private int cpuCores;
	private long ramUsedMb;
	private long ramTotalMb;
	private List<DiskUsage> disks;
	private NetworkUsage network;
	private long topProcessRssMb;
	private List<TopProcess> topProcesses;
	private long heaviestFileSizeMb;
	private List<HeaviestFile> heaviestFiles;

	public SystemMetric() {
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public double getCpuPercent() {
		return cpuPercent;
	}

	public void setCpuPercent(double cpuPercent) {
		this.cpuPercent = cpuPercent;
	}

	public int getCpuCores() {
		return cpuCores;
	}

	public void setCpuCores(int cpuCores) {
		this.cpuCores = cpuCores;
	}

	public long getRamUsedMb() {
		return ramUsedMb;
	}

	public void setRamUsedMb(long ramUsedMb) {
		this.ramUsedMb = ramUsedMb;
	}

	public long getRamTotalMb() {
		return ramTotalMb;
	}

	public void setRamTotalMb(long ramTotalMb) {
		this.ramTotalMb = ramTotalMb;
	}

	public List<DiskUsage> getDisks() {
		return disks;
	}

	public void setDisks(List<DiskUsage> disks) {
		this.disks = disks;
	}

	public NetworkUsage getNetwork() {
		return network;
	}

	public void setNetwork(NetworkUsage network) {
		this.network = network;
	}

	public long getTopProcessRssMb() {
		return topProcessRssMb;
	}

	public void setTopProcessRssMb(long topProcessRssMb) {
		this.topProcessRssMb = topProcessRssMb;
	}

	public List<TopProcess> getTopProcesses() {
		return topProcesses;
	}

	public void setTopProcesses(List<TopProcess> topProcesses) {
		this.topProcesses = topProcesses;
	}

	public long getHeaviestFileSizeMb() {
		return heaviestFileSizeMb;
	}

	public void setHeaviestFileSizeMb(long heaviestFileSizeMb) {
		this.heaviestFileSizeMb = heaviestFileSizeMb;
	}

	public List<HeaviestFile> getHeaviestFiles() {
		return heaviestFiles;
	}

	public void setHeaviestFiles(List<HeaviestFile> heaviestFiles) {
		this.heaviestFiles = heaviestFiles;
	}
}
