package com.example.chatbackend.adapter.in.web;

import com.example.chatbackend.application.port.in.GenerateChatReplyUseCase;
import com.example.chatbackend.domain.chat.ChatRequest;
import com.example.chatbackend.domain.chat.ChatResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	private final GenerateChatReplyUseCase generateChatReplyUseCase;

	public ChatController(GenerateChatReplyUseCase generateChatReplyUseCase) {
		this.generateChatReplyUseCase = generateChatReplyUseCase;
	}

	@PostMapping("/api/chat")
	public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		return generateChatReplyUseCase.generateReply(request);
	}

}
