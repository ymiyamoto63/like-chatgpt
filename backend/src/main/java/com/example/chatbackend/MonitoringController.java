package com.example.chatbackend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonitoringController {

	private final MonitoringMetricsService monitoringMetricsService;

	public MonitoringController(MonitoringMetricsService monitoringMetricsService) {
		this.monitoringMetricsService = monitoringMetricsService;
	}

	@GetMapping("/api/monitoring/snapshot")
	public MonitoringSnapshot snapshot() {
		return monitoringMetricsService.getSnapshot();
	}

}
