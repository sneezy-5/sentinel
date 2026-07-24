package com.monitoring.sentinel.central.alerting.health;

import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.core.enums.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * "Server down" detection (architecture doc, section 4.1): a server that hasn't pushed
 * within sentinel.server-down-threshold-seconds is marked DOWN. A dedicated component
 * rather than folded into business threshold evaluation, since it's about the absence
 * of data rather than a metric value.
 */
@Component
public class ServerHealthMonitor {

	private static final Logger log = LoggerFactory.getLogger(ServerHealthMonitor.class);

	private final ServerRepository serverRepository;
	private final Duration downThreshold;

	public ServerHealthMonitor(
			ServerRepository serverRepository,
			@Value("${sentinel.server-down-threshold-seconds:90}") long downThresholdSeconds) {
		this.serverRepository = serverRepository;
		this.downThreshold = Duration.ofSeconds(downThresholdSeconds);
	}

	@Scheduled(fixedDelayString = "PT30S")
	public void detectDownServers() {
		Instant cutoff = Instant.now().minus(downThreshold);
		List<ServerEntity> staleServers = serverRepository.findByStatusNotAndLastPushAtBefore(ServerStatus.DOWN, cutoff);
		for (ServerEntity server : staleServers) {
			server.setStatus(ServerStatus.DOWN);
			serverRepository.save(server);
			log.warn("Server {} marked DOWN (last push: {})", server.getId(), server.getLastPushAt());
		}
	}
}
