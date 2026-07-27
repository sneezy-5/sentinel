package com.monitoring.sentinel.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigLoaderTest {

	@Test
	void missingConfigFileFallsBackToDefaults() {
		AgentConfig config = ConfigLoader.load(Path.of("/does/not/exist.yml"));

		assertNull(config.getCentralUrl());
		assertEquals(20, config.getPushIntervalSeconds());
		assertEquals("/var/log", config.getHeaviestFileScanPath());
		assertEquals("/var/log/nginx/access.log", config.getNginxAccessLogPath());
	}

	// Simulates updating an already-deployed agent: an existing monitoring-agent.yml written
	// before heaviestFile*/nginx* existed (install.sh never overwrites an existing config -
	// see its comment) must still load cleanly, with the new fields falling back to
	// AgentConfig's Java-side defaults rather than null/0 - SnakeYAML's bean Constructor only
	// calls setters for keys actually present in the YAML, it doesn't null out the rest.
	@Test
	void oldConfigFileWithoutNewerKeysStillGetsDefaultsForThem(@TempDir Path tempDir) throws IOException {
		Path oldConfig = tempDir.resolve("monitoring-agent.yml");
		Files.writeString(oldConfig, """
				centralUrl: "https://monitor.example.com"
				token: "abc123"
				pushIntervalSeconds: 30
				""", StandardCharsets.UTF_8);

		AgentConfig config = ConfigLoader.load(oldConfig);

		assertEquals("https://monitor.example.com", config.getCentralUrl());
		assertEquals(30, config.getPushIntervalSeconds());
		// Not present in the file above - must still be the class's own defaults, not null/0.
		assertEquals("/var/log", config.getHeaviestFileScanPath());
		assertEquals(3600, config.getHeaviestFileScanIntervalSeconds());
		assertEquals("/var/log/nginx/access.log", config.getNginxAccessLogPath());
		assertEquals("/var/log/nginx/error.log", config.getNginxErrorLogPath());
	}

	@Test
	void explicitEmptyStringDisablesAFeatureEvenThoughItHasADefault(@TempDir Path tempDir) throws IOException {
		Path configPath = tempDir.resolve("monitoring-agent.yml");
		Files.writeString(configPath, """
				centralUrl: "https://monitor.example.com"
				token: "abc123"
				nginxAccessLogPath: ""
				""", StandardCharsets.UTF_8);

		AgentConfig config = ConfigLoader.load(configPath);

		assertEquals("", config.getNginxAccessLogPath());
	}
}
