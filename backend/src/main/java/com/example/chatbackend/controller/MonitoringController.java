package com.example.chatbackend.controller;

import com.example.chatbackend.dto.monitoring.MonitoringSnapshot;
import com.example.chatbackend.service.MonitoringMetricsService;

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
