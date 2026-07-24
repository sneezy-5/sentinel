package com.monitoring.sentinel.agent.network;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UnixSocketHttpClientTest {

	@Test
	void dechunksASingleChunk() {
		String chunked = "5\r\nhello\r\n0\r\n\r\n";
		assertEquals("hello", UnixSocketHttpClient.dechunk(chunked));
	}

	@Test
	void dechunksMultipleChunks() {
		String chunked = "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n";
		assertEquals("hello world", UnixSocketHttpClient.dechunk(chunked));
	}

	@Test
	void handlesEmptyChunkedBody() {
		assertEquals("", UnixSocketHttpClient.dechunk("0\r\n\r\n"));
	}

	@Test
	void chunkSizeIsHexadecimal() {
		// 0x10 = 16 bytes
		String chunked = "10\r\n0123456789abcdef\r\n0\r\n\r\n";
		assertEquals("0123456789abcdef", UnixSocketHttpClient.dechunk(chunked));
	}

	@Test
	void dechunkBytesPreservesNonUtf8SafeBytes() {
		// Docker log frame headers contain raw bytes (e.g. 0x01 stream-type, 0x00 padding)
		// that aren't valid standalone UTF-8 - the whole point of the byte-based path is to
		// never round-trip them through a String, where they'd get corrupted/replaced.
		byte[] payload = {0x01, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0x80, 0x41};
		byte[] chunked = buildChunk(payload);
		assertArrayEquals(payload, UnixSocketHttpClient.dechunkBytes(chunked));
	}

	@Test
	void dechunkBytesHandlesMultipleChunks() {
		byte[] chunk1 = "hello".getBytes(StandardCharsets.US_ASCII);
		byte[] chunk2 = " world".getBytes(StandardCharsets.US_ASCII);
		byte[] combined = concat(buildChunkBody(chunk1), buildChunkBody(chunk2), "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
		assertArrayEquals("hello world".getBytes(StandardCharsets.US_ASCII), UnixSocketHttpClient.dechunkBytes(combined));
	}

	private byte[] buildChunk(byte[] payload) {
		return concat(buildChunkBody(payload), "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
	}

	private byte[] buildChunkBody(byte[] payload) {
		String sizeLine = Integer.toHexString(payload.length) + "\r\n";
		return concat(sizeLine.getBytes(StandardCharsets.US_ASCII), payload, "\r\n".getBytes(StandardCharsets.US_ASCII));
	}

	private byte[] concat(byte[]... arrays) {
		int total = 0;
		for (byte[] a : arrays) {
			total += a.length;
		}
		byte[] result = new byte[total];
		int pos = 0;
		for (byte[] a : arrays) {
			System.arraycopy(a, 0, result, pos, a.length);
			pos += a.length;
		}
		return result;
	}
}
