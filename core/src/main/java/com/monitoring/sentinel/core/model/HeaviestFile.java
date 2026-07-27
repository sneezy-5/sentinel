package com.monitoring.sentinel.core.model;

public class HeaviestFile {

	private String path;
	private long sizeMb;

	public HeaviestFile() {
	}

	public HeaviestFile(String path, long sizeMb) {
		this.path = path;
		this.sizeMb = sizeMb;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public long getSizeMb() {
		return sizeMb;
	}

	public void setSizeMb(long sizeMb) {
		this.sizeMb = sizeMb;
	}
}
