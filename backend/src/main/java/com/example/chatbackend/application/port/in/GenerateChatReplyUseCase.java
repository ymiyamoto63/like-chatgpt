package com.example.chatbackend.application.port.in;

import com.example.chatbackend.domain.chat.ChatRequest;
import com.example.chatbackend.domain.chat.ChatResponse;

public interface GenerateChatReplyUseCase {

	ChatResponse generateReply(ChatRequest request);

}
