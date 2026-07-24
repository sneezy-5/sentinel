package com.monitoring.sentinel.agent.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Detected via /var/run/docker.sock (architecture doc, section 3.2). The most structured
 * case: per-container stats via /containers/{id}/stats. Roadmap #2 - discover() still needs
 * a real implementation.
 */
public class DockerAdapter implements ServiceAdapter {

	private static final Path DOCKER_SOCKET = Path.of("/var/run/docker.sock");

	@Override
	public boolean isAvailable() {
		return Files.exists(DOCKER_SOCKET);
	}

	@Override
	public List<DiscoveredService> discover() {
		// TODO(roadmap #2): call the Docker API over the unix socket to list containers
		// + stats, and detect Swarm ("docker info" -> Swarm: active) for services/replicas.
		return List.of();
	}
}
