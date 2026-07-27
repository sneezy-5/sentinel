package com.monitoring.sentinel.central.api;

import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import com.monitoring.sentinel.central.persistence.repository.AlertRuleRepository;
import com.monitoring.sentinel.central.persistence.repository.LogEntryRepository;
import com.monitoring.sentinel.central.persistence.repository.LogEventRepository;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceMetricRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import com.monitoring.sentinel.central.persistence.repository.SystemMetricRepository;
import com.monitoring.sentinel.central.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Server management consumed by the dashboard: creation (generates a token shown only
 * once), listing, revocation (architecture doc, section 4.1 / 5.1).
 */
@RestController
@RequestMapping("/api/servers")
public class ServerController {

	private final ServerRepository serverRepository;
	private final ServiceRepository serviceRepository;
	private final SystemMetricRepository systemMetricRepository;
	private final ServiceMetricRepository serviceMetricRepository;
	private final LogEntryRepository logEntryRepository;
	private final LogEventRepository logEventRepository;
	private final AlertRuleRepository alertRuleRepository;
	private final TokenService tokenService;
	private final String publicUrl;

	public ServerController(
			ServerRepository serverRepository,
			ServiceRepository serviceRepository,
			SystemMetricRepository systemMetricRepository,
			ServiceMetricRepository serviceMetricRepository,
			LogEntryRepository logEntryRepository,
			LogEventRepository logEventRepository,
			AlertRuleRepository alertRuleRepository,
			TokenService tokenService,
			@Value("${sentinel.public-url:http://localhost:8080}") String publicUrl) {
		this.serverRepository = serverRepository;
		this.serviceRepository = serviceRepository;
		this.systemMetricRepository = systemMetricRepository;
		this.serviceMetricRepository = serviceMetricRepository;
		this.logEntryRepository = logEntryRepository;
		this.logEventRepository = logEventRepository;
		this.alertRuleRepository = alertRuleRepository;
		this.tokenService = tokenService;
		this.publicUrl = publicUrl;
	}

	@GetMapping
	public List<ServerEntity> listServers() {
		return serverRepository.findAll();
	}

	@PostMapping
	public ResponseEntity<NewServerResponse> createServer(@RequestParam String name, @RequestParam String hostname) {
		String rawToken = tokenService.generateRawToken();
		ServerEntity server = new ServerEntity(UUID.randomUUID().toString(), name, hostname, tokenService.hash(rawToken));
		serverRepository.save(server);
		// The plain-text token is never persisted: this is the only time it's visible,
		// shown here as a ready-to-copy-paste install command (architecture doc, section 5.1).
		String installCommand = "curl -sSL " + publicUrl + "/install.sh | bash -s -- --token=" + rawToken
				+ " --central=" + publicUrl;
		return ResponseEntity.ok(new NewServerResponse(server.getId(), rawToken, installCommand));
	}

	@Transactional
	@DeleteMapping("/{serverId}")
	public ResponseEntity<Void> revokeServer(@PathVariable String serverId) {
		// No FK/cascade wiring between servers and its time-series data (server_id/service_id
		// are plain string columns, not @ManyToOne relationships) - deleting just the server
		// row would leave system_metrics/services/service_metrics/logs orphaned forever
		// (long-retention hypertables like log_events would never age them out).
		//
		// Each of these is a single bulk SQL DELETE (see the repository Javadoc) - this used
		// to loop over every service and delete its metrics/logs one row at a time via
		// Spring Data's default derived-delete behavior, which timed out (HTTP 504) once a
		// server had accumulated real history. The service-scoped deletes must run before
		// serviceRepository.deleteByServerId, since they resolve their rows via a subquery
		// over the services table.
		serviceMetricRepository.deleteByServerId(serverId);
		logEntryRepository.deleteByServerId(serverId);
		logEventRepository.deleteByServerId(serverId);
		serviceRepository.deleteByServerId(serverId);
		systemMetricRepository.deleteByServerId(serverId);
		// Alert rules reference serverId directly (even service-level ones, see
		// AlertRuleController) - without this they'd keep evaluating against a server that no
		// longer pushes anything, dead rows nobody can see or clean up from the UI.
		alertRuleRepository.deleteByServerId(serverId);
		serverRepository.deleteById(serverId);
		return ResponseEntity.noContent().build();
	}

	public record NewServerResponse(String serverId, String token, String installCommand) {
	}
}
