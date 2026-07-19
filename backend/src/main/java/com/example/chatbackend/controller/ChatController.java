package com.example.chatbackend.controller;

import com.example.chatbackend.domain.chat.ChatRequest;
import com.example.chatbackend.domain.chat.ChatResponse;
import com.example.chatbackend.service.MockChatService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	private final MockChatService mockChatService;

	public ChatController(MockChatService mockChatService) {
		this.mockChatService = mockChatService;
	}

	@PostMapping("/api/chat")
	public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		return mockChatService.generateResponse(request.message());
	}

}
