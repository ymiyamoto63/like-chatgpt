package com.example.chatbackend.application.port.out;

import com.example.chatbackend.domain.monitoring.MonitoringSnapshot;

public interface MetricsGenerationPort {

	MonitoringSnapshot generateSnapshot();

}
