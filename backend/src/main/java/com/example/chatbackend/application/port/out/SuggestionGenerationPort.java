package com.example.chatbackend.application.port.out;

import com.example.chatbackend.domain.suggest.SuggestResponse;

public interface SuggestionGenerationPort {

	SuggestResponse generateSuggestion(String text);

}
