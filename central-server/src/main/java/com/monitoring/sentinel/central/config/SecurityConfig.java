package com.monitoring.sentinel.central.config;

import com.monitoring.sentinel.central.security.TokenAuthFilter;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import com.monitoring.sentinel.central.security.TokenService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

	@Bean
	public FilterRegistrationBean<TokenAuthFilter> tokenAuthFilter(
			ServerRepository serverRepository, TokenService tokenService) {
		FilterRegistrationBean<TokenAuthFilter> registration =
				new FilterRegistrationBean<>(new TokenAuthFilter(serverRepository, tokenService));
		// /api/agents/** is reserved for pushes authenticated with an agent token;
		// /api/servers, /api/metrics, /api/logs (dashboard reads) stay outside this filter.
		registration.addUrlPatterns("/api/agents/*");
		return registration;
	}
}
