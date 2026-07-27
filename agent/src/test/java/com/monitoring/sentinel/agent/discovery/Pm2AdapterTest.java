package com.monitoring.sentinel.agent.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pm2AdapterTest {

	private final Pm2Adapter adapter = new Pm2Adapter();

	// Trimmed down from a real "pm2 jlist" payload (fork mode, online, restart_time 0) -
	// only the fields toDiscoveredService actually reads, real pm2 output also carries
	// megabytes of env vars/axm_monitor blobs per process that aren't relevant here.
	private static final String ONLINE_PROCESS = """
			{
			  "pid": 3861244,
			  "name": "rh-backend",
			  "pm2_env": { "status": "online", "restart_time": 0 },
			  "pm_id": 0,
			  "monit": { "memory": 92405760, "cpu": 0.7 }
			}""";

	private static final String CLUSTER_PROCESS = """
			{
			  "pid": 1949831,
			  "name": "whatsapp-worker",
			  "pm2_env": { "status": "online", "restart_time": 13 },
			  "pm_id": 1,
			  "monit": { "memory": 169869312, "cpu": 0 }
			}""";

	@Test
	void parsesAnOnlineForkModeProcess() throws IOException {
		List<DiscoveredService> services = adapter.parseJlist("[" + ONLINE_PROCESS + "]");

		assertEquals(1, services.size());
		DiscoveredService service = services.get(0);
		assertEquals("pm2:rh-backend", service.id());
		assertEquals("rh-backend", service.name());
		assertEquals("pm2", service.type());
		assertEquals("running", service.status());
		assertEquals(0.7, service.cpuPercent());
		assertEquals(88, service.memMb());
		assertEquals(0, service.diskMb());
		assertEquals(Map.of("pm2_id", "0", "restarts", "0", "log_native_id", "|"), service.metadata());
	}

	@Test
	void extractsLogPathsFromPm2EnvIntoLogNativeId() throws IOException {
		String withLogPaths = """
				{
				  "pid": 1,
				  "name": "whatsapp-worker",
				  "pm2_env": {
				    "status": "online", "restart_time": 0,
				    "pm_out_log_path": "/home/sneezy/.pm2/logs/whatsapp-worker-out-1.log",
				    "pm_err_log_path": "/home/sneezy/.pm2/logs/whatsapp-worker-error-1.log"
				  },
				  "pm_id": 1,
				  "monit": { "memory": 0, "cpu": 0 }
				}""";

		List<DiscoveredService> services = adapter.parseJlist("[" + withLogPaths + "]");

		assertEquals("/home/sneezy/.pm2/logs/whatsapp-worker-out-1.log|/home/sneezy/.pm2/logs/whatsapp-worker-error-1.log",
				services.get(0).metadata().get("log_native_id"));
	}

	@Test
	void parsesMultipleProcesses() throws IOException {
		List<DiscoveredService> services = adapter.parseJlist("[" + ONLINE_PROCESS + "," + CLUSTER_PROCESS + "]");

		assertEquals(2, services.size());
		assertEquals("pm2:whatsapp-worker", services.get(1).id());
		assertEquals(162, services.get(1).memMb());
		assertEquals("13", services.get(1).metadata().get("restarts"));
	}

	@Test
	void mapsAnyNonOnlineStatusToStopped() throws IOException {
		String stopped = """
				{
				  "pid": 1,
				  "name": "worker",
				  "pm2_env": { "status": "stopped", "restart_time": 2 },
				  "pm_id": 2,
				  "monit": { "memory": 0, "cpu": 0 }
				}""";

		List<DiscoveredService> services = adapter.parseJlist("[" + stopped + "]");

		assertEquals("stopped", services.get(0).status());
	}

	@Test
	void sanitizesNamesWithCharsOutsideTheStableIdAlphabet() throws IOException {
		String weirdName = """
				{
				  "pid": 1,
				  "name": "My.App Name",
				  "pm2_env": { "status": "online", "restart_time": 0 },
				  "pm_id": 0,
				  "monit": { "memory": 0, "cpu": 0 }
				}""";

		List<DiscoveredService> services = adapter.parseJlist("[" + weirdName + "]");

		assertEquals("pm2:my-app-name", services.get(0).id());
		assertEquals("My.App Name", services.get(0).name());
	}

	@Test
	void emptyListProducesNoServices() throws IOException {
		assertTrue(adapter.parseJlist("[]").isEmpty());
	}

	@Test
	void parseLineClassifiesErrorKeyword() {
		LogSource.LogLine result = adapter.parseLine("Error: connection refused");

		assertEquals("error", result.level());
		assertEquals("Error: connection refused", result.message());
	}

	@Test
	void parseLineClassifiesWarnKeyword() {
		assertEquals("warn", adapter.parseLine("Warning: retrying").level());
	}

	@Test
	void parseLineWithNoTimestampDefaultsToInfo() {
		LogSource.LogLine result = adapter.parseLine("server listening on port 3000");

		assertEquals("info", result.level());
		assertEquals("server listening on port 3000", result.message());
	}

	@Test
	void parseLineWithAnIsoTimestampPrefixExtractsIt() {
		LogSource.LogLine result = adapter.parseLine("2026-07-27T14:00:00Z worker started");

		assertEquals(Instant.parse("2026-07-27T14:00:00Z"), result.timestamp());
		assertEquals("worker started", result.message());
	}

	@Test
	void firstTailCallSkipsToEndOfFileWithoutReturningLines(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("app-out.log");
		Files.writeString(file, "old line 1\nold line 2\n", StandardCharsets.UTF_8);

		List<String> lines = adapter.tailNewLines(file);

		assertTrue(lines.isEmpty());
	}

	@Test
	void subsequentTailCallReturnsOnlyNewlyAppendedLines(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("app-out.log");
		Files.writeString(file, "line 1\n", StandardCharsets.UTF_8);
		adapter.tailNewLines(file);

		Files.writeString(file, "line 2\nline 3\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

		assertEquals(List.of("line 2", "line 3"), adapter.tailNewLines(file));
	}

	@Test
	void fetchLogsCombinesOutAndErrorStreams(@TempDir Path tempDir) throws IOException {
		Path outLog = tempDir.resolve("app-out.log");
		Path errLog = tempDir.resolve("app-error.log");
		Files.createFile(outLog);
		Files.createFile(errLog);
		adapter.fetchLogs(outLog + "|" + errLog, null); // first call: skips existing (empty) backlog

		Files.writeString(outLog, "listening on 3000\n", StandardCharsets.UTF_8);
		Files.writeString(errLog, "Error: crashed\n", StandardCharsets.UTF_8);

		List<LogSource.LogLine> lines = adapter.fetchLogs(outLog + "|" + errLog, null);

		assertEquals(2, lines.size());
		assertEquals("info", lines.get(0).level());
		assertEquals("error", lines.get(1).level());
	}

	@Test
	void fetchLogsSkipsBlankPaths() {
		assertTrue(adapter.fetchLogs("|", null).isEmpty());
	}
}
