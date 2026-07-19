package com.example.chatbackend.domain.monitoring;

public record MonitoringNode(String id, String label, double cpuPercent, double memoryPercent) {
}
