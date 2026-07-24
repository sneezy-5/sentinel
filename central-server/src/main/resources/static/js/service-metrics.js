// Refreshes the CPU/memory/disk stat card on the service-detail page without a full page
// reload: a manual button, plus a 20s poll (matches the agent's default push interval) so
// the numbers don't otherwise go stale until the operator remembers to hit F5.

(function () {
	function setMetric(root, key, text) {
		const el = root.querySelector('[data-metric="' + key + '"]');
		if (el) {
			el.textContent = text;
		}
	}

	async function refresh(root) {
		const serviceId = root.dataset.serviceId;
		const button = root.querySelector(".refresh-button");
		if (button) {
			button.disabled = true;
		}
		try {
			const response = await fetch("/api/metrics/services/" + encodeURIComponent(serviceId));
			if (!response.ok) {
				throw new Error("HTTP " + response.status);
			}
			const metrics = await response.json();
			if (metrics.length === 0) {
				return;
			}
			// Newest-first (see MetricsController.serviceMetrics) - no reversal needed.
			const latest = metrics[0];
			setMetric(root, "cpu", latest.cpuPercent.toFixed(1) + "%");
			setMetric(root, "mem", latest.memMb + " MB");
			setMetric(root, "disk", latest.diskMb + " MB");
			const updatedEl = root.querySelector(".metrics-updated");
			if (updatedEl) {
				updatedEl.textContent = "Updated " + new Date().toLocaleTimeString();
			}
		} catch (err) {
			// Transient fetch failure - leave the last known values on screen rather than
			// blanking them out.
		} finally {
			if (button) {
				button.disabled = false;
			}
		}
	}

	function init() {
		const root = document.getElementById("service-metrics");
		if (!root) {
			return;
		}
		const button = root.querySelector(".refresh-button");
		if (button) {
			button.addEventListener("click", () => refresh(root));
		}
		setInterval(() => refresh(root), 20000);
	}

	document.addEventListener("DOMContentLoaded", init);
})();
