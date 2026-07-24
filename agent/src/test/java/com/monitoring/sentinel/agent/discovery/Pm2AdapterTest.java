package com.monitoring.sentinel.agent.discovery;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pm2AdapterTest {

	private final Pm2Adapter adapter = new Pm2Adapter();

	// Trimmed down from a real "pm2 jlist" payload (fork mode, online, restart_time 0) -
	// only the fields toDiscoveredService actually reads, real pm2 output also carries
	// megabytes of env vars/axm_monitor blobs per process that aren't relevant here.
	private static final String ONLINE_PROCESS = """
			{
			  "pid": 3861244,
			  "name": "rh-backend",
			  "pm2_env": { "status": "online", "restart_time": 0 },
			  "pm_id": 0,
			  "monit": { "memory": 92405760, "cpu": 0.7 }
			}""";

	private static final String CLUSTER_PROCESS = """
			{
			  "pid": 1949831,
			  "name": "whatsapp-worker",
			  "pm2_env": { "status": "online", "restart_time": 13 },
			  "pm_id": 1,
			  "monit": { "memory": 169869312, "cpu": 0 }
			}""";

	@Test
	void parsesAnOnlineForkModeProcess() throws IOException {
		List<DiscoveredService> services = adapter.parseJlist("[" + ONLINE_PROCESS + "]");

		assertEquals(1, services.size());
		DiscoveredService service = services.get(0);
		assertEquals("pm2:rh-backend", service.id());
		assertEquals("rh-backend", service.name());
		assertEquals("pm2", service.type());
		assertEquals("running", service.status());
		assertEquals(0.7, service.cpuPercent());
		assertEquals(88, service.memMb());
		assertEquals(0, service.diskMb());
		assertEquals(Map.of("pm2_id", "0", "restarts", "0"), service.metadata());
	}

	@Test
	void parsesMultipleProcesses() throws IOException {
		List<DiscoveredService> services = adapter.parseJlist("[" + ONLINE_PROCESS + "," + CLUSTER_PROCESS + "]");

		assertEquals(2, services.size());
		assertEquals("pm2:whatsapp-worker", services.get(1).id());
		assertEquals(162, services.get(1).memMb());
		assertEquals("13", services.get(1).metadata().get("restarts"));
	}

	@Test
	void mapsAnyNonOnlineStatusToStopped() throws IOException {
		String stopped = """
				{
				  "pid": 1,
				  "name": "worker",
				  "pm2_env": { "status": "stopped", "restart_time": 2 },
				  "pm_id": 2,
				  "monit": { "memory": 0, "cpu": 0 }
				}""";

		List<DiscoveredService> services = adapter.parseJlist("[" + stopped + "]");

		assertEquals("stopped", services.get(0).status());
	}

	@Test
	void sanitizesNamesWithCharsOutsideTheStableIdAlphabet() throws IOException {
		String weirdName = """
				{
				  "pid": 1,
				  "name": "My.App Name",
				  "pm2_env": { "status": "online", "restart_time": 0 },
				  "pm_id": 0,
				  "monit": { "memory": 0, "cpu": 0 }
				}""";

		List<DiscoveredService> services = adapter.parseJlist("[" + weirdName + "]");

		assertEquals("pm2:my-app-name", services.get(0).id());
		assertEquals("My.App Name", services.get(0).name());
	}

	@Test
	void emptyListProducesNoServices() throws IOException {
		assertTrue(adapter.parseJlist("[]").isEmpty());
	}
}
