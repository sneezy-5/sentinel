package com.monitoring.sentinel.central.web;

import com.monitoring.sentinel.central.persistence.entity.AlertRuleEntity;
import com.monitoring.sentinel.central.persistence.repository.AlertRuleRepository;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import com.monitoring.sentinel.core.enums.AlertLevel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for AlertRule (architecture doc, section 4.1) - the model/entity/evaluation
 * (AlertEvaluationService) already existed, nothing let an operator actually create one.
 * Service id and metric name are real dropdowns (populated from known services / the
 * fixed set of metric names AlertEvaluationService actually understands) rather than
 * free text, to avoid typo'd rules that would silently never fire.
 */
@Controller
public class AlertRuleController {

	// Matches what's actually collected (server: cpuPercent/ramUsedMb, service:
	// cpuPercent/memMb/diskMb - see the ingestion DTOs) - AlertEvaluationService has no
	// separate registry of valid names to source this from, so it's listed here directly.
	private static final List<String> METRIC_NAMES = List.of("cpuPercent", "ramUsedMb", "memMb", "diskMb");

	private final AlertRuleRepository alertRuleRepository;
	private final ServerRepository serverRepository;
	private final ServiceRepository serviceRepository;

	public AlertRuleController(
			AlertRuleRepository alertRuleRepository,
			ServerRepository serverRepository,
			ServiceRepository serviceRepository) {
		this.alertRuleRepository = alertRuleRepository;
		this.serverRepository = serverRepository;
		this.serviceRepository = serviceRepository;
	}

	@GetMapping("/alerts")
	public String list(Model model) {
		model.addAttribute("rules", alertRuleRepository.findAll());
		model.addAttribute("servers", serverRepository.findAll());
		model.addAttribute("services", serviceRepository.findAll());
		model.addAttribute("metricNames", METRIC_NAMES);
		model.addAttribute("levels", AlertLevel.values());
		return "alert-rules";
	}

	@PostMapping("/alerts")
	public String create(
			@RequestParam String serverId,
			@RequestParam(required = false) String serviceId,
			@RequestParam String targetMetric,
			@RequestParam double threshold,
			@RequestParam AlertLevel level) {
		AlertRuleEntity rule = new AlertRuleEntity();
		rule.setId(UUID.randomUUID().toString());
		rule.setServerId(serverId);
		rule.setServiceId(serviceId == null || serviceId.isBlank() ? null : serviceId);
		rule.setTargetMetric(targetMetric);
		rule.setThreshold(threshold);
		rule.setLevel(level);
		rule.setEnabled(true);
		alertRuleRepository.save(rule);
		return "redirect:/alerts";
	}

	@PostMapping("/alerts/{id}/delete")
	public String delete(@PathVariable String id) {
		alertRuleRepository.deleteById(id);
		return "redirect:/alerts";
	}
}
