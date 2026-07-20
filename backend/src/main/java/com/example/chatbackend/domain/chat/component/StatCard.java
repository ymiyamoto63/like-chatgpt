package com.example.chatbackend.domain.chat.component;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatCard(String label, String value, String delta) {
}
