package com.monitoring.sentinel.central.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxAccessLogParserTest {

	@Test
	void parsesMethodAndPathFromACombinedLogLine() {
		String line = "127.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /api/stock HTTP/1.1\" 200 612 \"-\" \"curl/8.0\"";

		Optional<NginxAccessLogParser.Endpoint> result = NginxAccessLogParser.parse(line);

		assertTrue(result.isPresent());
		assertEquals("GET", result.get().method());
		assertEquals("/api/stock", result.get().path());
		assertEquals("GET /api/stock", result.get().label());
	}

	@Test
	void stripsQueryStringFromThePath() {
		String line = "127.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /api/stock?symbol=ABC HTTP/1.1\" 200 612 \"-\" \"-\"";

		Optional<NginxAccessLogParser.Endpoint> result = NginxAccessLogParser.parse(line);

		assertEquals("/api/stock", result.get().path());
	}

	@Test
	void nonAccessLogLineIsNotMatched() {
		assertTrue(NginxAccessLogParser.parse("2023/10/10 13:55:36 [error] connect() failed").isEmpty());
		assertTrue(NginxAccessLogParser.parse("just a plain log message").isEmpty());
	}
}
