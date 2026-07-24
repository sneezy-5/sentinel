package com.monitoring.sentinel.central.web;

import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Server-side rendered dashboard with Thymeleaf, directly inside central-server (no
 * separate frontend module). The "near real-time" refresh (roadmap #4) is done through
 * JS polling of /api/metrics/** from the templates, not WebSocket/SSE for now.
 */
@Controller
public class DashboardController {

	private final ServerRepository serverRepository;
	private final ServiceRepository serviceRepository;

	public DashboardController(ServerRepository serverRepository, ServiceRepository serviceRepository) {
		this.serverRepository = serverRepository;
		this.serviceRepository = serviceRepository;
	}

	@GetMapping("/")
	public String listServers(Model model) {
		model.addAttribute("servers", serverRepository.findAll());
		return "dashboard";
	}

	@GetMapping("/servers/{serverId}")
	public String serverDetail(@PathVariable String serverId, Model model) {
		model.addAttribute("server", serverRepository.findById(serverId).orElseThrow());
		model.addAttribute("services", serviceRepository.findByServerId(serverId));
		return "server-detail";
	}
}
