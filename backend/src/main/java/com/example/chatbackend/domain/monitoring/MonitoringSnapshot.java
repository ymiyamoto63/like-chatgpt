package com.example.chatbackend.domain.monitoring;

import java.util.List;

public record MonitoringSnapshot(List<MonitoringNode> nodes, List<MonitoringEdge> edges) {
}
