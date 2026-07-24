package com.monitoring.sentinel.central.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the admin account on first boot only, from ADMIN_USERNAME/ADMIN_PASSWORD (see
 * deploy/.env.example). After that the account lives entirely in the DB - changing the
 * password via the Settings page doesn't touch these env vars, and this seeder never
 * overwrites an existing account.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
	private static final String INSECURE_DEFAULT = "change-me";

	private final AdminUserRepository adminUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final String defaultUsername;
	private final String defaultPassword;

	public AdminSeeder(
			AdminUserRepository adminUserRepository,
			PasswordEncoder passwordEncoder,
			@Value("${sentinel.admin.username:admin}") String defaultUsername,
			@Value("${sentinel.admin.password:change-me}") String defaultPassword) {
		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.defaultUsername = defaultUsername;
		this.defaultPassword = defaultPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (adminUserRepository.count() > 0) {
			return;
		}
		adminUserRepository.save(new AdminUserEntity(defaultUsername, passwordEncoder.encode(defaultPassword)));
		if (INSECURE_DEFAULT.equals(defaultPassword)) {
			log.warn("Admin account '{}' created with the default password - change it from the "
					+ "Settings page immediately, this is not safe to leave as-is.", defaultUsername);
		} else {
			log.info("Admin account '{}' created.", defaultUsername);
		}
	}
}
