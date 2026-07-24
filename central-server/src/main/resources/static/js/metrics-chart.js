// Renders the system-metrics stat tiles + CPU history chart on the server detail page.
// Vanilla JS, no dependencies - the whole dashboard stays dependency-free on purpose.

(function () {
	const SVG_NS = "http://www.w3.org/2000/svg";

	function svgEl(tag, attrs) {
		const el = document.createElementNS(SVG_NS, tag);
		for (const key in attrs) {
			el.setAttribute(key, attrs[key]);
		}
		return el;
	}

	function formatBytesPerSec(bytesPerSec) {
		if (!isFinite(bytesPerSec) || bytesPerSec < 0) {
			return "0.0 B/s";
		}
		const units = ["B/s", "KB/s", "MB/s", "GB/s"];
		let value = bytesPerSec;
		let unitIndex = 0;
		while (value >= 1024 && unitIndex < units.length - 1) {
			value /= 1024;
			unitIndex++;
		}
		return value.toFixed(1) + " " + units[unitIndex];
	}

	function ramPercent(m) {
		return m.ramTotalMb > 0 ? (m.ramUsedMb / m.ramTotalMb) * 100 : 0;
	}

	function diskPercent(m) {
		if (!m.disks || m.disks.length === 0 || m.disks[0].totalGb <= 0) {
			return null;
		}
		return (m.disks[0].usedGb / m.disks[0].totalGb) * 100;
	}

	function networkRate(prev, curr) {
		const seconds = (new Date(curr.timestamp) - new Date(prev.timestamp)) / 1000;
		if (seconds <= 0) {
			return 0;
		}
		const deltaBytes = (curr.rxBytes - prev.rxBytes) + (curr.txBytes - prev.txBytes);
		return Math.max(0, deltaBytes / seconds);
	}

	// A 12-point sparkline per the stat-tile contract: de-emphasis line, accent end-dot.
	function renderSparkline(svg, values) {
		svg.textContent = "";
		if (values.length < 2) {
			return;
		}
		const width = 100;
		const height = 28;
		const min = Math.min(...values);
		const max = Math.max(...values);
		const range = max - min || 1;
		const points = values.map((v, i) => {
			const x = (i / (values.length - 1)) * width;
			const y = height - ((v - min) / range) * height;
			return [x, y];
		});
		const path = "M" + points.map((p) => p[0].toFixed(1) + "," + p[1].toFixed(1)).join(" L");
		svg.setAttribute("viewBox", "0 0 " + width + " " + height);
		svg.setAttribute("preserveAspectRatio", "none");
		svg.appendChild(svgEl("path", { class: "spark-line", d: path }));
		const last = points[points.length - 1];
		svg.appendChild(svgEl("circle", { class: "spark-dot", cx: last[0], cy: last[1], r: 3 }));
	}

	function renderStatTiles(root, metrics) {
		const latest = metrics[metrics.length - 1];
		const cpuValues = metrics.map((m) => m.cpuPercent);
		const ramValues = metrics.map(ramPercent);
		const diskValues = metrics.map(diskPercent).filter((v) => v !== null);
		const rateValues = [];
		for (let i = 1; i < metrics.length; i++) {
			rateValues.push(networkRate(metrics[i - 1], metrics[i]));
		}

		setTile(root, "cpu", latest.cpuPercent.toFixed(1) + "%", cpuValues);
		setTile(root, "ram", (latest.ramUsedMb / 1024).toFixed(1) + " / " + (latest.ramTotalMb / 1024).toFixed(1) + " GB", ramValues);

		const latestDiskPercent = diskPercent(latest);
		if (latestDiskPercent !== null) {
			setTile(root, "disk", latest.disks[0].usedGb.toFixed(0) + " / " + latest.disks[0].totalGb.toFixed(0) + " GB", diskValues);
		} else {
			setTile(root, "disk", "—", []);
		}

		const latestRate = rateValues.length > 0 ? rateValues[rateValues.length - 1] : 0;
		setTile(root, "network", formatBytesPerSec(latestRate), rateValues);
	}

	function setTile(root, key, valueText, sparkValues) {
		const valueEl = root.querySelector('[data-stat="' + key + '"] .stat-value');
		const sparkEl = root.querySelector('[data-stat="' + key + '"] .sparkline');
		if (valueEl) {
			valueEl.textContent = valueText;
		}
		if (sparkEl) {
			renderSparkline(sparkEl, sparkValues.slice(-12));
		}
	}

	// CPU-over-time line chart with a hover crosshair + tooltip (single series: no legend
	// needed, the card header already names it).
	function renderChart(container, metrics) {
		const svg = container.querySelector(".chart-svg");
		const tooltip = container.querySelector(".chart-tooltip");
		svg.textContent = "";

		if (metrics.length < 2) {
			container.querySelector(".chart-empty").hidden = false;
			svg.hidden = true;
			return;
		}

		const width = 1000;
		const height = 220;
		const padding = { top: 10, right: 10, bottom: 24, left: 36 };
		const plotWidth = width - padding.left - padding.right;
		const plotHeight = height - padding.top - padding.bottom;

		svg.setAttribute("viewBox", "0 0 " + width + " " + height);
		svg.setAttribute("preserveAspectRatio", "none");

		const maxValue = Math.max(100, ...metrics.map((m) => m.cpuPercent));
		const yFor = (v) => padding.top + plotHeight - (v / maxValue) * plotHeight;
		const xFor = (i) => padding.left + (i / (metrics.length - 1)) * plotWidth;

		// Gridlines at clean 0/50/100-style steps (marks-and-anatomy.md).
		const steps = maxValue <= 100 ? [0, 25, 50, 75, 100] : [0, maxValue / 2, maxValue];
		steps.forEach((step) => {
			const y = yFor(step);
			svg.appendChild(svgEl("line", { class: "grid-line", x1: padding.left, x2: width - padding.right, y1: y, y2: y }));
			const label = svgEl("text", { class: "axis-label", x: padding.left - 8, y: y + 4, "text-anchor": "end" });
			label.textContent = Math.round(step) + "%";
			svg.appendChild(label);
		});

		const linePoints = metrics.map((m, i) => [xFor(i), yFor(m.cpuPercent)]);
		const linePath = "M" + linePoints.map((p) => p[0].toFixed(1) + "," + p[1].toFixed(1)).join(" L");
		const areaPath = linePath
			+ " L" + linePoints[linePoints.length - 1][0].toFixed(1) + "," + (padding.top + plotHeight)
			+ " L" + linePoints[0][0].toFixed(1) + "," + (padding.top + plotHeight) + " Z";

		svg.appendChild(svgEl("path", { class: "series-area", d: areaPath }));
		svg.appendChild(svgEl("path", { class: "series-line", d: linePath }));

		const crosshair = svgEl("line", { class: "crosshair", x1: 0, x2: 0, y1: padding.top, y2: padding.top + plotHeight });
		const hoverDot = svgEl("circle", { class: "hover-dot", r: 5 });
		svg.appendChild(crosshair);
		svg.appendChild(hoverDot);

		const hitArea = svgEl("rect", {
			x: padding.left, y: padding.top, width: plotWidth, height: plotHeight,
			fill: "transparent",
		});
		svg.appendChild(hitArea);

		const tooltipValue = tooltip.querySelector(".tooltip-value");
		const tooltipLabel = tooltip.querySelector(".tooltip-label");

		function showAt(clientX) {
			const rect = svg.getBoundingClientRect();
			const relativeX = ((clientX - rect.left) / rect.width) * width;
			let nearest = 0;
			let nearestDist = Infinity;
			linePoints.forEach((p, i) => {
				const dist = Math.abs(p[0] - relativeX);
				if (dist < nearestDist) {
					nearestDist = dist;
					nearest = i;
				}
			});
			const point = linePoints[nearest];
			const metric = metrics[nearest];
			crosshair.setAttribute("x1", point[0]);
			crosshair.setAttribute("x2", point[0]);
			crosshair.style.opacity = 1;
			hoverDot.setAttribute("cx", point[0]);
			hoverDot.setAttribute("cy", point[1]);
			hoverDot.style.opacity = 1;

			tooltipValue.textContent = metric.cpuPercent.toFixed(1) + "% CPU";
			tooltipLabel.textContent = new Date(metric.timestamp).toLocaleTimeString();
			const tooltipX = (point[0] / width) * rect.width;
			const tooltipY = (point[1] / height) * rect.height;
			tooltip.style.left = tooltipX + "px";
			tooltip.style.top = tooltipY + "px";
			tooltip.style.opacity = 1;
		}

		function hide() {
			crosshair.style.opacity = 0;
			hoverDot.style.opacity = 0;
			tooltip.style.opacity = 0;
		}

		hitArea.addEventListener("pointermove", (e) => showAt(e.clientX));
		hitArea.addEventListener("pointerleave", hide);
	}

	async function refreshMetrics(detail, serverId) {
		let metrics;
		try {
			const response = await fetch("/api/metrics/servers/" + encodeURIComponent(serverId) + "?limit=120");
			if (!response.ok) {
				throw new Error("HTTP " + response.status);
			}
			metrics = await response.json();
		} catch (err) {
			detail.querySelector(".chart-empty").hidden = false;
			detail.querySelector(".chart-empty").textContent = "Could not load metrics.";
			return;
		}

		if (!metrics || metrics.length === 0) {
			detail.querySelectorAll(".stat-value").forEach((el) => (el.textContent = "—"));
			detail.querySelector(".chart-empty").hidden = false;
			return;
		}

		detail.querySelector(".chart-empty").hidden = true;
		renderStatTiles(detail, metrics);
		renderChart(detail, metrics);
	}

	// Status/last-push/services count are server-rendered on load - re-fetched from the
	// same list endpoint the dashboard itself uses (GET /api/servers) rather than adding a
	// single-server endpoint just for this.
	async function refreshOverview(serverId) {
		try {
			const response = await fetch("/api/servers");
			if (!response.ok) {
				throw new Error("HTTP " + response.status);
			}
			const servers = await response.json();
			const server = servers.find((s) => s.id === serverId);
			if (!server) {
				return;
			}
			const statusLower = server.status.toLowerCase();
			document.querySelectorAll('[data-field="status-badge"]').forEach((el) => {
				el.textContent = server.status;
				el.className = "badge badge-" + statusLower;
			});
			document.querySelectorAll('[data-field="status-text"]').forEach((el) => {
				el.textContent = server.status;
			});
			document.querySelectorAll('[data-field="last-push"]').forEach((el) => {
				el.textContent = server.lastPushAt || "—";
			});
		} catch (err) {
			// Transient fetch failure - leave the last known values on screen.
		}
	}

	function init() {
		const detail = document.getElementById("server-metrics");
		if (!detail) {
			return;
		}
		const serverId = detail.dataset.serverId;
		const refresh = () => {
			refreshMetrics(detail, serverId);
			refreshOverview(serverId);
		};
		refresh();
		setInterval(refresh, 30000);
	}

	document.addEventListener("DOMContentLoaded", init);
})();
