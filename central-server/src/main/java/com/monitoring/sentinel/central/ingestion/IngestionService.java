package com.monitoring.sentinel.central.ingestion;

import com.monitoring.sentinel.central.ingestion.dto.LogBatchPayload;
import com.monitoring.sentinel.central.ingestion.dto.LogEntryPayload;
import com.monitoring.sentinel.central.ingestion.dto.MetricsPayload;
import com.monitoring.sentinel.central.ingestion.dto.ServicePayload;
import com.monitoring.sentinel.central.persistence.entity.LogEntryEntity;
import com.monitoring.sentinel.central.persistence.entity.LogEventEntity;
import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import com.monitoring.sentinel.central.persistence.entity.ServiceEntity;
import com.monitoring.sentinel.central.persistence.entity.ServiceMetricEntity;
import com.monitoring.sentinel.central.persistence.entity.SystemMetricEntity;
import com.monitoring.sentinel.central.persistence.repository.LogEntryRepository;
import com.monitoring.sentinel.central.persistence.repository.LogEventRepository;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceMetricRepository;
import com.monitoring.sentinel.central.persistence.repository.ServiceRepository;
import com.monitoring.sentinel.central.persistence.repository.SystemMetricRepository;
import com.monitoring.sentinel.core.enums.LogEventType;
import com.monitoring.sentinel.core.enums.LogLevel;
import com.monitoring.sentinel.core.enums.ServerStatus;
import com.monitoring.sentinel.core.enums.ServiceStatus;
import com.monitoring.sentinel.core.enums.ServiceType;
import com.monitoring.sentinel.core.model.DiskUsage;
import com.monitoring.sentinel.core.model.LogEntry;
import com.monitoring.sentinel.core.model.LogEvent;
import com.monitoring.sentinel.core.model.NetworkUsage;
import com.monitoring.sentinel.core.model.Service;
import com.monitoring.sentinel.core.model.ServiceMetric;
import com.monitoring.sentinel.core.model.SystemMetric;
import com.monitoring.sentinel.core.validation.ServiceIdValidator;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class IngestionService {

	private final ServerRepository serverRepository;
	private final SystemMetricRepository systemMetricRepository;
	private final ServiceRepository serviceRepository;
	private final ServiceMetricRepository serviceMetricRepository;
	private final LogEntryRepository logEntryRepository;
	private final LogEventRepository logEventRepository;

	public IngestionService(
			ServerRepository serverRepository,
			SystemMetricRepository systemMetricRepository,
			ServiceRepository serviceRepository,
			ServiceMetricRepository serviceMetricRepository,
			LogEntryRepository logEntryRepository,
			LogEventRepository logEventRepository) {
		this.serverRepository = serverRepository;
		this.systemMetricRepository = systemMetricRepository;
		this.serviceRepository = serviceRepository;
		this.serviceMetricRepository = serviceMetricRepository;
		this.logEntryRepository = logEntryRepository;
		this.logEventRepository = logEventRepository;
	}

	@Transactional
	public void recordMetrics(String serverId, MetricsPayload payload) {
		ServerEntity server = serverRepository.findById(serverId)
				.orElseThrow(() -> new NoSuchElementException("Unknown server: " + serverId));
		server.setLastPushAt(payload.timestamp());
		server.setStatus(ServerStatus.UP);
		serverRepository.save(server);

		SystemMetric systemMetric = new SystemMetric();
		systemMetric.setServerId(serverId);
		systemMetric.setTimestamp(payload.timestamp());
		systemMetric.setCpuPercent(payload.system().cpuPercent());
		systemMetric.setCpuCores(payload.system().cpuCores());
		systemMetric.setRamUsedMb(payload.system().ramUsedMb());
		systemMetric.setRamTotalMb(payload.system().ramTotalMb());
		systemMetric.setNetwork(new NetworkUsage(
				payload.system().network().rxBytes(), payload.system().network().txBytes()));
		systemMetric.setDisks(payload.system().disk().stream()
				.map(d -> new DiskUsage(d.mount(), d.usedGb(), d.totalGb()))
				.collect(Collectors.toList()));
		systemMetricRepository.save(SystemMetricEntity.fromModel(systemMetric));

		for (ServicePayload servicePayload : payload.services()) {
			recordService(serverId, payload.timestamp(), servicePayload);
		}
	}

	private void recordService(String serverId, Instant timestamp, ServicePayload servicePayload) {
		if (!ServiceIdValidator.isValid(servicePayload.id())) {
			// Malformed id: skip rather than fragment the history with an unstable id.
			return;
		}

		Service service = new Service();
		service.setId(servicePayload.id());
		service.setServerId(serverId);
		service.setName(servicePayload.name());
		service.setType(ServiceType.fromPrefix(servicePayload.id().split(":", 2)[0]));
		service.setStatus("running".equalsIgnoreCase(servicePayload.status())
				? ServiceStatus.RUNNING : ServiceStatus.STOPPED);
		service.setMetadata(servicePayload.metadata());
		serviceRepository.save(ServiceEntity.fromModel(service));

		ServiceMetric serviceMetric = new ServiceMetric();
		serviceMetric.setServiceId(servicePayload.id());
		serviceMetric.setTimestamp(timestamp);
		serviceMetric.setCpuPercent(servicePayload.cpuPercent());
		serviceMetric.setMemMb(servicePayload.memMb());
		serviceMetric.setDiskMb(servicePayload.diskMb());
		serviceMetricRepository.save(ServiceMetricEntity.fromModel(serviceMetric));
	}

	@Transactional
	public void recordLogs(String serverId, LogBatchPayload payload) {
		if (!ServiceIdValidator.isValid(payload.serviceId())) {
			return;
		}

		List<LogEntryEntity> rawEntries = payload.entries().stream()
				.map(entry -> toLogEntry(payload.serviceId(), entry))
				.map(LogEntryEntity::fromModel)
				.collect(Collectors.toList());
		logEntryRepository.saveAll(rawEntries);

		// Minimal counter derivation (architecture doc, section 7.2): the exact format of
		// custom pattern-matching rules is still an open point (section 9) - for now we
		// just classify by the level the agent already provided.
		long errorCount = payload.entries().stream()
				.filter(e -> "error".equalsIgnoreCase(e.level()))
				.count();
		if (errorCount > 0) {
			LogEvent event = new LogEvent();
			event.setServiceId(payload.serviceId());
			event.setTimestamp(Instant.now());
			event.setEventType(LogEventType.ERROR);
			event.setCount(errorCount);
			logEventRepository.save(LogEventEntity.fromModel(event));
		}
	}

	private LogEntry toLogEntry(String serviceId, LogEntryPayload payload) {
		LogEntry entry = new LogEntry();
		entry.setServiceId(serviceId);
		entry.setTimestamp(payload.timestamp());
		entry.setLevel(parseLevel(payload.level()));
		entry.setMessage(payload.message());
		return entry;
	}

	private LogLevel parseLevel(String rawLevel) {
		try {
			return LogLevel.valueOf(rawLevel.toUpperCase());
		} catch (IllegalArgumentException e) {
			return LogLevel.INFO;
		}
	}
}
