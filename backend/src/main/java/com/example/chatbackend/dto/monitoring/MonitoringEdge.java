package com.example.chatbackend.dto.monitoring;

public record MonitoringEdge(String id, String sourceId, String targetId, double bandwidthPercent) {
}
