package com.monitoring.sentinel.central.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves agent install artifacts (binaries, systemd units, config template) from an
 * external directory rather than bundling them into the jar: they're built by CI/on the
 * ops machine, independently of central-server's own release cycle (architecture doc,
 * section 5.1 - install.sh expects them under {@code <central>/downloads/...}).
 *
 * Expected layout under sentinel.downloads-dir:
 *   agent/linux/{amd64,arm64}/sentinel-agent
 *   agent-native/linux/{amd64,arm64}/sentinel-native
 *   install/install.sh
 *   install/monitoring-agent.service
 *   install/monitoring-agent-collector.service
 *   install/monitoring-agent.yml.template
 *
 * If the directory doesn't exist yet, this just means /downloads/** and /install.sh 404 -
 * safe to register unconditionally.
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
		// install.sh is fetched at the domain root ("curl <central>/install.sh"), not
		// under /downloads, so the generated install command stays a plain one-liner.
		registry.addResourceHandler("/install.sh")
				.addResourceLocations("file:" + downloadsDir + "install/");
	}
}
