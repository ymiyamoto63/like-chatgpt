package com.example.chatbackend.domain.monitoring;

public record MonitoringEdge(String id, String sourceId, String targetId, double bandwidthPercent) {
}
