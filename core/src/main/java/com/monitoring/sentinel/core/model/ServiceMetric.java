package com.monitoring.sentinel.core.model;

import java.time.Instant;

public class ServiceMetric {

	private String serviceId;
	private Instant timestamp;
	private double cpuPercent;
	private long memMb;
	private long diskMb;

	public ServiceMetric() {
	}

	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
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

	public long getMemMb() {
		return memMb;
	}

	public void setMemMb(long memMb) {
		this.memMb = memMb;
	}

	/** Container's writable layer size (Docker's "SizeRw" - what `docker ps --size` shows
	 * as SIZE), not the full image+layers virtual size. 0 for adapters that don't report it. */
	public long getDiskMb() {
		return diskMb;
	}

	public void setDiskMb(long diskMb) {
		this.diskMb = diskMb;
	}
}
