package com.monitoring.sentinel.central.security;

import com.monitoring.sentinel.central.persistence.entity.ServerEntity;
import com.monitoring.sentinel.central.persistence.repository.ServerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates agent -> central pushes: "Authorization: Bearer &lt;token&gt;" (architecture
 * doc, section 5). The token both identifies AND authenticates the server (section 5:
 * "the token serves as both identification and authentication") - no serverId in the URL,
 * the server is resolved from the token's hash. No @Component here: registered explicitly
 * in SecurityConfig with a urlPattern restricted to ingestion routes.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

	public static final String SERVER_ID_ATTRIBUTE = "authenticatedServerId";

	private final ServerRepository serverRepository;
	private final TokenService tokenService;

	public TokenAuthFilter(ServerRepository serverRepository, TokenService tokenService) {
		this.serverRepository = serverRepository;
		this.tokenService = tokenService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String rawToken = extractBearerToken(request.getHeader("Authorization"));
		if (rawToken == null) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing token");
			return;
		}

		Optional<ServerEntity> server = serverRepository.findByTokenHash(tokenService.hash(rawToken));
		if (server.isEmpty()) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
			return;
		}

		request.setAttribute(SERVER_ID_ATTRIBUTE, server.get().getId());
		chain.doFilter(request, response);
	}

	private String extractBearerToken(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			return null;
		}
		return authorizationHeader.substring("Bearer ".length()).trim();
	}
}
