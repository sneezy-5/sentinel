package com.monitoring.sentinel.central.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertSettingsRepository extends JpaRepository<AlertSettingsEntity, String> {
}
