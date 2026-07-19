package com.example.chatbackend.adapter.in.web;

import com.example.chatbackend.application.port.in.GetMonitoringSnapshotUseCase;
import com.example.chatbackend.domain.monitoring.MonitoringSnapshot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonitoringController {

	private final GetMonitoringSnapshotUseCase getMonitoringSnapshotUseCase;

	public MonitoringController(GetMonitoringSnapshotUseCase getMonitoringSnapshotUseCase) {
		this.getMonitoringSnapshotUseCase = getMonitoringSnapshotUseCase;
	}

	@GetMapping("/api/monitoring/snapshot")
	public MonitoringSnapshot snapshot() {
		return getMonitoringSnapshotUseCase.getSnapshot();
	}

}
