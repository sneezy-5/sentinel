package com.monitoring.sentinel.central.persistence.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class HeaviestFileEmbeddable {

	private String path;
	private long sizeMb;

	protected HeaviestFileEmbeddable() {
	}

	public HeaviestFileEmbeddable(String path, long sizeMb) {
		this.path = path;
		this.sizeMb = sizeMb;
	}

	public String getPath() {
		return path;
	}

	public long getSizeMb() {
		return sizeMb;
	}
}
