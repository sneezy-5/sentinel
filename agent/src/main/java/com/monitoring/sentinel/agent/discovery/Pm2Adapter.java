package com.monitoring.sentinel.agent.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Detected via ~/.pm2/pm2.pid (architecture doc, section 3.2). List/stats via "pm2 jlist".
 * No per-process network/disk stats (known limitation). Roadmap #5 - discover() still
 * needs a real implementation.
 */
public class Pm2Adapter implements ServiceAdapter {

	private final Path pm2PidFile;

	public Pm2Adapter() {
		this(Path.of(System.getProperty("user.home"), ".pm2", "pm2.pid"));
	}

	public Pm2Adapter(Path pm2PidFile) {
		this.pm2PidFile = pm2PidFile;
	}

	@Override
	public boolean isAvailable() {
		return Files.exists(pm2PidFile);
	}

	@Override
	public List<DiscoveredService> discover() {
		// TODO(roadmap #5): run "pm2 jlist", parse the JSON, map to DiscoveredService
		// (metadata: pm2_id, restarts - see the example payload in section 6.1).
		return List.of();
	}
}
