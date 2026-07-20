package com.example.chatbackend.domain.chat.component;

import java.util.List;

public record TableComponent(List<String> columns, List<List<String>> rows) implements UiComponent {
}
