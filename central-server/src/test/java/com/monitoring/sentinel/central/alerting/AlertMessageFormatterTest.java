package com.monitoring.sentinel.central.alerting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertMessageFormatterTest {

	@Test
	void formatsCpuPercentWithOneDecimalAndPercentSign() {
		assertEquals("92.3%", AlertMessageFormatter.formatValue("cpuPercent", 92.34));
	}

	@Test
	void formatsMemoryMetricsAsWholeMegabytes() {
		assertEquals("1234 MB", AlertMessageFormatter.formatValue("ramUsedMb", 1234.4));
		assertEquals("512 MB", AlertMessageFormatter.formatValue("memMb", 512.0));
		assertEquals("2048 MB", AlertMessageFormatter.formatValue("diskMb", 2048.49));
	}

	@Test
	void unknownMetricFallsBackToTheRawValue() {
		assertEquals("42.0", AlertMessageFormatter.formatValue("somethingElse", 42.0));
	}
}
