package com.example.chatbackend;

import java.util.List;

public record MonitoringSnapshot(List<MonitoringNode> nodes, List<MonitoringEdge> edges) {
}
