package com.monitoring.sentinel.central.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single admin account (this is a one-operator tool, not a multi-tenant product - see
 * the README). Seeded once at startup from ADMIN_USERNAME/ADMIN_PASSWORD (AdminSeeder),
 * then lives entirely in the DB so the password can be changed from the Settings page
 * without editing env vars and restarting.
 */
@Entity
@Table(name = "admin_users")
public class AdminUserEntity {

	@Id
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	protected AdminUserEntity() {
	}

	public AdminUserEntity(String username, String passwordHash) {
		this.username = username;
		this.passwordHash = passwordHash;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
