package com.example.chatbackend.dto.chat;

import com.example.chatbackend.dto.component.UiComponent;

import java.util.List;

public record ChatResponse(String reply, List<UiComponent> components) {
}
