package com.monitoring.sentinel.agent.network;

import org.junit.jupiter.api.Test;

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
}
