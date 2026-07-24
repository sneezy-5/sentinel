package com.monitoring.sentinel.central.persistence.repository;

import com.monitoring.sentinel.core.enums.ServerStatus;
import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<ServerEntity, String> {

	List<ServerEntity> findByStatusNotAndLastPushAtBefore(ServerStatus status, Instant threshold);

	Optional<ServerEntity> findByTokenHash(String tokenHash);
}
