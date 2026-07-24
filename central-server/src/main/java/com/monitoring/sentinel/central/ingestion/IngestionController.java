package com.monitoring.sentinel.central.ingestion;

import com.monitoring.sentinel.central.ingestion.dto.LogBatchPayload;
import com.monitoring.sentinel.central.ingestion.dto.MetricsPayload;
import com.monitoring.sentinel.central.security.TokenAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives agent pushes (architecture doc, section 5.1). No serverId in the URL: the
 * token, verified by TokenAuthFilter, both identifies and authenticates the server
 * (section 5) - the resolved id is read from the request attribute rather than trusting
 * the caller.
 */
@RestController
@RequestMapping("/api/agents")
public class IngestionController {

	private final IngestionService ingestionService;

	public IngestionController(IngestionService ingestionService) {
		this.ingestionService = ingestionService;
	}

	@PostMapping("/metrics")
	public ResponseEntity<Void> pushMetrics(@RequestBody MetricsPayload payload, HttpServletRequest request) {
		String serverId = (String) request.getAttribute(TokenAuthFilter.SERVER_ID_ATTRIBUTE);
		ingestionService.recordMetrics(serverId, payload);
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/logs")
	public ResponseEntity<Void> pushLogs(@RequestBody LogBatchPayload payload, HttpServletRequest request) {
		String serverId = (String) request.getAttribute(TokenAuthFilter.SERVER_ID_ATTRIBUTE);
		ingestionService.recordLogs(serverId, payload);
		return ResponseEntity.accepted().build();
	}
}
