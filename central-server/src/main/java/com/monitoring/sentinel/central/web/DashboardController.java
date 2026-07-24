package com.monitoring.sentinel.central.web;

import com.monitoring.sentinel.central.persistence.entity.ServiceEntity;
import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import com.monitoring.sentinel.central.persistence.repository.LogEntryRepository;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceMetricRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import com.monitoring.sentinel.core.enums.LogLevel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side rendered dashboard with Thymeleaf, directly inside central-server (no
 * separate frontend module). The "near real-time" refresh (roadmap #4) is done through
 * JS polling of /api/metrics/** from the templates, not WebSocket/SSE for now.
 */
@Controller
public class DashboardController {

	// No thymeleaf-extras-java8time on the classpath, so Instant can't be formatted directly
	// in the template - reformatted server-side into something readable instead of the raw
	// "2026-07-24T13:29:47.964843Z" toString(). System default zone: matches what journalctl
	// on the monitored server itself would show, which is what an operator is comparing against.
	private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private final ServerRepository serverRepository;
	private final ServiceRepository serviceRepository;
	private final ServiceMetricRepository serviceMetricRepository;
	private final LogEntryRepository logEntryRepository;

	public DashboardController(
			ServerRepository serverRepository,
			ServiceRepository serviceRepository,
			ServiceMetricRepository serviceMetricRepository,
			LogEntryRepository logEntryRepository) {
		this.serverRepository = serverRepository;
		this.serviceRepository = serviceRepository;
		this.serviceMetricRepository = serviceMetricRepository;
		this.logEntryRepository = logEntryRepository;
	}

	@GetMapping("/")
	public String listServers(Model model) {
		model.addAttribute("servers", serverRepository.findAll());
		return "dashboard";
	}

	@GetMapping("/servers/{serverId}")
	public String serverDetail(@PathVariable String serverId, Model model) {
		List<ServiceEntity> services = serviceRepository.findByServerId(serverId);

		// One query per service rather than a single bulk query: services per server is a
		// small number (containers on one box), and this reuses the existing "latest metric
		// for this service" repository method instead of adding a new bulk-fetch query.
		Map<String, ServiceMetricEntity> latestServiceMetrics = new HashMap<>();
		for (ServiceEntity service : services) {
			serviceMetricRepository.findFirstByServiceIdOrderByTimestampDesc(service.getId())
					.ifPresent(metric -> latestServiceMetrics.put(service.getId(), metric));
		}

		model.addAttribute("server", serverRepository.findById(serverId).orElseThrow());
		model.addAttribute("services", services);
		model.addAttribute("latestServiceMetrics", latestServiceMetrics);
		return "server-detail";
	}

	@GetMapping("/servers/{serverId}/services/{serviceId}")
	public String serviceDetail(
			@PathVariable String serverId, @PathVariable String serviceId, Model model) {
		model.addAttribute("server", serverRepository.findById(serverId).orElseThrow());
		model.addAttribute("service", serviceRepository.findById(serviceId).orElseThrow());
		model.addAttribute("metric",
				serviceMetricRepository.findFirstByServiceIdOrderByTimestampDesc(serviceId).orElse(null));

		List<LogLineView> logs = logEntryRepository
				.findByServiceIdOrderByTimestampDesc(serviceId, PageRequest.of(0, 100))
				.stream()
				.map(entry -> new LogLineView(
						LOG_TIMESTAMP_FORMAT.format(entry.getTimestamp()), entry.getLevel(), entry.getMessage()))
				.toList();
		model.addAttribute("logs", logs);
		return "service-detail";
	}

	public record LogLineView(String timestamp, LogLevel level, String message) {
	}
}
