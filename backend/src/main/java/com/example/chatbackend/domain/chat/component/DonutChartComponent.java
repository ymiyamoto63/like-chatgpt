package com.example.chatbackend.domain.chat.component;

import java.util.List;

public record DonutChartComponent(String title, List<String> labels, List<Double> values) implements UiComponent {
}
