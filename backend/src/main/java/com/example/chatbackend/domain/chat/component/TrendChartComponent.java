package com.example.chatbackend.domain.chat.component;

import java.util.List;

public record TrendChartComponent(String title, List<String> labels, List<Double> values, double average)
		implements UiComponent {
}
