package com.example.chatbackend.application.port.in;

import com.example.chatbackend.domain.suggest.SuggestRequest;
import com.example.chatbackend.domain.suggest.SuggestResponse;

public interface GenerateSuggestionUseCase {

	SuggestResponse generateSuggestion(SuggestRequest request);

}
