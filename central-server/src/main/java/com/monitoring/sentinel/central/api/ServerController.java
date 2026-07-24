package com.monitoring.sentinel.central.api;

import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
	private final TokenService tokenService;
	private final String publicUrl;

	public ServerController(
			ServerRepository serverRepository,
			TokenService tokenService,
			@Value("${sentinel.public-url:http://localhost:8080}") String publicUrl) {
		this.serverRepository = serverRepository;
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

	@DeleteMapping("/{serverId}")
	public ResponseEntity<Void> revokeServer(@PathVariable String serverId) {
		serverRepository.deleteById(serverId);
		return ResponseEntity.noContent().build();
	}

	public record NewServerResponse(String serverId, String token, String installCommand) {
	}
}
