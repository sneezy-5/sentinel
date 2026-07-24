package com.monitoring.sentinel.agent.collector;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bridge to the C module (architecture doc, section 3.1/3.2). Choice: a local file rather
 * than JNI/JNA - a native crash must not be able to take the agent's JVM down with it, and
 * a file/pipe can be debugged and restarted independently on both sides. The C module
 * writes a JSON snapshot every few seconds to the agreed-upon location; we just read the
 * latest one.
 */
public class NativeStatsClient {

	private final Path statsFile;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public NativeStatsClient(Path statsFile) {
		this.statsFile = statsFile;
	}

	public SystemStats readLatest() {
		try {
			byte[] content = Files.readAllBytes(statsFile);
			return objectMapper.readValue(content, SystemStats.class);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read system stats from " + statsFile
					+ " (is the C module (agent-native) running?)", e);
		}
	}
}
