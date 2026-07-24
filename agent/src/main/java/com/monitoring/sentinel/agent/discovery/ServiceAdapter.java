package com.monitoring.sentinel.agent.discovery;

import java.util.List;

/**
 * A discovery adapter activates automatically if its environment is detected, with no
 * configuration required (architecture doc, section 3.2).
 */
public interface ServiceAdapter {

	/** true if this adapter's environment is present on this server (e.g. docker.sock). */
	boolean isAvailable();

	List<DiscoveredService> discover();
}
