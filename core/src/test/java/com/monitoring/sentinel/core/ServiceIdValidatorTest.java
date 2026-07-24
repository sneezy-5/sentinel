package com.monitoring.sentinel.core;

import com.monitoring.sentinel.core.enums.ServiceType;
import com.monitoring.sentinel.core.validation.ServiceIdValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceIdValidatorTest {

	@Test
	void acceptsWellFormedId() {
		assertTrue(ServiceIdValidator.isValid("docker:djeli-api"));
		assertTrue(ServiceIdValidator.isValid("pm2:worker-mailer"));
	}

	@Test
	void rejectsUnknownPrefix() {
		assertFalse(ServiceIdValidator.isValid("systemd:nginx"));
	}

	@Test
	void rejectsMissingSeparator() {
		assertFalse(ServiceIdValidator.isValid("docker-djeli-api"));
	}

	@Test
	void buildsIdFromTypeAndName() {
		assertEquals("docker:djeli-api", ServiceIdValidator.build(ServiceType.DOCKER, "djeli-api"));
	}
}
