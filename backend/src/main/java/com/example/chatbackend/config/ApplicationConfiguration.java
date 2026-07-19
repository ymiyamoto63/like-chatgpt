package com.example.chatbackend.config;

import com.example.chatbackend.application.port.in.GenerateChatReplyUseCase;
import com.example.chatbackend.application.port.in.GenerateSuggestionUseCase;
import com.example.chatbackend.application.port.in.GetMonitoringSnapshotUseCase;
import com.example.chatbackend.application.port.out.MetricsGenerationPort;
import com.example.chatbackend.application.port.out.ReplyGenerationPort;
import com.example.chatbackend.application.port.out.SuggestionGenerationPort;
import com.example.chatbackend.application.service.ChatService;
import com.example.chatbackend.application.service.MonitoringService;
import com.example.chatbackend.application.service.SuggestService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

	@Bean
	GenerateChatReplyUseCase generateChatReplyUseCase(ReplyGenerationPort replyGenerationPort) {
		return new ChatService(replyGenerationPort);
	}

	@Bean
	GenerateSuggestionUseCase generateSuggestionUseCase(SuggestionGenerationPort suggestionGenerationPort) {
		return new SuggestService(suggestionGenerationPort);
	}

	@Bean
	GetMonitoringSnapshotUseCase getMonitoringSnapshotUseCase(MetricsGenerationPort metricsGenerationPort) {
		return new MonitoringService(metricsGenerationPort);
	}

}
