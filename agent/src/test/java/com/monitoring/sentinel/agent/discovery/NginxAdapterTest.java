package com.monitoring.sentinel.agent.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

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
	void discoverReturnsJustTheMainServiceWhenNoVhostConfigExists() {
		// The shared `adapter` points at a nonexistent nginx config (default real /etc/nginx
		// paths, absent in the test sandbox) - discoverVhostLogPaths() finds nothing, so only
		// the catch-all service is reported.
		List<DiscoveredService> services = adapter.discover();

		assertEquals(1, services.size());
		assertEquals("nginx:main", services.get(0).id());
		assertEquals("nginx", services.get(0).type());
	}

	@Test
	void fromConfigTreatsBlankPathAsDisabled() {
		NginxAdapter disabled = NginxAdapter.fromConfig("", "");

		assertFalse(disabled.isAvailable());
	}

	@Test
	void vhostServiceNameStripsLogExtensionAndAccessSuffix() {
		assertEquals("onda-backend", adapter.vhostServiceName(Path.of("/var/log/nginx/onda-backend.access.log")));
		assertEquals("waretrack", adapter.vhostServiceName(Path.of("/var/log/nginx/waretrack.log")));
		assertEquals("django_stack", adapter.vhostServiceName(Path.of("/var/log/nginx/django_stack-access.log")));
	}

	@Test
	void vhostServiceNameFallsBackRatherThanReturningBlank() {
		assertEquals("access", adapter.vhostServiceName(Path.of("/var/log/nginx/access.log")));
	}

	@Test
	void discoverVhostLogPathsFindsAccessLogDirectivesUnderSitesEnabled(@TempDir Path tempDir) throws IOException {
		Path sitesEnabled = tempDir.resolve("sites-enabled");
		Files.createDirectory(sitesEnabled);
		Files.writeString(sitesEnabled.resolve("onda-backend.conf"), """
				server {
				    server_name onda-backend.example.com;
				    access_log /var/log/nginx/onda-backend.access.log;
				}
				""", StandardCharsets.UTF_8);
		Files.writeString(sitesEnabled.resolve("waretrack.conf"), """
				server {
				    server_name waretrack.example.com;
				    access_log /var/log/nginx/waretrack.log;
				}
				""", StandardCharsets.UTF_8);

		NginxAdapter withConfig = new NginxAdapter(Path.of("/var/log/nginx/access.log"),
				Path.of("/var/log/nginx/error.log"), tempDir.resolve("nginx.conf"), List.of(sitesEnabled));

		Set<Path> found = withConfig.discoverVhostLogPaths();

		assertEquals(Set.of(Path.of("/var/log/nginx/onda-backend.access.log"), Path.of("/var/log/nginx/waretrack.log")),
				found);
	}

	@Test
	void discoverVhostLogPathsSkipsOffAndSyslogTargets(@TempDir Path tempDir) throws IOException {
		Path sitesEnabled = tempDir.resolve("sites-enabled");
		Files.createDirectory(sitesEnabled);
		Files.writeString(sitesEnabled.resolve("site.conf"), """
				server {
				    access_log off;
				}
				server {
				    access_log syslog:server=127.0.0.1:514 combined;
				}
				""", StandardCharsets.UTF_8);

		NginxAdapter withConfig = new NginxAdapter(Path.of("/var/log/nginx/access.log"),
				Path.of("/var/log/nginx/error.log"), tempDir.resolve("nginx.conf"), List.of(sitesEnabled));

		assertTrue(withConfig.discoverVhostLogPaths().isEmpty());
	}

	@Test
	void discoverVhostLogPathsExcludesTheConfiguredMainAccessLog(@TempDir Path tempDir) throws IOException {
		Path sitesEnabled = tempDir.resolve("sites-enabled");
		Files.createDirectory(sitesEnabled);
		Files.writeString(sitesEnabled.resolve("sentinel.conf"), """
				server {
				    access_log /var/log/nginx/access.log;
				}
				""", StandardCharsets.UTF_8);

		NginxAdapter withConfig = new NginxAdapter(Path.of("/var/log/nginx/access.log"),
				Path.of("/var/log/nginx/error.log"), tempDir.resolve("nginx.conf"), List.of(sitesEnabled));

		assertTrue(withConfig.discoverVhostLogPaths().isEmpty());
	}

	@Test
	void discoverReportsAndTailsEachDiscoveredVhostLogSeparately(@TempDir Path tempDir) throws IOException {
		Path mainAccess = tempDir.resolve("access.log");
		Path mainError = tempDir.resolve("error.log");
		Files.createFile(mainAccess);
		Files.createFile(mainError);

		Path sitesEnabled = tempDir.resolve("sites-enabled");
		Files.createDirectory(sitesEnabled);
		Path vhostLog = tempDir.resolve("onda-backend.access.log");
		Files.createFile(vhostLog);
		Files.writeString(sitesEnabled.resolve("onda-backend.conf"),
				"server {\n    access_log " + vhostLog + ";\n}\n", StandardCharsets.UTF_8);

		NginxAdapter withConfig = new NginxAdapter(mainAccess, mainError, tempDir.resolve("nginx.conf"), List.of(sitesEnabled));
		withConfig.discover(); // first cycle: establishes tailing offsets for every file, empty backlog

		Files.writeString(vhostLog,
				"127.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /api/foo HTTP/1.1\" 200 0 \"-\" \"-\"\n",
				StandardCharsets.UTF_8);

		List<DiscoveredService> services = withConfig.discover();

		assertEquals(2, services.size());
		assertEquals("nginx:main", services.get(0).id());
		assertEquals("nginx:onda-backend", services.get(1).id());
		assertEquals("onda-backend", services.get(1).name());

		List<LogSource.LogLine> vhostLines = withConfig.fetchLogs("nginx:onda-backend", null);
		assertEquals(1, vhostLines.size());
		assertTrue(vhostLines.get(0).message().contains("/api/foo"));
		assertTrue(withConfig.fetchLogs("nginx:main", null).isEmpty());
	}

	@Test
	void knownVhostsKeepBeingReportedEvenWithoutNewTrafficThisCycle(@TempDir Path tempDir) throws IOException {
		Path mainAccess = tempDir.resolve("access.log");
		Path mainError = tempDir.resolve("error.log");
		Files.createFile(mainAccess);
		Files.createFile(mainError);

		Path sitesEnabled = tempDir.resolve("sites-enabled");
		Files.createDirectory(sitesEnabled);
		Path vhostLog = tempDir.resolve("onda-backend.access.log");
		Files.createFile(vhostLog);
		Files.writeString(sitesEnabled.resolve("onda-backend.conf"),
				"server {\n    access_log " + vhostLog + ";\n}\n", StandardCharsets.UTF_8);

		NginxAdapter withConfig = new NginxAdapter(mainAccess, mainError, tempDir.resolve("nginx.conf"), List.of(sitesEnabled));
		withConfig.discover();
		withConfig.discover(); // no traffic on either cycle

		List<DiscoveredService> services = withConfig.discover();

		assertEquals(2, services.size());
		assertEquals("nginx:onda-backend", services.get(1).id());
	}

	@Test
	void errorLogLinesAlwaysGoToTheMainService(@TempDir Path tempDir) throws IOException {
		Path access = tempDir.resolve("access.log");
		Path error = tempDir.resolve("error.log");
		Files.createFile(access);
		Files.createFile(error);
		NginxAdapter tailing = new NginxAdapter(access, error);
		tailing.discover();

		Files.writeString(error, "2023/10/10 13:55:36 [error] connect() failed\n", StandardCharsets.UTF_8);
		tailing.discover();

		List<LogSource.LogLine> mainLines = tailing.fetchLogs("nginx:main", null);
		assertEquals(1, mainLines.size());
		assertEquals("error", mainLines.get(0).level());
	}

	@Test
	void fetchLogsReturnsEmptyForAnUnknownServiceId() {
		assertTrue(adapter.fetchLogs("nginx:does-not-exist", null).isEmpty());
	}
}
