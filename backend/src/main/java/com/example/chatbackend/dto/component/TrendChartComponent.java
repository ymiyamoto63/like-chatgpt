package com.example.chatbackend.dto.component;

import java.util.List;

public record TrendChartComponent(String title, List<String> labels, List<Double> values, double average)
		implements UiComponent {
}
