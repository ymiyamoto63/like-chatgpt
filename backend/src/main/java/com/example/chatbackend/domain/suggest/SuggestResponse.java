package com.example.chatbackend.domain.suggest;

public record SuggestResponse(String completion) {

	public static SuggestResponse empty() {
		return new SuggestResponse(null);
	}
}
