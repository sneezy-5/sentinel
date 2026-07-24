package com.monitoring.sentinel.agent.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends payloads to the central over HTTPS + Bearer token (architecture doc, section 5).
 * On failure, the caller (AgentMain) is responsible for going through LocalBuffer instead
 * of losing the snapshot (section 3.4).
 */
public class PushClient {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	private final String centralUrl;
	private final String token;

	public PushClient(String centralUrl, String token) {
		this.centralUrl = centralUrl;
		this.token = token;
	}

	public boolean pushMetrics(Object metricsPayload) {
		return post("/api/agents/metrics", metricsPayload);
	}

	public boolean pushLogs(Object logBatchPayload) {
		return post("/api/agents/logs", logBatchPayload);
	}

	private boolean post(String path, Object payload) {
		try {
			String json = objectMapper.writeValueAsString(payload);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(centralUrl + path))
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + token)
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();
			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			return response.statusCode() / 100 == 2;
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}
}
