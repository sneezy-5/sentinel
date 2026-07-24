package com.monitoring.sentinel.core.model;

public class NetworkUsage {

	private long rxBytes;
	private long txBytes;

	public NetworkUsage() {
	}

	public NetworkUsage(long rxBytes, long txBytes) {
		this.rxBytes = rxBytes;
		this.txBytes = txBytes;
	}

	public long getRxBytes() {
		return rxBytes;
	}

	public void setRxBytes(long rxBytes) {
		this.rxBytes = rxBytes;
	}

	public long getTxBytes() {
		return txBytes;
	}

	public void setTxBytes(long txBytes) {
		this.txBytes = txBytes;
	}
}
