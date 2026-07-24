package com.monitoring.sentinel.agent.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Minimal HTTP/1.1-over-unix-socket GET client - just enough to talk to the Docker Engine
 * API on /var/run/docker.sock. Not a general-purpose HTTP client: no keep-alive, no request
 * body, one GET per connection. Built on java.nio's unix domain socket support (JDK 16+),
 * so no extra dependency is needed just for this (the agent stays lightweight - see
 * architecture doc, section 3.1).
 */
public final class UnixSocketHttpClient {

	private UnixSocketHttpClient() {
	}

	public static String get(Path socketPath, String path) throws IOException {
		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
		try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(address);
			String request = "GET " + path + " HTTP/1.1\r\nHost: docker\r\nConnection: close\r\n\r\n";
			channel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.US_ASCII)));
			return parseBody(readAll(channel));
		}
	}

	private static byte[] readAll(SocketChannel channel) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8192);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int read;
		// The request asks for "Connection: close", so the daemon closes its end once the
		// response is fully sent - read() returning -1 is the natural end, not a timeout.
		while ((read = channel.read(buffer)) != -1) {
			buffer.flip();
			byte[] chunk = new byte[buffer.remaining()];
			buffer.get(chunk);
			out.write(chunk);
			buffer.clear();
		}
		return out.toByteArray();
	}

	private static String parseBody(byte[] raw) {
		String full = new String(raw, StandardCharsets.UTF_8);
		int headerEnd = full.indexOf("\r\n\r\n");
		if (headerEnd < 0) {
			return full;
		}
		String headers = full.substring(0, headerEnd);
		String body = full.substring(headerEnd + 4);

		if (headers.toLowerCase(java.util.Locale.ROOT).contains("transfer-encoding: chunked")) {
			return dechunk(body);
		}
		return body;
	}

	/** Package-private for direct unit testing - Docker's Go HTTP server commonly chunks
	 * responses whose length isn't known upfront, including the stats endpoint. */
	static String dechunk(String chunkedBody) {
		StringBuilder result = new StringBuilder();
		int pos = 0;
		while (pos < chunkedBody.length()) {
			int lineEnd = chunkedBody.indexOf("\r\n", pos);
			if (lineEnd < 0) {
				break;
			}
			String sizeLine = chunkedBody.substring(pos, lineEnd).trim();
			int chunkSize;
			try {
				chunkSize = Integer.parseInt(sizeLine, 16);
			} catch (NumberFormatException e) {
				break;
			}
			if (chunkSize == 0) {
				break;
			}
			int chunkStart = lineEnd + 2;
			int chunkEnd = Math.min(chunkStart + chunkSize, chunkedBody.length());
			result.append(chunkedBody, chunkStart, chunkEnd);
			pos = chunkEnd + 2;
		}
		return result.toString();
	}
}
