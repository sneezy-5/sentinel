package com.monitoring.sentinel.core.model;

public class DiskUsage {

	private String mount;
	private double usedGb;
	private double totalGb;

	public DiskUsage() {
	}

	public DiskUsage(String mount, double usedGb, double totalGb) {
		this.mount = mount;
		this.usedGb = usedGb;
		this.totalGb = totalGb;
	}

	public String getMount() {
		return mount;
	}

	public void setMount(String mount) {
		this.mount = mount;
	}

	public double getUsedGb() {
		return usedGb;
	}

	public void setUsedGb(double usedGb) {
		this.usedGb = usedGb;
	}

	public double getTotalGb() {
		return totalGb;
	}

	public void setTotalGb(double totalGb) {
		this.totalGb = totalGb;
	}
}
