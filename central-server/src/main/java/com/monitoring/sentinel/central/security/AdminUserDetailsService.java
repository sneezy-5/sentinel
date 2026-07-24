package com.monitoring.sentinel.central.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

	private final AdminUserRepository adminUserRepository;

	public AdminUserDetailsService(AdminUserRepository adminUserRepository) {
		this.adminUserRepository = adminUserRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AdminUserEntity admin = adminUserRepository.findById(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));
		return User.withUsername(admin.getUsername())
				.password(admin.getPasswordHash())
				.roles("ADMIN")
				.build();
	}
}
