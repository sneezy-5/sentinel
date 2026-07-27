package com.monitoring.sentinel.core.model;

public class TopProcess {

	private int pid;
	private String name;
	private long rssMb;

	public TopProcess() {
	}

	public TopProcess(int pid, String name, long rssMb) {
		this.pid = pid;
		this.name = name;
		this.rssMb = rssMb;
	}

	public int getPid() {
		return pid;
	}

	public void setPid(int pid) {
		this.pid = pid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getRssMb() {
		return rssMb;
	}

	public void setRssMb(long rssMb) {
		this.rssMb = rssMb;
	}
}
