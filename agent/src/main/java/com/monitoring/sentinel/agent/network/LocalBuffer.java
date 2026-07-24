package com.monitoring.sentinel.agent.network;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Local buffer (JSONL file) used when the central is unreachable (architecture doc,
 * section 3.4). Each line: "&lt;epochMillis&gt;\t&lt;json&gt;". Purges entries older than
 * maxAge on every drain() call, so it never grows unbounded.
 */
public class LocalBuffer {

	private final Path bufferFile;
	private final Duration maxAge;

	public LocalBuffer(Path bufferFile, Duration maxAge) {
		this.bufferFile = bufferFile;
		this.maxAge = maxAge;
	}

	public void append(String json) {
		String line = Instant.now().toEpochMilli() + "\t" + json.replace("\n", "") + System.lineSeparator();
		try {
			Files.createDirectories(bufferFile.getParent());
			Files.writeString(bufferFile, line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Returns entries still valid (not older than maxAge) and empties the file. */
	public List<String> drain() {
		if (!Files.exists(bufferFile)) {
			return List.of();
		}
		Instant cutoff = Instant.now().minus(maxAge);
		List<String> valid = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(bufferFile, StandardCharsets.UTF_8)) {
				int tab = line.indexOf('\t');
				if (tab < 0) {
					continue;
				}
				long epochMillis = Long.parseLong(line.substring(0, tab));
				if (Instant.ofEpochMilli(epochMillis).isAfter(cutoff)) {
					valid.add(line.substring(tab + 1));
				}
			}
			Files.deleteIfExists(bufferFile);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return valid;
	}
}
