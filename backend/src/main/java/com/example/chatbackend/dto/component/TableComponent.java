package com.example.chatbackend.dto.component;

import java.util.List;

public record TableComponent(List<String> columns, List<List<String>> rows) implements UiComponent {
}
