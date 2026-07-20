package com.example.chatbackend.application.port.in;

import com.example.chatbackend.domain.monitoring.MonitoringSnapshot;

public interface GetMonitoringSnapshotUseCase {

	MonitoringSnapshot getSnapshot();

}
