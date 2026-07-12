package com.example.chatbackend;

import java.util.List;

public record TableComponent(List<String> columns, List<List<String>> rows) implements UiComponent {
}
