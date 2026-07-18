package com.example.chatbackend.dto.suggest;

public record SuggestResponse(String completion) {

	public static SuggestResponse empty() {
		return new SuggestResponse(null);
	}
}
