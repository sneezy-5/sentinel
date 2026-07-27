package com.monitoring.sentinel.agent.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxAdapterTest {

	private final NginxAdapter adapter = new NginxAdapter(Path.of("unused"), Path.of("unused"));

	@Test
	void parsesAStandardCombinedAccessLine() {
		String line = "127.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /api/stock HTTP/1.1\" 200 612 \"-\" \"curl/8.0\"";

		LogSource.LogLine result = adapter.parseAccessLine(line);

		assertEquals(Instant.parse("2023-10-10T13:55:36Z"), result.timestamp());
		assertEquals("info", result.level());
		assertEquals(line, result.message());
	}

	@Test
	void classifiesServerErrorStatusAsError() {
		String line = "127.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /api/stock HTTP/1.1\" 502 0 \"-\" \"curl/8.0\"";

		assertEquals("error", adapter.parseAccessLine(line).level());
	}

	@Test
	void classifiesClientErrorStatusAsWarn() {
		String line = "127.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /missing HTTP/1.1\" 404 0 \"-\" \"curl/8.0\"";

		assertEquals("warn", adapter.parseAccessLine(line).level());
	}

	@Test
	void unparsableAccessLineIsKeptWholeAsInfo() {
		LogSource.LogLine result = adapter.parseAccessLine("not a valid access log line");

		assertEquals("info", result.level());
		assertEquals("not a valid access log line", result.message());
	}

	@Test
	void parsesNginxErrorLogLevelTag() {
		String line = "2023/10/10 13:55:36 [error] 1234#0: *1 connect() failed";

		LogSource.LogLine result = adapter.parseErrorLine(line);

		assertEquals(Instant.parse("2023-10-10T13:55:36Z").getEpochSecond(), result.timestamp().getEpochSecond());
		assertEquals("error", result.level());
	}

	@Test
	void parsesNginxWarnLogLevelTag() {
		String line = "2023/10/10 13:55:36 [warn] 1234#0: something";

		assertEquals("warn", adapter.parseErrorLine(line).level());
	}

	@Test
	void nonErrorNonWarnLevelTagIsInfo() {
		String line = "2023/10/10 13:55:36 [notice] nginx started";

		assertEquals("info", adapter.parseErrorLine(line).level());
	}

	@Test
	void firstTailCallSkipsToEndOfFileWithoutReturningLines(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("access.log");
		Files.writeString(file, "old line 1\nold line 2\n", StandardCharsets.UTF_8);

		var result = adapter.tail(file, -1);

		assertTrue(result.lines().isEmpty());
		assertEquals(Files.size(file), result.newOffset());
	}

	@Test
	void subsequentTailCallReturnsOnlyNewlyAppendedLines(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("access.log");
		Files.writeString(file, "line 1\n", StandardCharsets.UTF_8);
		long offsetAfterFirstLine = Files.size(file);

		Files.writeString(file, "line 2\nline 3\n", StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.APPEND);

		var result = adapter.tail(file, offsetAfterFirstLine);

		assertEquals(List.of("line 2", "line 3"), result.lines());
		assertEquals(Files.size(file), result.newOffset());
	}

	@Test
	void tailStartsOverWhenFileShrinksBelowThePreviousOffset(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("access.log");
		Files.writeString(file, "a fairly long line that will be rotated away\n", StandardCharsets.UTF_8);
		long largeOffset = Files.size(file) + 1000;

		Files.writeString(file, "fresh line after rotation\n", StandardCharsets.UTF_8);

		var result = adapter.tail(file, largeOffset);

		assertEquals(List.of("fresh line after rotation"), result.lines());
	}

	@Test
	void isAvailableIsFalseWhenAccessLogIsMissing(@TempDir Path tempDir) {
		NginxAdapter missing = new NginxAdapter(tempDir.resolve("does-not-exist.log"), tempDir.resolve("err.log"));

		assertFalse(missing.isAvailable());
	}

	@Test
	void isAvailableIsTrueWhenAccessLogExists(@TempDir Path tempDir) throws IOException {
		Path access = tempDir.resolve("access.log");
		Files.createFile(access);
		NginxAdapter present = new NginxAdapter(access, tempDir.resolve("err.log"));

		assertTrue(present.isAvailable());
	}

	@Test
	void discoverReturnsASingleNginxService() {
		List<DiscoveredService> services = adapter.discover();

		assertEquals(1, services.size());
		assertEquals("nginx:main", services.get(0).id());
		assertEquals("nginx", services.get(0).type());
	}

	@Test
	void fromConfigTreatsBlankPathAsDisabled(@TempDir Path tempDir) {
		NginxAdapter disabled = NginxAdapter.fromConfig("", "");

		assertFalse(disabled.isAvailable());
	}
}
