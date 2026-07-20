package com.example.chatbackend.domain.chat;

import com.example.chatbackend.domain.chat.component.UiComponent;

import java.util.List;

public record ChatResponse(String reply, List<UiComponent> components) {
}
