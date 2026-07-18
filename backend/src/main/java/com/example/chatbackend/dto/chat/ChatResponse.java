package com.example.chatbackend;

import java.util.List;

public record ChatResponse(String reply, List<UiComponent> components) {
}
