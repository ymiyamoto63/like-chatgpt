package com.example.chatbackend.application.service;

import com.example.chatbackend.application.port.in.GenerateChatReplyUseCase;
import com.example.chatbackend.application.port.out.ReplyGenerationPort;
import com.example.chatbackend.domain.chat.ChatRequest;
import com.example.chatbackend.domain.chat.ChatResponse;

public class ChatService implements GenerateChatReplyUseCase {

	private final ReplyGenerationPort replyGenerationPort;

	public ChatService(ReplyGenerationPort replyGenerationPort) {
		this.replyGenerationPort = replyGenerationPort;
	}

	@Override
	public ChatResponse generateReply(ChatRequest request) {
		return replyGenerationPort.generateReply(request.message());
	}

}
