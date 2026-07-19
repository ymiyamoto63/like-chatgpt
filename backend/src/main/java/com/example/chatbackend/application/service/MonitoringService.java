package com.example.chatbackend.application.service;

import com.example.chatbackend.application.port.in.GetMonitoringSnapshotUseCase;
import com.example.chatbackend.application.port.out.MetricsGenerationPort;
import com.example.chatbackend.domain.monitoring.MonitoringSnapshot;

public class MonitoringService implements GetMonitoringSnapshotUseCase {

	private final MetricsGenerationPort metricsGenerationPort;

	public MonitoringService(MetricsGenerationPort metricsGenerationPort) {
		this.metricsGenerationPort = metricsGenerationPort;
	}

	@Override
	public MonitoringSnapshot getSnapshot() {
		return metricsGenerationPort.generateSnapshot();
	}

}
