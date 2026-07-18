package com.example.chatbackend.dto.component;

import java.util.List;

public record ChoicesComponent(List<String> options) implements UiComponent {
}
