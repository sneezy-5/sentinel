package com.monitoring.sentinel.central.web;

import com.monitoring.sentinel.central.persistence.entity.AlertRuleEntity;
import com.monitoring.sentinel.central.persistence.repository.AlertRuleRepository;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.core.enums.AlertLevel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * CRUD for AlertRule (architecture doc, section 4.1) - the model/entity/evaluation
 * (AlertEvaluationService) already existed, nothing let an operator actually create one.
 * Deliberately minimal: a flat form (server + optional service id as free text, metric
 * name as free text) rather than cascading dropdowns - matches how little validation the
 * rest of the alerting pipeline does today (AlertEvaluationService throws at evaluation
 * time for an unknown metric name, it doesn't validate up front either).
 */
@Controller
public class AlertRuleController {

	private final AlertRuleRepository alertRuleRepository;
	private final ServerRepository serverRepository;

	public AlertRuleController(AlertRuleRepository alertRuleRepository, ServerRepository serverRepository) {
		this.alertRuleRepository = alertRuleRepository;
		this.serverRepository = serverRepository;
	}

	@GetMapping("/alerts")
	public String list(Model model) {
		model.addAttribute("rules", alertRuleRepository.findAll());
		model.addAttribute("servers", serverRepository.findAll());
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
