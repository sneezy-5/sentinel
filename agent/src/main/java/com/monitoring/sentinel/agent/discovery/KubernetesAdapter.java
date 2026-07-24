package com.monitoring.sentinel.agent.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Detected via an active kubelet / present kubeconfig (architecture doc, section 3.2).
 * Needs metrics-server for per-pod CPU/RAM stats, falls back to cgroups otherwise
 * (fragile, open point - section 9). Recommended deployment: DaemonSet. Roadmap #7 -
 * discover() still needs a real implementation.
 */
public class KubernetesAdapter implements ServiceAdapter {

	private static final Path SERVICE_ACCOUNT_TOKEN =
			Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");

	@Override
	public boolean isAvailable() {
		return Files.exists(SERVICE_ACCOUNT_TOKEN) || System.getenv("KUBECONFIG") != null;
	}

	@Override
	public List<DiscoveredService> discover() {
		// TODO(roadmap #7): call the Kubernetes API (pods on this node) + metrics-server,
		// with a direct cgroups fallback if metrics-server is absent.
		return List.of();
	}
}
