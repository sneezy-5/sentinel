package com.monitoring.sentinel.central.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUserEntity, String> {
}
