package com.example.chatbackend;

public record SuggestResponse(String completion) {

	public static SuggestResponse empty() {
		return new SuggestResponse(null);
	}
}
