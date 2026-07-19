package com.example.chatbackend.dto.monitoring;

public record MonitoringNode(String id, String label, double cpuPercent, double memoryPercent) {
}
