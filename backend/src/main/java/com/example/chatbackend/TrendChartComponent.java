package com.example.chatbackend;

import java.util.List;

public record TrendChartComponent(String title, List<String> labels, List<Double> values, double average)
		implements UiComponent {
}
