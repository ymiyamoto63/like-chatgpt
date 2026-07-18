package com.example.chatbackend;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message) {
}
