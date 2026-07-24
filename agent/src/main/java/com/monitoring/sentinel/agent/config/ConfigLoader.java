package com.monitoring.sentinel.agent.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads monitoring-agent.yml. The file is optional and never required at startup
 * (architecture doc, section 3.3): if it's missing or empty, the agent must still start
 * with AgentConfig's defaults - except for centralUrl/token, without which pushing is
 * simply impossible (the agent reports it but doesn't crash).
 */
public final class ConfigLoader {

	private ConfigLoader() {
	}

	public static AgentConfig load(Path configPath) {
		if (configPath == null || !Files.exists(configPath)) {
			return new AgentConfig();
		}
		Yaml yaml = new Yaml(new Constructor(AgentConfig.class, new LoaderOptions()));
		try (InputStream input = Files.newInputStream(configPath)) {
			AgentConfig config = yaml.load(input);
			return config != null ? config : new AgentConfig();
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read " + configPath, e);
		}
	}
}
