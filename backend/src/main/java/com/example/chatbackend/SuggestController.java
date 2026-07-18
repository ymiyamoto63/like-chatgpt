package com.example.chatbackend;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuggestController {

	private final MockSuggestService mockSuggestService;

	public SuggestController(MockSuggestService mockSuggestService) {
		this.mockSuggestService = mockSuggestService;
	}

	@PostMapping("/api/suggest")
	public SuggestResponse suggest(@RequestBody SuggestRequest request) {
		return mockSuggestService.suggest(request.text());
	}

}
