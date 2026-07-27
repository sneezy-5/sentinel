package com.monitoring.sentinel.agent.collector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the largest files under a configured root (monitoring-agent.yml:
 * heaviestFileScanPath/heaviestFileScanIntervalSeconds) - unlike the C module's system
 * stats (collected every ~10s), a recursive directory walk is too expensive to redo every
 * push cycle, so this caches its last result and only rescans once the configured interval
 * has elapsed. scanIfDue() is meant to be called every cycle regardless - it's a cheap
 * Instant comparison when a rescan isn't due yet.
 */
public class HeaviestFileScanner {

	private static final int TOP_N = 5;

	private final Path rootPath;
	private final Duration interval;

	private List<HeaviestFile> lastResult = List.of();
	private Instant nextScanAt = Instant.EPOCH;

	public HeaviestFileScanner(String rootPath, Duration interval) {
		this.rootPath = (rootPath == null || rootPath.isBlank()) ? null : Path.of(rootPath);
		this.interval = interval;
	}

	public List<HeaviestFile> scanIfDue() {
		if (rootPath == null) {
			return List.of();
		}
		Instant now = Instant.now();
		if (now.isBefore(nextScanAt)) {
			return lastResult;
		}
		nextScanAt = now.plus(interval);
		lastResult = scan();
		return lastResult;
	}

	private List<HeaviestFile> scan() {
		if (!Files.isDirectory(rootPath)) {
			System.err.println("HeaviestFileScanner: " + rootPath + " isn't a directory, skipping this scan");
			return List.of();
		}
		List<HeaviestFile> top = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(rootPath)) {
			stream.filter(Files::isRegularFile).forEach(path -> {
				try {
					insertTop(top, new HeaviestFile(path.toString(), Files.size(path) / (1024 * 1024)));
				} catch (IOException e) {
					// Deleted mid-walk, or unreadable - skip this one file, not the whole scan.
				}
			});
		} catch (IOException | UncheckedIOException e) {
			// A permission error partway through a subdirectory shouldn't discard files
			// already found - keep whatever's in `top` instead of throwing it all away.
			System.err.println("HeaviestFileScanner: scanning " + rootPath + " stopped early: " + e.getMessage());
		}
		return top;
	}

	private void insertTop(List<HeaviestFile> top, HeaviestFile candidate) {
		top.add(candidate);
		top.sort((a, b) -> Long.compare(b.sizeMb(), a.sizeMb()));
		if (top.size() > TOP_N) {
			top.remove(top.size() - 1);
		}
	}
}
