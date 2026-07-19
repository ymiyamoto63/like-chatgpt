package com.example.chatbackend.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SuggestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void suggestReturnsCompletionForPrefixOfCannedPhrase() throws Exception {
		mockMvc.perform(post("/api/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"当月担\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completion", is("当者別問い合わせ")));
	}

	@Test
	void suggestReturnsNullCompletionForNonMatchingText() throws Exception {
		mockMvc.perform(post("/api/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"こんにちは\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completion", nullValue()));
	}

	@Test
	void suggestReturnsNullCompletionForEmptyText() throws Exception {
		mockMvc.perform(post("/api/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completion", nullValue()));
	}

}
