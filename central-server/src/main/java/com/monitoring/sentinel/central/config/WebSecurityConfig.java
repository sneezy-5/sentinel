package com.monitoring.sentinel.central.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Single-admin session auth for the dashboard (architecture doc has no multi-tenant
 * concept - this is a one-operator tool). /api/agents/** stays outside session auth
 * entirely: it's authenticated by TokenAuthFilter (bearer token, see config.SecurityConfig)
 * instead, since a curl|bash install script can't hold a login session or a CSRF token.
 */
@Configuration
public class WebSecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(
						"/api/agents/**",
						"/css/**", "/js/**",
						"/install.sh",
						"/monitoring-agent.service",
						"/monitoring-agent-collector.service",
						"/monitoring-agent.yml.template",
						"/login")
				.permitAll()
				.anyRequest().authenticated())
			.formLogin(form -> form
					.loginPage("/login")
					.defaultSuccessUrl("/", false)
					.permitAll())
			.logout(logout -> logout
					.logoutUrl("/logout")
					.logoutSuccessUrl("/login")
					.permitAll())
			// Agent pushes carry a bearer token, not a browser session - no CSRF token to give.
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/agents/**"));
		return http.build();
	}
}
