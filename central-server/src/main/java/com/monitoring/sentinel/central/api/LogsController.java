package com.monitoring.sentinel.central.api;

import com.monitoring.sentinel.central.persistence.entity.LogEntryEntity;
import com.monitoring.sentinel.central.persistence.repository.LogEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Browsing raw logs (architecture doc, section 7.1): "show me the last 50 lines". */
@RestController
@RequestMapping("/api/logs")
public class LogsController {

	private final LogEntryRepository logEntryRepository;

	public LogsController(LogEntryRepository logEntryRepository) {
		this.logEntryRepository = logEntryRepository;
	}

	@GetMapping("/services/{serviceId}")
	public List<LogEntryEntity> recentLogs(
			@PathVariable String serviceId, @RequestParam(defaultValue = "50") int limit) {
		return logEntryRepository.findByServiceIdOrderByTimestampDesc(serviceId, PageRequest.of(0, limit));
	}
}
