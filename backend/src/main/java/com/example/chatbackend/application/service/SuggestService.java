package com.example.chatbackend.application.service;

import com.example.chatbackend.application.port.in.GenerateSuggestionUseCase;
import com.example.chatbackend.application.port.out.SuggestionGenerationPort;
import com.example.chatbackend.domain.suggest.SuggestRequest;
import com.example.chatbackend.domain.suggest.SuggestResponse;

public class SuggestService implements GenerateSuggestionUseCase {

	private final SuggestionGenerationPort suggestionGenerationPort;

	public SuggestService(SuggestionGenerationPort suggestionGenerationPort) {
		this.suggestionGenerationPort = suggestionGenerationPort;
	}

	@Override
	public SuggestResponse generateSuggestion(SuggestRequest request) {
		return suggestionGenerationPort.generateSuggestion(request.text());
	}

}
