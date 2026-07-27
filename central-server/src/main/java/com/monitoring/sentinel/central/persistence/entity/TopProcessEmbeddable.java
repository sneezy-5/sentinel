package com.monitoring.sentinel.central.persistence.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class TopProcessEmbeddable {

	private int pid;
	private String name;
	private long rssMb;

	protected TopProcessEmbeddable() {
	}

	public TopProcessEmbeddable(int pid, String name, long rssMb) {
		this.pid = pid;
		this.name = name;
		this.rssMb = rssMb;
	}

	public int getPid() {
		return pid;
	}

	public String getName() {
		return name;
	}

	public long getRssMb() {
		return rssMb;
	}
}
