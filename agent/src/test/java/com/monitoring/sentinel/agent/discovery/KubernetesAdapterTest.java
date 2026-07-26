package com.monitoring.sentinel.agent.discovery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesAdapterTest {

	private final KubernetesAdapter adapter = new KubernetesAdapter();

	@Test
	void parsesPlainCoresAndBytes() {
		assertEquals(2.0, KubernetesAdapter.parseQuantity("2"));
		assertEquals(128974848.0, KubernetesAdapter.parseQuantity("128974848"));
	}

	@Test
	void parsesDecimalCpuSuffixes() {
		assertEquals(0.25, KubernetesAdapter.parseQuantity("250m"), 1e-9);
		assertEquals(0.000001, KubernetesAdapter.parseQuantity("1000n"), 1e-12);
	}

	@Test
	void parsesBinaryMemorySuffixes() {
		assertEquals(512.0 * 1024 * 1024, KubernetesAdapter.parseQuantity("512Mi"));
		assertEquals(1024.0 * 1024 * 1024, KubernetesAdapter.parseQuantity("1Gi"));
		assertEquals(128.0 * 1024, KubernetesAdapter.parseQuantity("128Ki"));
	}

	@Test
	void parsesDecimalMemorySuffixes() {
		assertEquals(500_000.0, KubernetesAdapter.parseQuantity("500k"));
		assertEquals(2_000_000_000.0, KubernetesAdapter.parseQuantity("2G"));
	}

	@Test
	void emptyOrNullQuantityIsZero() {
		assertEquals(0.0, KubernetesAdapter.parseQuantity(""));
		assertEquals(0.0, KubernetesAdapter.parseQuantity(null));
	}

	@Test
	void parseLinesSplitsTimestampAndClassifiesLevel() {
		String raw = "2026-07-24T12:00:00.000000000Z first\n2026-07-24T12:00:01.000000000Z ERROR: second\n";

		List<LogSource.LogLine> lines = adapter.parseLines(raw);

		assertEquals(2, lines.size());
		assertEquals(Instant.parse("2026-07-24T12:00:00.000000000Z"), lines.get(0).timestamp());
		assertEquals("first", lines.get(0).message());
		assertEquals("info", lines.get(0).level());
		assertEquals("ERROR: second", lines.get(1).message());
		assertEquals("error", lines.get(1).level());
	}

	@Test
	void parseLinesSkipsBlankLines() {
		List<LogSource.LogLine> lines = adapter.parseLines("\n2026-07-24T12:00:00.000000000Z real line\n\n");

		assertEquals(1, lines.size());
	}

	@Test
	void parseLinesKeepsLineWithoutTimestampWhole() {
		List<LogSource.LogLine> lines = adapter.parseLines("just some text with spaces\n");

		assertEquals(1, lines.size());
		assertEquals("just some text with spaces", lines.get(0).message());
	}

	@Test
	void fetchLogsReturnsEmptyForMalformedNativeId() {
		assertTrue(adapter.fetchLogs("not-enough-parts", Instant.now()).isEmpty());
	}

	@Test
	void discoverReturnsEmptyWithoutNodeNameEnvVar() {
		// NODE_NAME isn't set in the test environment - discover() must fail soft, not throw.
		assertTrue(adapter.discover().isEmpty());
	}
}
