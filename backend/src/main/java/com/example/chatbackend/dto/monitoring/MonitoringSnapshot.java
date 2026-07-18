package com.example.chatbackend.dto.monitoring;

import java.util.List;

public record MonitoringSnapshot(List<MonitoringNode> nodes, List<MonitoringEdge> edges) {
}
