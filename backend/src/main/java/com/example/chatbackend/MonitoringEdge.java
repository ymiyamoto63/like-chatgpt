package com.example.chatbackend;

public record MonitoringEdge(String id, String sourceId, String targetId, double bandwidthPercent) {
}
