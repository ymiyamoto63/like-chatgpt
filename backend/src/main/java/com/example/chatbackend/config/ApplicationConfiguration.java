package com.example.chatbackend.config;

import com.example.chatbackend.application.port.in.GenerateChatReplyUseCase;
import com.example.chatbackend.application.port.out.ReplyGenerationPort;
import com.example.chatbackend.application.service.ChatService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

	@Bean
	GenerateChatReplyUseCase generateChatReplyUseCase(ReplyGenerationPort replyGenerationPort) {
		return new ChatService(replyGenerationPort);
	}

}
