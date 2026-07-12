package com.example.chatbackend;

import java.util.List;

public record BarChartComponent(String title, List<String> labels, List<Double> values) implements UiComponent {
}
