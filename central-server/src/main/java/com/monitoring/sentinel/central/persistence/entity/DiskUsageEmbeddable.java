package com.monitoring.sentinel.central.persistence.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class DiskUsageEmbeddable {

	private String mount;
	private double usedGb;
	private double totalGb;

	protected DiskUsageEmbeddable() {
	}

	public DiskUsageEmbeddable(String mount, double usedGb, double totalGb) {
		this.mount = mount;
		this.usedGb = usedGb;
		this.totalGb = totalGb;
	}

	public String getMount() {
		return mount;
	}

	public double getUsedGb() {
		return usedGb;
	}

	public double getTotalGb() {
		return totalGb;
	}
}
