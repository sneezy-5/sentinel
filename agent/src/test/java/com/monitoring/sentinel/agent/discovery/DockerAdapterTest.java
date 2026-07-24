package com.monitoring.sentinel.agent.discovery;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerAdapterTest {

	private final DockerAdapter adapter = new DockerAdapter();

	@Test
	void demuxesASingleFrameWithATimestampedLine() {
		byte[] raw = frame(1, "2026-07-24T12:00:00.000000000Z Starting up\n");

		List<LogSource.LogLine> lines = adapter.demux(raw);

		assertEquals(1, lines.size());
		assertEquals(Instant.parse("2026-07-24T12:00:00.000000000Z"), lines.get(0).timestamp());
		assertEquals("Starting up", lines.get(0).message());
		assertEquals("info", lines.get(0).level());
	}

	@Test
	void demuxesMultipleFramesAndClassifiesLevelsByKeyword() {
		byte[] raw = concat(
				frame(1, "2026-07-24T12:00:00.000000000Z Request handled\n"),
				frame(2, "2026-07-24T12:00:01.000000000Z ERROR: DB connection timeout\n"));

		List<LogSource.LogLine> lines = adapter.demux(raw);

		assertEquals(2, lines.size());
		assertEquals("info", lines.get(0).level());
		assertEquals("error", lines.get(1).level());
	}

	@Test
	void aSingleFrameCanContainMultipleLines() {
		byte[] raw = frame(1, "2026-07-24T12:00:00.000000000Z first\n2026-07-24T12:00:01.000000000Z second\n");

		List<LogSource.LogLine> lines = adapter.demux(raw);

		assertEquals(2, lines.size());
		assertEquals("first", lines.get(0).message());
		assertEquals("second", lines.get(1).message());
	}

	@Test
	void aLineWithoutATimestampPrefixIsKeptWholeRatherThanMisparsed() {
		byte[] raw = frame(1, "just some text with spaces\n");

		List<LogSource.LogLine> lines = adapter.demux(raw);

		assertEquals(1, lines.size());
		assertEquals("just some text with spaces", lines.get(0).message());
	}

	@Test
	void blankLinesAreSkipped() {
		byte[] raw = frame(1, "\n2026-07-24T12:00:00.000000000Z real line\n\n");

		List<LogSource.LogLine> lines = adapter.demux(raw);

		assertEquals(1, lines.size());
	}

	@Test
	void warnKeywordIsClassifiedAsWarn() {
		byte[] raw = frame(1, "2026-07-24T12:00:00.000000000Z WARNING: disk almost full\n");

		List<LogSource.LogLine> lines = adapter.demux(raw);

		assertEquals("warn", lines.get(0).level());
	}

	@Test
	void handlesEmptyInputWithoutThrowing() {
		assertTrue(adapter.demux(new byte[0]).isEmpty());
	}

	/** Builds one Docker log stream frame: 1-byte stream type, 3 zero bytes, 4-byte
	 * big-endian length, then the payload - matching the format documented in DockerAdapter. */
	private byte[] frame(int streamType, String payload) {
		byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(streamType);
		out.write(0);
		out.write(0);
		out.write(0);
		int len = payloadBytes.length;
		out.write((len >>> 24) & 0xFF);
		out.write((len >>> 16) & 0xFF);
		out.write((len >>> 8) & 0xFF);
		out.write(len & 0xFF);
		out.writeBytes(payloadBytes);
		return out.toByteArray();
	}

	private byte[] concat(byte[]... arrays) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] a : arrays) {
			out.writeBytes(a);
		}
		return out.toByteArray();
	}
}
