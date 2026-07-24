package com.monitoring.sentinel.central.web;

import com.monitoring.sentinel.central.persistence.entity.ServiceEntity;
import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceMetricRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

	private final ServerRepository serverRepository;
	private final ServiceRepository serviceRepository;
	private final ServiceMetricRepository serviceMetricRepository;

	public DashboardController(
			ServerRepository serverRepository,
			ServiceRepository serviceRepository,
			ServiceMetricRepository serviceMetricRepository) {
		this.serverRepository = serverRepository;
		this.serviceRepository = serviceRepository;
		this.serviceMetricRepository = serviceMetricRepository;
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
}
