package com.example.chatbackend.adapter.in.web;

import com.example.chatbackend.application.port.in.GenerateSuggestionUseCase;
import com.example.chatbackend.domain.suggest.SuggestRequest;
import com.example.chatbackend.domain.suggest.SuggestResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuggestController {

	private final GenerateSuggestionUseCase generateSuggestionUseCase;

	public SuggestController(GenerateSuggestionUseCase generateSuggestionUseCase) {
		this.generateSuggestionUseCase = generateSuggestionUseCase;
	}

	@PostMapping("/api/suggest")
	public SuggestResponse suggest(@RequestBody SuggestRequest request) {
		return generateSuggestionUseCase.generateSuggestion(request);
	}

}
