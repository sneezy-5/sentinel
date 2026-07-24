package com.monitoring.sentinel.central.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Optional self-hosted fallback for the agent binaries. By default install.sh fetches
 * sentinel-agent/sentinel-native from GitHub Releases (public, no auth, no server-side
 * setup) - this exists for setups that can't rely on outbound access to github.com from
 * monitored servers, letting an operator drop the binaries in sentinel.downloads-dir and
 * point install.sh at "<central>/downloads" via --download-base instead.
 *
 * install.sh itself, the systemd units, and the config template are NOT served from here:
 * they're plain static resources bundled into central-server's own image (see
 * src/main/resources/static/), covered by Spring Boot's default static handling.
 *
 * Expected layout under sentinel.downloads-dir, if used:
 *   sentinel-agent-linux-{amd64,arm64}
 *   sentinel-native-linux-{amd64,arm64}
 *
 * If the directory doesn't exist, /downloads/** just 404s - safe to register unconditionally.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String downloadsDir;

	public WebConfig(@Value("${sentinel.downloads-dir:/opt/sentinel/downloads}") String downloadsDir) {
		this.downloadsDir = downloadsDir.endsWith("/") ? downloadsDir : downloadsDir + "/";
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/downloads/**")
				.addResourceLocations("file:" + downloadsDir);
	}
}
