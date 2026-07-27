package com.monitoring.sentinel.agent.collector;

import java.util.List;

public record SystemStats(
		double cpuPercent,
		int cpuCores,
		long ramUsedMb,
		long ramTotalMb,
		List<Disk> disks,
		long rxBytes,
		long txBytes,
		long topProcessRssMb,
		List<TopProcess> topProcesses) {

	public record Disk(String mount, double usedGb, double totalGb) {
	}

	public record TopProcess(int pid, String name, long rssMb) {
	}
}
